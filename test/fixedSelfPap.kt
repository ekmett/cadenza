import cadenza.Language
import cadenza.data.BigInt
import cadenza.data.Closure
import cadenza.data.Neutral
import cadenza.data.NeutralValue
import cadenza.jit.BuiltinRootNode
import cadenza.jit.ClosureRootNode
import cadenza.jit.FixNatFNodeGen
import cadenza.jit.Minus
import cadenza.jit.Plus
import cadenza.jit.PlusNodeGen
import cadenza.jit.natF
import cadenza.jit.natFF
import cadenza.semantics.Type
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.source.Source
import org.graalvm.polyglot.Context
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigInteger

class FixedSelfPapTests {
  // The inner root has four physical parameters. Supplying two different prefix
  // types leaves the binary function accepted by fixNatF, with its capture intact.
  private val factorySource = """
    \(captured : Nat) ->
      \(scale : Nat) (positive : Bool) (self : Nat -> Nat) (n : Nat) ->
        if le n 0 then captured else
          if positive then plus (mult scale n) (self (minus n 1))
          else minus (self (minus n 1)) (mult scale n)
  """.trimIndent()

  private fun withLanguage(action: () -> Unit) {
    Context.create("cadenza").use { context ->
      context.initialize("cadenza")
      context.enter()
      try { action() } finally { context.leave() }
    }
  }

  private fun factory(): Closure = Language.currentLanguage().parse(
    Source.newBuilder("cadenza", factorySource, "fixed-self-prefix.za").build()).call() as Closure

  private fun expected(captured: BigInteger, scale: Int, positive: Boolean, n: Int): BigInteger {
    val change = BigInteger.valueOf(scale.toLong()).multiply(BigInteger.valueOf(n.toLong()))
      .multiply(BigInteger.valueOf(n.toLong() + 1)).divide(BigInteger.TWO)
    return if (positive) captured.add(change) else captured.subtract(change)
  }

  @Test fun bindingSelfPreservesOriginalTypeMixedPrefixAndCapture() = withLanguage {
    val interop = InteropLibrary.getUncached()
    val function = interop.execute(factory(), 10000) as Closure
    assertEquals(4, (function.callTarget.rootNode as ClosureRootNode).arity)
    val originalType = function.type
    val prefix = arrayOf<Any?>(100, true)
    val partiallyApplied = function.pap(prefix)
    assertEquals(2, partiallyApplied.arity)
    assertEquals(natFF, partiallyApplied.type)
    val builtin = FixNatFNodeGen.create()
    BuiltinRootNode(Language.currentLanguage(), builtin).callTarget
    val fixed = builtin.mkFix(partiallyApplied)
    assertEquals(1, fixed.arity)
    assertEquals(natF, fixed.type)
    assertSame(function.callTarget, fixed.callTarget)
    assertSame(function.env, fixed.env)
    assertEquals(3, fixed.papArgs.size)
    assertEquals(100, fixed.papArgs[0])
    assertEquals(true, fixed.papArgs[1])
    assertNotSame(partiallyApplied.papArgs, fixed.papArgs)
    assertArrayEquals(prefix, partiallyApplied.papArgs)
    assertEquals(originalType, function.type)
    assertEquals(natFF, partiallyApplied.type)
    assertEquals(11500, interop.execute(fixed, 5))
    // The source prefix remains reusable after the new self has been bound.
    assertEquals(11500, interop.execute(partiallyApplied, fixed, 5))
    assertEquals(12100, interop.execute(fixed, 6))
  }

  @Test fun exportedPrefixedFixedFunctionsKeepCapturesAfterCacheSaturation() {
    for (backend in listOf("ast", "bytecode")) {
      Context.newBuilder("cadenza").allowExperimentalOptions(true)
        .option("cadenza.Backend", backend).build().use { context ->
          val make = context.eval("cadenza", factorySource)
          val fix = context.eval("cadenza", "fixNatF")
          val cases = (0 until 8).map { index ->
            val captured = if (index % 2 == 0) BigInteger.valueOf(10000L + index)
              else BigInteger.ONE.shiftLeft(100).add(BigInteger.valueOf(index.toLong()))
            val scale = 10 + index
            val positive = index % 3 != 0
            val prefix = make.execute(context.eval("cadenza", captured.toString())).execute(scale, positive)
            val fixed = fix.execute(prefix)
            assertEquals(expected(captured, scale, positive, 5), fixed.execute(5).asBigInteger(), backend)
            Triple(fixed, captured, scale to positive)
          }
          // Every partial survives replacement of the shared fixNatF site's bounded cache.
          for ((fixed, captured, arguments) in cases.reversed() + cases) {
            val (scale, positive) = arguments
            for (n in listOf(0, 1, 12)) {
              assertEquals(expected(captured, scale, positive, n), fixed.execute(n).asBigInteger(), backend)
            }
          }
        }
    }
  }

  @Test fun compiledGuestBindingPreservesMixedPrefixesAcrossPromotionAndRecovery() {
    CompilationTestSupport.requireOptimizingRuntime()
    CompilationTestSupport.context().use { context ->
      context.initialize("cadenza")
      context.enter()
      try {
        // Both the capture and the two-argument PAP are created inside this guest
        // driver, so its installed code includes the new self-binding path.
        val driver = Language.currentLanguage().parse(Source.newBuilder("cadenza", """
          \(captured : Nat) (scale : Nat) (positive : Bool) (count : Nat) ->
            let function : Nat -> Bool -> (Nat -> Nat) -> Nat -> Nat =
              ($factorySource) captured
            in fixNatF (function scale positive) count
        """.trimIndent(), "compiled-fixed-self-prefix.za").build()).call() as Closure
        assertNull(driver.env)
        val target = driver.callTarget
        fun check(captured: Any, scale: Int, positive: Boolean, count: Int) {
          val seed = if (captured is BigInt) captured.value else BigInteger.valueOf((captured as Int).toLong())
          val result = target.call(0L, captured, scale, positive, count)
          val actual = if (result is BigInt) result.value else BigInteger.valueOf((result as Int).toLong())
          assertEquals(expected(seed, scale, positive, count), actual)
        }
        fun compileConcretePath() {
          // Fresh captures and differing mixed prefixes exceed the three-entry
          // fixed-point cache before installation; both arithmetic branches run.
          repeat(24) { index -> check(10000 + 11 * index, 1 + index % 7, index % 2 == 0, index % 12) }
          CompilationTestSupport.compileAndVerify(target)
        }
        compileConcretePath()
        check(12345, 23, true, 17)
        check(23456, 31, false, 19)
        check(BigInt(BigInteger.ONE.shiftLeft(100)), 29, false, 13)
        compileConcretePath()
        check(34567, 37, false, 23)
        check(45678, 41, true, 29)
      } finally {
        context.leave()
      }
    }
  }

  @Test fun prefixedFixedFunctionsPreserveNeutralCapturesThroughCacheSaturation() = withLanguage {
    val make = factory()
    val builtin = FixNatFNodeGen.create()
    val target = BuiltinRootNode(Language.currentLanguage(), builtin).callTarget
    val cases = (0 until 8).map { index ->
      val symbolic = NeutralValue(Type.Nat, Neutral.NCallBuiltin(PlusNodeGen.create(), arrayOf(index, null)))
      val captured: Any = if (index % 2 == 0) 10000 + index else symbolic
      // The guest's internal neutral path deliberately bypasses host Nat validation.
      val function = make.callTarget.call(0L, captured) as Closure
      val scale = index + 1
      val positive = index % 3 != 0
      val prefix = function.pap(arrayOf<Any?>(scale, positive))
      Triple(prefix, captured, scale to positive)
    }
    for ((prefix, captured, arguments) in cases + cases.reversed()) {
      val (scale, positive) = arguments
      val zero = target.call(0L, prefix, 0)
      val one = target.call(0L, prefix, 1)
      if (captured is NeutralValue) {
        assertEquals(Type.Nat, (zero as NeutralValue).type)
        assertSame(captured.term, zero.term)
        val application = (one as NeutralValue).term as Neutral.NCallBuiltin
        assertTrue(if (positive) application.builtin is Plus else application.builtin is Minus)
        val neutralIndex = if (positive) 1 else 0
        assertSame(captured.term, (application.args[neutralIndex] as NeutralValue).term)
        assertEquals(scale, application.args[1 - neutralIndex])
      } else {
        assertEquals(captured, zero)
        assertEquals((captured as Int) + if (positive) scale else -scale, one)
      }
      assertArrayEquals(arrayOf<Any?>(scale, positive), prefix.papArgs)
      assertEquals(natFF, prefix.type)
    }
  }
}
