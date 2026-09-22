import cadenza.Language
import cadenza.data.BigInt
import cadenza.data.Closure
import cadenza.data.Neutral
import cadenza.data.NeutralValue
import cadenza.jit.Minus
import cadenza.jit.Mult
import cadenza.jit.Plus
import cadenza.jit.PlusNodeGen
import cadenza.semantics.Type
import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.source.Source
import org.graalvm.polyglot.Context
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigInteger

class NeutralCompilationTests {
  private val huge = BigInteger.ONE.shiftLeft(90).add(BigInteger.valueOf(37))
  private val text = """
    \(choose : Bool) (f : Nat -> Nat) (x : Nat) (offset : Nat) ->
      if choose then minus (f (plus x offset)) 7 else plus (mult x 11) offset
  """.trimIndent()

  private class Symbols {
    // Identity distinguishes holes; these deliberately incomplete builtins are never run.
    val choose = NeutralValue(Type.Bool, Neutral.NCallBuiltin(PlusNodeGen.create(), emptyArray()))
    val function = NeutralValue(Type.Arr(Type.Nat, Type.Nat),
      Neutral.NCallBuiltin(PlusNodeGen.create(), emptyArray()))
    val argument = NeutralValue(Type.Nat, Neutral.NCallBuiltin(PlusNodeGen.create(), emptyArray()))
  }

  private fun integer(value: Any?): BigInteger = when (value) {
    is Int -> BigInteger.valueOf(value.toLong())
    is BigInt -> value.value
    else -> error("Expected concrete integer, got $value")
  }

  /** Independent residual interpreter: no guest evaluation or builtin.run calls. */
  private fun substitute(value: Any?, holes: Symbols, choose: Boolean, argument: BigInteger): BigInteger {
    if (value !is NeutralValue) return integer(value)
    assertEquals(Type.Nat, value.type)
    if (value.term === holes.argument.term) return argument
    fun eval(nested: Any?) = substitute(nested, holes, choose, argument)
    return when (val term = value.term) {
      is Neutral.NIf -> {
        assertSame(holes.choose.term, term.body)
        eval(if (choose) term.thenValue else term.elseValue)
      }
      is Neutral.NApp -> {
        assertSame(holes.function.term, term.rator)
        assertEquals(1, term.rands.size)
        eval(term.rands.single()).multiply(BigInteger.TWO).add(BigInteger.valueOf(13))
      }
      is Neutral.NCallBuiltin -> {
        assertEquals(2, term.args.size)
        val left = eval(term.args[0])
        val right = eval(term.args[1])
        when (term.builtin) {
          is Plus -> left.add(right)
          is Minus -> left.subtract(right)
          is Mult -> left.multiply(right)
          else -> error("Unexpected residual builtin ${term.builtin}")
        }
      }
    }
  }

  private fun expected(choose: Boolean, x: BigInteger, offset: Int): BigInteger =
    if (choose) x.multiply(BigInteger.TWO).add(BigInteger.valueOf(2L * offset + 6))
    else x.multiply(BigInteger.valueOf(11)).add(BigInteger.valueOf(offset.toLong()))

  private fun withContext(action: (Language) -> Unit) {
    val context = Context.newBuilder("cadenza")
      .allowExperimentalOptions(true).option("cadenza.Backend", "ast").build()
    context.use {
      context.initialize("cadenza")
      context.enter()
      try { action(Language.currentLanguage()) } finally { context.leave() }
    }
  }

  private class Harness(language: Language, text: String) {
    val function = language.parse(Source.newBuilder("cadenza", text, "neutral-compilation.za").build()).call() as Closure
    val concrete = language.parse(Source.newBuilder("cadenza", "\\(n : Nat) -> plus (mult n 2) 13",
      "neutral-concrete-function.za").build()).call() as Closure
    val target: RootCallTarget = function.callTarget
    init {
      assertNull(function.env)
      assertTrue(function.papArgs.isEmpty())
    }
    fun neutral(holes: Symbols, offset: Int) =
      target.call(0L, holes.choose, holes.function, holes.argument, offset) as NeutralValue
    fun concrete(choose: Boolean, argument: Any, offset: Int) =
      target.call(0L, choose, concrete, argument, offset)
  }

  // Portable semantics: this test must pass with the original SlowPathException
  // representation as well as either compiler-visible experimental representation.
  @Test fun retainedResidualsSurviveRepeatedNeutralConcreteAndPromotedHistories() = withContext { language ->
    val harness = Harness(language, text)
    data class Saved(val residual: NeutralValue, val holes: Symbols, val offset: Int)
    val saved = mutableListOf<Saved>()
    repeat(18) { iteration ->
      val holes = Symbols()
      val offset = 3 * iteration + 1
      saved += Saved(harness.neutral(holes, offset), holes, offset)
      assertEquals(expected(true, BigInteger.valueOf(23), offset), integer(harness.concrete(true, 23, offset)))
      assertEquals(expected(iteration % 2 == 0, huge, offset),
        integer(harness.concrete(iteration % 2 == 0, BigInt(huge), offset)))
      assertEquals(expected(false, BigInteger.valueOf(41), offset), integer(harness.concrete(false, 41, offset)))
    }
    // Every residual outlives later calls and holds a different set of holes. Two
    // independent substitutions of both arms catch stale frames and operand order.
    for ((residual, holes, offset) in saved.reversed()) {
      for (choose in listOf(false, true)) for (argument in listOf(BigInteger.ZERO, huge)) {
        assertEquals(expected(choose, argument, offset), substitute(residual, holes, choose, argument))
      }
    }
  }

}
