import cadenza.Language
import cadenza.data.Neutral
import cadenza.data.NeutralException
import cadenza.data.NeutralValue
import cadenza.jit.Builtin
import cadenza.jit.CadenzaRootNode
import cadenza.jit.Code
import cadenza.jit.FrameLayout
import cadenza.jit.Plus
import cadenza.jit.PlusNodeGen
import cadenza.semantics.Type
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node.Child
import com.oracle.truffle.api.nodes.NodeUtil
import org.graalvm.polyglot.Context
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Builtin.run permits legacy exception-style neutrals even when executeAny returns values. */
class NeutralLegacyTests {
  private class EvaluateRoot(language: Language, expression: Code) :
    CadenzaRootNode(language, FrameLayout().build()) {
    @Child private var expression = expression
    override fun execute(frame: VirtualFrame): Any? = expression.executeAny(frame)
  }

  private fun withLanguage(action: (Language) -> Unit) {
    Context.create("cadenza").use { context ->
      context.initialize("cadenza")
      context.enter()
      try { action(Language.currentLanguage()) } finally { context.leave() }
    }
  }

  private fun hole(type: Type) = NeutralValue(type,
    Neutral.NCallBuiltin(PlusNodeGen.create(), emptyArray()))

  private fun legacy(symbolic: NeutralValue): Code = Code.CallBuiltin(symbolic.type,
    object : Builtin(symbolic.type, 0) {
      override fun run(frame: VirtualFrame, args: Array<Any?>): Any? =
        throw NeutralException(symbolic.type, symbolic.term)
    }, emptyArray())

  private fun effect(trace: MutableList<String>, label: String, value: Int): Code =
    Code.CallBuiltin(Type.Nat, object : Builtin(Type.Nat, 0) {
      override fun run(frame: VirtualFrame, args: Array<Any?>): Any {
        trace += label
        return value
      }
    }, emptyArray())

  @Test fun legacyNeutralArgumentPreservesLaterEffectsAndTheEnclosingOperation() = withLanguage { language ->
    val symbolic = hole(Type.Nat)
    val trace = mutableListOf<String>()
    val expression = Code.CallBuiltin(Type.Nat, PlusNodeGen.create(),
      arrayOf(legacy(symbolic), effect(trace, "second argument", 7)))
    val root = EvaluateRoot(language, expression)
    repeat(2) {
      trace.clear()
      val result = root.callTarget.call() as NeutralValue
      assertEquals(listOf("second argument"), trace)
      assertEquals(Type.Nat, result.type)
      val addition = result.term as Neutral.NCallBuiltin
      assertTrue(addition.builtin is Plus)
      assertEquals(2, addition.args.size)
      val retained = addition.args[0] as NeutralValue
      assertEquals(Type.Nat, retained.type)
      assertSame(symbolic.term, retained.term)
      assertEquals(7, addition.args[1])
    }
    assertTrue(NodeUtil.verify(root))
  }

  @Test fun legacyNeutralConditionNormalizesBothBranchesAndRetainsThePredicate() = withLanguage { language ->
    val symbolic = hole(Type.Bool)
    val trace = mutableListOf<String>()
    val expression = Code.If(Type.Nat, legacy(symbolic),
      effect(trace, "then", 17), effect(trace, "else", 23))
    val root = EvaluateRoot(language, expression)
    repeat(2) {
      trace.clear()
      val result = root.callTarget.call() as NeutralValue
      assertEquals(listOf("then", "else"), trace)
      assertEquals(Type.Nat, result.type)
      val conditional = result.term as Neutral.NIf
      assertSame(symbolic.term, conditional.body)
      assertEquals(17, conditional.thenValue)
      assertEquals(23, conditional.elseValue)
    }
    assertTrue(NodeUtil.verify(root))
  }
}
