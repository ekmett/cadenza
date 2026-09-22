import cadenza.Language
import cadenza.data.Closure
import cadenza.data.Neutral
import cadenza.data.NeutralException
import cadenza.data.NeutralValue
import cadenza.jit.Builtin
import cadenza.jit.Builtin1
import cadenza.jit.Builtin2
import cadenza.jit.FrameLayout
import cadenza.jit.InteropApplyRootNode
import cadenza.jit.PlusNodeGen
import cadenza.semantics.Type
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.ArityException
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.UnsupportedMessageException
import com.oracle.truffle.api.interop.UnsupportedTypeException
import com.oracle.truffle.api.nodes.NodeUtil
import com.oracle.truffle.api.source.Source
import org.graalvm.polyglot.Context
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NeutralTests {
  private fun neutral(type: Type) = NeutralValue(type,
    Neutral.NCallBuiltin(PlusNodeGen.create(), emptyArray()))

  private fun parse(program: String): Closure = Language.currentLanguage().parse(
    Source.newBuilder("cadenza", program, "neutral.za").build()).call() as Closure

  // Internal evaluator calls may carry typed neutrals; public interop accepts concrete
  // arguments. Enter the regular guest dispatcher without public argument validation.
  private fun apply(function: Closure, vararg arguments: Any?): Any? =
    InteropApplyRootNode(Language.currentLanguage(), arguments.size).callTarget.call(function, arguments)

  private fun withLanguage(action: () -> Unit) {
    Context.create("cadenza").use { context ->
      context.initialize("cadenza")
      context.enter()
      try { action() } finally { context.leave() }
    }
  }

  @Test fun overapplicationRetainsNeutralFunctionsBeforeAndAfterCacheSaturation() = withLanguage {
    val functionType = Type.Arr(Type.Nat, Type.Arr(Type.Nat, Type.Nat))
    val symbolic = neutral(functionType)
    val consumer = parse("\\(f : Nat -> Nat -> Nat -> Nat) -> f 0 1 2")
    val producers = (1..8).map {
      // Each compilation creates a distinct target at the same consumer call site.
      val factory = parse("\\(f : Nat -> Nat -> Nat) -> \\(ignored : Nat) -> f")
      apply(factory, symbolic) as Closure
    }
    repeat(3) {
      for (producer in producers) {
        val result = apply(consumer, producer) as NeutralValue
        assertEquals(Type.Nat, result.type)
        val application = result.term as Neutral.NApp
        assertSame(symbolic.term, application.rator)
        assertArrayEquals(arrayOf(1, 2), application.rands)
      }
    }
    val concrete = parse("\\(x : Nat) -> \\(y : Nat) (z : Nat) -> plus x (plus y z)")
    assertEquals(3, apply(consumer, concrete))
    assertTrue(NodeUtil.verify(consumer.callTarget.rootNode))
  }

  @Test fun neutralConditionalsContainTailCallsInEachNormalizedBranch() = withLanguage {
    val condition = neutral(Type.Bool)
    val function = parse("let f : Bool -> Nat = \\(p : Bool) -> " +
      "if p then 1 else f (eq 1 1) in f")
    repeat(3) {
      val result = apply(function, condition) as NeutralValue
      assertEquals(Type.Nat, result.type)
      val conditional = result.term as Neutral.NIf
      assertSame(condition.term, conditional.body)
      assertEquals(1, conditional.thenValue)
      assertEquals(1, conditional.elseValue)
    }
    assertTrue(NodeUtil.verify(function.callTarget.rootNode))
    val clone = NodeUtil.cloneNode(function.callTarget.rootNode)
    val clonedFunction = Closure(function.env, function.papArgs, function.arity, function.type, clone.callTarget)
    val clonedResult = apply(clonedFunction, condition) as NeutralValue
    assertEquals(1, (clonedResult.term as Neutral.NIf).elseValue)
    assertTrue(NodeUtil.verify(clone))
    assertEquals(1, apply(function, true))
  }

  @Test fun builtinResultsRaiseNeutralsBeforeTheirOuterExpressionConsumesThem() = withLanguage {
    val symbolic = neutral(Type.Nat)
    val function = parse("\\(n : Nat) -> plus " +
      "(fixNatF (\\(self : Nat -> Nat) (x : Nat) -> n) 0) 1")
    val result = apply(function, symbolic) as NeutralValue
    assertEquals(Type.Nat, result.type)
    val addition = result.term as Neutral.NCallBuiltin
    assertSame(symbolic.term, (addition.args[0] as NeutralValue).term)
    assertEquals(1, addition.args[1])
    assertEquals(42, apply(function, 41))
  }

  @Test fun typedBuiltinEntryPointsPreserveNeutralExceptions() {
    val frame = Truffle.getRuntime().createVirtualFrame(emptyArray(), FrameLayout().build())
    val symbolic = neutral(Type.Nat)
    val zero = object : Builtin(Type.Nat, 0) {
      override fun run(frame: VirtualFrame, args: Array<Any?>): Any = symbolic
    }
    val one = object : Builtin1(Type.Arr(Type.Nat, Type.Nat)) {
      override fun execute(x: Any?): Any = symbolic
    }
    val two = object : Builtin2(Type.Arr(Type.Nat, Type.Arr(Type.Nat, Type.Nat))) {
      override fun execute(left: Any?, right: Any?): Any = symbolic
    }
    for ((builtin, arguments) in listOf(
      zero to emptyArray<Any?>(), one to arrayOf<Any?>(0), two to arrayOf<Any?>(0, 0)
    )) {
      val exception = assertThrows(NeutralException::class.java) { builtin.runInteger(frame, arguments) }
      assertSame(symbolic.term, exception.term)
      assertThrows(NeutralException::class.java) { builtin.runUnit(frame, arguments) }
    }
  }

  @Test fun neutralInteropReportsCallabilityArityAndArgumentErrors() {
    val interop = InteropLibrary.getUncached()
    val symbolic = neutral(Type.Arr(Type.Nat, Type.Arr(Type.Nat, Type.Nat)))
    assertTrue(interop.isExecutable(symbolic))
    val partial = interop.execute(symbolic, 20) as NeutralValue
    assertEquals(Type.Arr(Type.Nat, Type.Nat), partial.type)
    assertTrue(interop.isExecutable(partial))
    val result = interop.execute(partial, 22) as NeutralValue
    assertEquals(Type.Nat, result.type)
    assertFalse(interop.isExecutable(result))
    assertArrayEquals(arrayOf(20, 22), (result.term as Neutral.NApp).rands)
    assertThrows(UnsupportedMessageException::class.java) { interop.execute(result) }
    assertFalse(interop.isExecutable(neutral(Type.Bool)))
    val arity = assertThrows(ArityException::class.java) { interop.execute(symbolic, 1, 2, 3) }
    assertEquals(0, arity.expectedMinArity)
    assertEquals(2, arity.expectedMaxArity)
    assertEquals(3, arity.actualArity)
    assertThrows(UnsupportedTypeException::class.java) { interop.execute(symbolic, "wrong") }
    assertThrows(UnsupportedTypeException::class.java) { interop.execute(symbolic, -1) }
    val neutralArgument = neutral(Type.Nat)
    assertThrows(UnsupportedTypeException::class.java) { interop.execute(symbolic, neutralArgument) }
    val internal = symbolic.apply(arrayOf(neutralArgument, 1))
    assertEquals(Type.Nat, internal.type)
    assertSame(neutralArgument, (internal.term as Neutral.NApp).rands[0])
  }
}
