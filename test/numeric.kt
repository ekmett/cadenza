import cadenza.RuntimeError
import cadenza.data.BigInt
import cadenza.interpreter.eval
import cadenza.interpreter.initialEnv
import cadenza.interpreter.parse
import com.oracle.truffle.api.source.Source
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger

class NumericTests {
  private fun context(backend: String) = Context.newBuilder("cadenza")
    .allowExperimentalOptions(true).option("cadenza.Backend", backend).build()

  private val numbers = listOf(
    "mult 46340 46340" to "2147395600",
    "mult 46341 46341" to "2147488281",
    "mult 2147483647 2147483647" to "4611686014132420609",
    "mult 2147483648 2147483648" to "4611686018427387904",
    "(mult 46341) 46341" to "2147488281",
    "div 4294967296 2" to "2147483648",
    "div 7 2147483648" to "0",
    "div 4294967296 2147483648" to "2",
    "mod 4294967297 2" to "1",
    "mod 7 2147483648" to "7",
    "mod 4294967297 2147483648" to "1",
    // Signed intermediates are established behavior; promotion preserves it.
    "minus 0 1" to "-1",
    "mult (minus 0 2147483648) (minus 0 1)" to "2147483648",
    "div (minus (minus 0 2147483647) 1) (minus 0 1)" to "2147483648",
    "div (minus 0 7) 3" to "-2",
    "mod (minus 0 7) 3" to "-1",
    "div 7 (minus 0 3)" to "-2",
    "mod 7 (minus 0 3)" to "1",
    "div (minus 0 4294967297) 3" to "-1431655765",
    "mod (minus 0 4294967297) 3" to "-2",
    "div 4294967297 (minus 0 2147483648)" to "-2",
    "mod 4294967297 (minus 0 2147483648)" to "1"
  )

  private val comparisons = listOf(
    "le (plus 2147483647 1) 2147483647" to false,
    "le 2147483647 (plus 2147483647 1)" to true,
    "le 2147483648 2147483648" to true,
    "eq 2147483648 2147483648" to true,
    "eq 2147483648 2147483649" to false,
    "eq (minus 2147483648 2147483647) 1" to true,
    "eq 1 (minus 2147483648 2147483647)" to true,
    "le (minus 0 2147483648) (minus 0 1)" to true,
    "eq (minus 0 2147483648) (minus (minus 0 2147483647) 1)" to true
  )

  @TestFactory fun integerArithmeticAgreesAcrossRepresentationsAndBackends(): List<DynamicTest> =
    listOf("ast", "bytecode").flatMap { backend -> numbers.map { (program, expected) ->
      DynamicTest.dynamicTest("$backend: $program") {
        context(backend).use { context ->
          assertEquals(BigInteger(expected), context.eval("cadenza", program).asBigInteger())
        }
      }
    } }

  @TestFactory fun comparisonsCoerceMixedIntAndBigIntInputs(): List<DynamicTest> =
    listOf("ast", "bytecode").flatMap { backend -> comparisons.map { (program, expected) ->
      DynamicTest.dynamicTest("$backend: $program") {
        context(backend).use { context ->
          assertEquals(expected, context.eval("cadenza", program).asBoolean())
        }
      }
    } }

  @Test fun promotedCallSitesContinueToAcceptSmallAndMixedInputs() {
    for (backend in listOf("ast", "bytecode")) {
      context(backend).use { context ->
        val big = context.eval("cadenza", "2147483648")
        val smallBig = context.eval("cadenza", "minus 2147483648 2147483647")
        val multiply = context.eval("cadenza", "\\(x : Nat) (y : Nat) -> mult x y")
        assertEquals(42, multiply.execute(6, 7).asInt())
        assertEquals(BigInteger("2147488281"), multiply.execute(46341, 46341).asBigInteger())
        assertEquals(BigInteger("4294967296"), multiply.execute(big, 2).asBigInteger())
        assertEquals(42, multiply.execute(6, 7).asInt())
        val equal = context.eval("cadenza", "\\(x : Nat) (y : Nat) -> eq x y")
        assertTrue(equal.execute(1, 1).asBoolean())
        assertTrue(equal.execute(smallBig, 1).asBoolean())
        assertTrue(equal.execute(1, smallBig).asBoolean())
        assertFalse(equal.execute(big, 1).asBoolean())
        assertTrue(equal.execute(1, 1).asBoolean())
        for (name in listOf("div", "mod")) {
          val function = context.eval("cadenza", "\\(x : Nat) (y : Nat) -> $name x y")
          assertEquals(if (name == "div") 3 else 1, function.execute(7, 2).asInt())
          assertEquals(if (name == "div") BigInteger("1073741824") else BigInteger.ZERO,
            function.execute(big, 2).asBigInteger())
          assertEquals(if (name == "div") 3 else 1, function.execute(7, 2).asInt())
        }
      }
    }
  }

  @TestFactory fun divisionAndModuloByZeroAreGuestErrors(): List<DynamicTest> {
    val programs = listOf(
      "div 1 0", "mod 1 0", "div 2147483648 0", "mod 2147483648 0",
      "div 1 (minus 2147483648 2147483648)", "mod 1 (minus 2147483648 2147483648)",
      "(div 1) 0", "(mod 1) 0"
    )
    return listOf("ast", "bytecode").flatMap { backend -> programs.map { program ->
      DynamicTest.dynamicTest("$backend: $program") {
        context(backend).use { context ->
          val error = assertThrows(PolyglotException::class.java) { context.eval("cadenza", program) }
          assertFalse(error.isInternalError)
          assertTrue(error.isGuestException)
          assertTrue(error.message!!.contains(if (program.contains("div")) "division by zero" else "modulo by zero"))
          assertEquals(42, context.eval("cadenza", "plus 40 2").asInt())
        }
      }
    } }
  }

  @Test fun zeroErrorsDoNotDisableWorkingCallSites() {
    for (backend in listOf("ast", "bytecode")) {
      context(backend).use { context ->
        val big = context.eval("cadenza", "2147483648")
        for (name in listOf("div", "mod")) {
          val function = context.eval("cadenza", "\\(x : Nat) (y : Nat) -> $name x y")
          assertThrows(PolyglotException::class.java) { function.execute(7, 0) }
          assertEquals(if (name == "div") 3 else 1, function.execute(7, 2).asInt())
          assertThrows(PolyglotException::class.java) { function.execute(big, 0) }
          assertEquals(if (name == "div") 3 else 1, function.execute(7, 2).asInt())
        }
      }
    }
  }

  @Test fun referenceInterpreterUsesTheSameIntegerSemantics() {
    for ((program, expected) in numbers) {
      val value = parse(Source.newBuilder("cadenza", program, "reference.za").build()).eval(initialEnv)
      val integer = when (value) {
        is Int -> BigInteger.valueOf(value.toLong())
        is BigInt -> value.value
        else -> error("unexpected numeric result $value")
      }
      assertEquals(BigInteger(expected), integer, program)
    }
    for ((program, expected) in comparisons) {
      assertEquals(expected,
        parse(Source.newBuilder("cadenza", program, "reference.za").build()).eval(initialEnv), program)
    }
    assertThrows(RuntimeError::class.java) {
      parse(Source.newBuilder("cadenza", "div 1 0", "zero.za").build()).eval(initialEnv)
    }
  }
}
