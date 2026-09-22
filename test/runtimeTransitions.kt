import cadenza.Language
import cadenza.RuntimeError
import cadenza.data.BigInt
import cadenza.data.Closure
import cadenza.data.Neutral
import cadenza.data.NeutralValue
import cadenza.jit.InteropApplyRootNode
import cadenza.jit.Minus
import cadenza.jit.Plus
import cadenza.jit.PlusNodeGen
import cadenza.semantics.Type
import com.oracle.truffle.api.source.Source
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Value
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger

class RuntimeTransitionTests {
  private val huge = BigInteger.ONE.shiftLeft(80).add(BigInteger.valueOf(123))

  private fun context(backend: String) = Context.newBuilder("cadenza")
    .allowExperimentalOptions(true).option("cadenza.Backend", backend).build()

  private fun bothBackends(name: String, test: (Context) -> Unit): List<DynamicTest> =
    listOf("ast", "bytecode").map { backend -> DynamicTest.dynamicTest("$backend: $name") {
      context(backend).use(test)
    } }

  // Closed-form oracles deliberately do not follow the guest's recursive control flow.
  private fun triangular(n: Int): BigInteger = BigInteger.valueOf(n.toLong())
    .multiply(BigInteger.valueOf(n.toLong() + 1)).divide(BigInteger.TWO)

  private fun integer(value: Any?): BigInteger = when (value) {
    is Int -> BigInteger.valueOf(value.toLong())
    is BigInt -> value.value
    else -> error("Expected a concrete guest integer, got $value")
  }

  private fun divisionError(action: () -> Unit) {
    val error = assertThrows(PolyglotException::class.java, action)
    assertTrue(error.isGuestException)
    assertFalse(error.isInternalError)
    assertTrue(error.message!!.contains("division by zero"), error.message)
  }

  @TestFactory fun recursiveFailuresDoNotCorruptLaterCallsOrTheirContinuations() =
    bothBackends("recursive numeric histories and exception recovery") { context ->
      val function = context.eval("cadenza", """
        \(seed : Nat) (count : Nat) (divisor : Nat) ->
          let go : Nat -> Nat -> Nat = \(n : Nat) (acc : Nat) ->
            if le n 0 then div acc divisor else go (minus n 1) (plus acc n)
          in plus 17 (go count seed)
      """.trimIndent())
      val cases = listOf(
        Triple(BigInteger.valueOf(1000), 300, 7),
        Triple(BigInteger.valueOf(Int.MAX_VALUE.toLong()), 257, 3),
        Triple(huge, 401, 11),
        Triple(BigInteger.valueOf(42), 0, 2),
        Triple(BigInteger.valueOf(900), 1001, 13)
      )
      // Replay in reverse after promotion and errors: a new context would hide stale state.
      for ((seed, count, divisor) in cases + cases.reversed()) {
        val argument = context.eval("cadenza", seed.toString())
        val expected = seed.add(triangular(count)).divide(BigInteger.valueOf(divisor.toLong()))
          .add(BigInteger.valueOf(17))
        assertEquals(expected, function.execute(argument, count, divisor).asBigInteger())
        divisionError { function.execute(argument, count + 1, 0) }
        assertEquals(expected, function.execute(argument, count, divisor).asBigInteger())
      }
      // Exercise an object-represented counter as well as an object accumulator.
      val three = context.eval("cadenza", "minus 2147483651 2147483648")
      assertEquals(BigInteger.valueOf(34), function.execute(11, three, 1).asBigInteger())
      assertEquals(BigInteger.valueOf(34), function.execute(11, 3, 1).asBigInteger())
    }

  @TestFactory fun escapingCapturesKeepTheValueFromTheirOwnTailIteration() =
    bothBackends("captured snapshots across frame reuse and later calls") { context ->
      val make = context.eval("cadenza", """
        \(seed : Nat) (steps : Nat) ->
          let go : Nat -> Nat -> (Nat -> Nat) -> (Nat -> Nat) =
            \(n : Nat) (acc : Nat) (saved : Nat -> Nat) ->
              if le n 0 then saved else
                let snapshot : Nat -> Nat = \(extra : Nat) -> plus acc extra in
                go (minus n 1) (plus acc n)
                  (if eq (mod n 3) 0 then snapshot else saved)
          in go steps seed (\(extra : Nat) -> plus seed extra)
      """.trimIndent())
      val escaped = mutableListOf<Pair<Value, BigInteger>>()
      for (seed in listOf(BigInteger.valueOf(1000), huge, BigInteger.valueOf(2000))) {
        val argument = context.eval("cadenza", seed.toString())
        for (steps in listOf(0, 1, 2, 3, 4, 17, 512)) {
          // The final saved snapshot is at n=3, before adding 3+2+1; shorter runs
          // retain the initial closure. Neither case observes the loop's final acc.
          val expected = if (steps < 3) seed else seed.add(triangular(steps)).subtract(triangular(3))
          escaped += make.execute(argument, steps) to expected
        }
      }
      for ((closure, captured) in escaped.reversed() + escaped) {
        for (extra in listOf(0, 1, 1000)) {
          assertEquals(captured.add(BigInteger.valueOf(extra.toLong())), closure.execute(extra).asBigInteger())
        }
      }
    }

  @TestFactory fun saturatedDispatchPreservesPartialArgumentsAcrossFailures() =
    bothBackends("weighted partial and overapplication histories") { context ->
      val signature = "Nat -> Nat -> Nat -> Nat -> Nat"
      val partialApply = context.eval("cadenza",
        "\\(f : $signature) (a : Nat) (b : Nat) -> f a b")
      val fullApply = context.eval("cadenza",
        "\\(f : $signature) (a : Nat) (b : Nat) (c : Nat) (d : Nat) -> f a b c d")
      val shapes = listOf(listOf(1, 1, 1, 1), listOf(2, 2), listOf(3, 1), listOf(4),
        listOf(1, 3), listOf(2, 1, 1), listOf(1, 2, 1), listOf(1, 1, 2))
      val names = listOf("a", "b", "c", "d")
      val functions = shapes.mapIndexed { index, shape ->
        var offset = 0
        val lambdas = shape.joinToString("") { arity ->
          val binders = names.subList(offset, offset + arity).joinToString(" ") { "($it : Nat)" }
          offset += arity
          "\\$binders -> "
        }
        context.eval("cadenza", lambdas +
          "div (plus $index (plus (mult 1000000 a) (plus (mult 1000 b) (mult 7 c)))) d")
      }
      data class Saved(val partial: Value, val numerator: BigInteger, val c: Int, val d: Int)
      val saved = mutableListOf<Saved>()
      for (index in functions.indices.toList() + functions.indices.reversed()) {
        val a = if (index % 2 == 0) BigInteger.valueOf((1000 + index).toLong()) else huge.add(BigInteger.valueOf(index.toLong()))
        val aValue = context.eval("cadenza", a.toString())
        val b = 20 + index
        val c = 30 + index
        val d = 1 + index % 3
        val numerator = a.multiply(BigInteger.valueOf(1000000))
          .add(BigInteger.valueOf((1000 * b + 7 * c + index).toLong()))
        val expected = numerator.divide(BigInteger.valueOf(d.toLong()))
        val function = functions[index]
        assertEquals(expected, fullApply.execute(function, aValue, b, c, d).asBigInteger())
        divisionError { fullApply.execute(function, aValue, b, c, 0) }
        assertEquals(expected, fullApply.execute(function, aValue, b, c, d).asBigInteger())
        saved += Saved(partialApply.execute(function, aValue, b), numerator, c, d)
      }
      // These closures survive cache replacement and later applications of the same roots.
      for ((partial, numerator, c, d) in saved.reversed()) {
        divisionError { partial.execute(c, 0) }
        val expected = numerator.divide(BigInteger.valueOf(d.toLong()))
        assertEquals(expected, partial.execute(c, d).asBigInteger())
        assertEquals(expected, partial.execute(c).execute(d).asBigInteger())
      }
    }

  private fun withAst(action: (Language) -> Unit) {
    context("ast").use { context ->
      context.initialize("cadenza")
      context.enter()
      try { action(Language.currentLanguage()) } finally { context.leave() }
    }
  }

  private fun parse(language: Language, text: String): Closure = language.parse(
    Source.newBuilder("cadenza", text, "runtime-transitions.za").build()).call() as Closure

  private fun symbolic(type: Type) = NeutralValue(type,
    Neutral.NCallBuiltin(PlusNodeGen.create(), emptyArray()))

  // An independent evaluator for this test's residual arithmetic. It never calls guest
  // builtins; subtraction also makes the ordering of residual operands observable.
  private fun substituteArithmetic(value: Any?, variable: NeutralValue, replacement: BigInteger): BigInteger {
    if (value !is NeutralValue) return integer(value)
    assertEquals(Type.Nat, value.type)
    if (value.term === variable.term) return replacement
    val application = value.term as Neutral.NCallBuiltin
    assertEquals(2, application.args.size)
    val left = substituteArithmetic(application.args[0], variable, replacement)
    val right = substituteArithmetic(application.args[1], variable, replacement)
    return when (application.builtin) {
      is Plus -> left.add(right)
      is Minus -> left.subtract(right)
      else -> error("Unexpected residual operation ${application.builtin}")
    }
  }

  @Test fun neutralHistoriesKeepResidualsValidAfterConcreteCallsResume() = withAst { language ->
    // The experimental bytecode backend intentionally supports concrete evaluation only.
    val function = parse(language, """
      let go : Nat -> Nat -> Nat = \(remaining : Nat) (acc : Nat) ->
        if le remaining 0 then acc else go (minus remaining 1) (minus acc remaining)
      in go
    """.trimIndent())
    val apply = InteropApplyRootNode(language, 2).callTarget
    val variable = symbolic(Type.Nat)
    val residuals = mutableListOf<Pair<Any?, Int>>()
    for (count in listOf(0, 1, 7, 24, 3, 12)) {
      for (seed in listOf<Any>(1000, BigInt(huge), 2000)) {
        assertEquals(integer(seed).subtract(triangular(count)),
          integer(apply.call(function, arrayOf<Any?>(count, seed))))
        residuals += apply.call(function, arrayOf<Any?>(count, variable)) to count
        assertEquals(BigInteger.valueOf(41).subtract(triangular(count)),
          integer(apply.call(function, arrayOf<Any?>(count, 41))))
      }
    }
    for ((residual, count) in residuals.reversed()) {
      for (replacement in listOf(BigInteger.ZERO, BigInteger.valueOf(37), huge)) {
        assertEquals(replacement.subtract(triangular(count)), substituteArithmetic(residual, variable, replacement))
      }
    }
  }

  @Test fun neutralBranchesFinishBothTailComputationsWithoutContaminatingLaterCalls() = withAst { language ->
    val function = parse(language, """
      let go : Nat -> Nat -> Nat = \(remaining : Nat) (acc : Nat) ->
        if le remaining 0 then acc else go (minus remaining 1) (plus acc remaining)
      in \(choose : Bool) (seed : Nat) (steps : Nat) ->
        if choose then go steps seed else go (plus steps 1) (plus seed 100)
    """.trimIndent())
    val apply = InteropApplyRootNode(language, 3).callTarget
    val variable = symbolic(Type.Bool)
    val saved = mutableListOf<Triple<Neutral.NIf, BigInteger, BigInteger>>()
    for (seed in listOf<Any>(1000, BigInt(huge), 2000)) {
      for (steps in listOf(0, 1, 37, 1000)) {
        val yes = integer(seed).add(triangular(steps))
        val no = integer(seed).add(BigInteger.valueOf(100)).add(triangular(steps + 1))
        assertEquals(yes, integer(apply.call(function, arrayOf<Any?>(true, seed, steps))))
        val residual = apply.call(function, arrayOf<Any?>(variable, seed, steps)) as NeutralValue
        assertEquals(Type.Nat, residual.type)
        val branches = residual.term as Neutral.NIf
        assertSame(variable.term, branches.body)
        saved += Triple(branches, yes, no)
        assertEquals(no, integer(apply.call(function, arrayOf<Any?>(false, seed, steps))))
        assertEquals(yes, integer(apply.call(function, arrayOf<Any?>(true, seed, steps))))
      }
    }
    for ((branches, yes, no) in saved.reversed()) {
      assertEquals(yes, integer(branches.thenValue))
      assertEquals(no, integer(branches.elseValue))
    }
  }

  @Test fun graalCompiledRecursionRecoversAfterPromotionNeutralAndGuestFailure() {
    CompilationTestSupport.requireOptimizingRuntime()
    CompilationTestSupport.context().use { context ->
        context.initialize("cadenza")
        context.enter()
        try {
          val function = parse(Language.currentLanguage(), """
            let go : Nat -> Nat -> Nat -> Nat = \(n : Nat) (acc : Nat) (divisor : Nat) ->
              if le n 0 then (if eq divisor 1 then acc else div acc divisor)
              else go (minus n 1) (plus acc n) divisor
            in go
          """.trimIndent())
          val target = function.callTarget
          fun call(n: Int, seed: Any, divisor: Int = 1): Any? {
            val prefix = if (function.env == null) arrayOf<Any?>(0L) else arrayOf<Any?>(0L, function.env)
            val arguments = cadenza.data.append(cadenza.data.append(prefix, function.papArgs),
              arrayOf<Any?>(n, seed, divisor))
            return target.call(*arguments)
          }
          fun compileConcretePath() {
            repeat(24) { n ->
              assertEquals(BigInteger.valueOf(1000).add(triangular(n)), integer(call(n, 1000)))
            }
            CompilationTestSupport.compileAndVerify(target)
          }
          compileConcretePath()
          assertEquals(huge.add(triangular(17)), integer(call(17, BigInt(huge))))
          compileConcretePath()
          val variable = symbolic(Type.Nat)
          val residual = call(17, variable)
          assertEquals(huge.add(triangular(17)), substituteArithmetic(residual, variable, huge))
          compileConcretePath()
          assertThrows(RuntimeError::class.java) { call(17, 1000, 0) }
          compileConcretePath()
          assertEquals(BigInteger.valueOf(1000).add(triangular(23)), integer(call(23, 1000)))
        } finally {
          context.leave()
        }
      }
  }
}
