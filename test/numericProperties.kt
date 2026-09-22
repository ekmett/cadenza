import cadenza.data.BigInt
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Value
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.util.Random

/** Independent numeric oracles exercise live call sites, not closed constant programs. */
class NumericPropertyTests {
  private val one = BigInteger.ONE
  private val intMaximum = BigInteger.valueOf(Int.MAX_VALUE.toLong())
  private val seed = 0x434144454E5A41L

  private fun context(backend: String) = Context.newBuilder("cadenza")
    .allowExperimentalOptions(true).option("cadenza.Backend", backend).build()

  private data class Operands(val left: BigInteger, val right: BigInteger)

  private fun operands(): List<Operands> {
    fun pair(left: Long, right: Long) = Operands(BigInteger.valueOf(left), BigInteger.valueOf(right))
    val cases = mutableListOf(
      pair(0, 0), pair(1, 2), pair(-1, 2), pair(Int.MIN_VALUE.toLong(), -1),
      pair(Int.MAX_VALUE.toLong(), 1), pair(Int.MIN_VALUE.toLong(), 1),
      pair(46341, 46341), pair(7, -3), pair(-7, 3), pair(-7, -3)
    )
    for (exponent in listOf(24, 31, 32, 53, 63, 64, 100, 127, 128, 255)) {
      val power = one.shiftLeft(exponent)
      cases += Operands(power - one, power + one)
      cases += Operands(-power, power - one)
      cases += Operands(power + one, -power + one)
      cases += Operands(power, BigInteger.ZERO)
      cases += Operands(-power, -one)
      // Exercise true equality after the comparison sites have seen mixed ranges.
      cases += Operands(power, power)
      cases += Operands(-power, -power)
    }
    val random = Random(seed)
    repeat(48) {
      fun integer(): BigInteger {
        val magnitude = BigInteger(1 + random.nextInt(256), random)
        return if (random.nextBoolean()) magnitude else magnitude.negate()
      }
      cases += Operands(integer(), integer())
    }
    // Return to small values after each operation has seen large operands and errors.
    cases += pair(40, 2)
    cases += pair(-40, 2)
    cases += pair(7, -3)
    return cases
  }

  private fun signedFunction(context: Context, expression: String): Value = context.eval("cadenza",
    "\\(ap : Nat) (an : Nat) (at : Nat) (bp : Nat) (bn : Nat) (bt : Nat) -> " +
      "let a : Nat = minus (minus ap an) at in " +
      "let b : Nat = minus (minus bp bn) bt in $expression")

  /** Build signed values inside the guest; the public Nat boundary must remain nonnegative. */
  private fun arguments(operands: Operands, forceBig: Boolean = false): Array<Any> {
    fun unsigned(value: BigInteger): Any = when {
      forceBig -> BigInt(value)
      value <= intMaximum -> value.toInt()
      else -> value
    }
    fun signed(value: BigInteger): List<Any> = if (value.signum() >= 0) {
      listOf(unsigned(value), unsigned(BigInteger.ZERO), unsigned(BigInteger.ZERO))
    } else {
      // -2147483648 becomes (0 - 2147483647) - 1, so the Int.MIN_VALUE /
      // -1 edge really reaches integer division instead of arriving as BigInt.
      listOf(unsigned(BigInteger.ZERO), unsigned(value.negate() - one), unsigned(one))
    }
    return (signed(operands.left) + signed(operands.right)).toTypedArray()
  }

  @Test fun liveArithmeticAndComparisonSitesAgreeWithBigIntegerThroughPromotion() {
    val operations: Map<String, (BigInteger, BigInteger) -> BigInteger> = linkedMapOf(
      "plus" to { left, right -> left.add(right) },
      "minus" to { left, right -> left.subtract(right) },
      "mult" to { left, right -> left.multiply(right) }
    )
    for (backend in listOf("ast", "bytecode")) {
      context(backend).use { context ->
        val arithmetic = operations.mapValues { (name, _) -> signedFunction(context, "$name a b") }
        val equal = signedFunction(context, "eq a b")
        val lessOrEqual = signedFunction(context, "le a b")
        for (forceBig in listOf(false, true)) {
          val cases = if (forceBig) operands().reversed() else operands()
          for ((index, values) in cases.withIndex()) {
            val input = arguments(values, forceBig)
            val location = "$backend seed=$seed case=$index forceBig=$forceBig $values"
            for ((name, oracle) in operations) {
              assertEquals(oracle(values.left, values.right), arithmetic.getValue(name).execute(*input).asBigInteger(), "$name: $location")
            }
            assertEquals(values.left == values.right, equal.execute(*input).asBoolean(), "eq: $location")
            assertEquals(values.left <= values.right, lessOrEqual.execute(*input).asBoolean(), "le: $location")
          }
        }
      }
    }
  }

  @Test fun signedDivisionAndRemainderReconstructTheDividendAfterErrorsAndOverflow() {
    for (backend in listOf("ast", "bytecode")) {
      context(backend).use { context ->
        val divide = signedFunction(context, "div a b")
        val remainder = signedFunction(context, "mod a b")
        val reconstruct = signedFunction(context, "plus (mult (div a b) b) (mod a b)")
        for ((index, values) in operands().withIndex()) {
          val input = arguments(values)
          val location = "$backend seed=$seed case=$index $values"
          if (values.right.signum() == 0) {
            for ((name, function) in listOf("division" to divide, "modulo" to remainder)) {
              val error = assertThrows(PolyglotException::class.java, { function.execute(*input) }, location)
              assertTrue(error.isGuestException, location)
              assertFalse(error.isInternalError, location)
              assertTrue(error.message!!.contains("$name by zero"), location)
            }
            // The same nodes must work immediately after the guest exception.
            val recovery = arguments(Operands(BigInteger.valueOf(-7), BigInteger.valueOf(3)))
            assertEquals(BigInteger.valueOf(-2), divide.execute(*recovery).asBigInteger(), location)
            assertEquals(BigInteger.valueOf(-1), remainder.execute(*recovery).asBigInteger(), location)
            continue
          }
          val expected = values.left.divideAndRemainder(values.right)
          val quotient = divide.execute(*input).asBigInteger()
          val residual = remainder.execute(*input).asBigInteger()
          assertEquals(expected[0], quotient, "quotient: $location")
          assertEquals(expected[1], residual, "remainder: $location")
          assertEquals(values.left, quotient * values.right + residual, "host reconstruction: $location")
          assertEquals(values.left, reconstruct.execute(*input).asBigInteger(), "guest reconstruction: $location")
          assertTrue(residual.abs() < values.right.abs(), "remainder bound: $location")
          assertTrue(residual.signum() == 0 || residual.signum() == values.left.signum(), "remainder sign: $location")
        }
      }
    }
  }

  @Test fun signedGuestResultsExposeExactIntegralInteropRanges() {
    val values = mutableSetOf(BigInteger.ZERO)
    for (bits in listOf(7, 15, 31, 63, 100)) {
      val edge = one.shiftLeft(bits)
      for (offset in -1L..1L) {
        values += edge + BigInteger.valueOf(offset)
        values += -edge + BigInteger.valueOf(offset)
      }
    }
    for (backend in listOf("ast", "bytecode")) {
      context(backend).use { context ->
        val identity = signedFunction(context, "a")
        for (forceBig in listOf(false, true)) {
          for (integer in values) {
            val value = identity.execute(*arguments(Operands(integer, BigInteger.ZERO), forceBig))
            val location = "$backend forceBig=$forceBig integer=$integer"
            assertTrue(value.isNumber, location)
            assertTrue(value.fitsInBigInteger(), location)
            assertEquals(integer, value.asBigInteger(), location)
            val fitsByte = integer >= BigInteger.valueOf(Byte.MIN_VALUE.toLong()) && integer <= BigInteger.valueOf(Byte.MAX_VALUE.toLong())
            val fitsShort = integer >= BigInteger.valueOf(Short.MIN_VALUE.toLong()) && integer <= BigInteger.valueOf(Short.MAX_VALUE.toLong())
            val fitsInt = integer >= BigInteger.valueOf(Int.MIN_VALUE.toLong()) && integer <= intMaximum
            val fitsLong = integer >= BigInteger.valueOf(Long.MIN_VALUE) && integer <= BigInteger.valueOf(Long.MAX_VALUE)
            assertEquals(fitsByte, value.fitsInByte(), location)
            assertEquals(fitsShort, value.fitsInShort(), location)
            assertEquals(fitsInt, value.fitsInInt(), location)
            assertEquals(fitsLong, value.fitsInLong(), location)
            if (fitsByte) assertEquals(integer.byteValueExact(), value.asByte(), location)
            else assertThrows(ClassCastException::class.java, { value.asByte() }, location)
            if (fitsShort) assertEquals(integer.shortValueExact(), value.asShort(), location)
            else assertThrows(ClassCastException::class.java, { value.asShort() }, location)
            if (fitsInt) assertEquals(integer.intValueExact(), value.asInt(), location)
            else assertThrows(ClassCastException::class.java, { value.asInt() }, location)
            if (fitsLong) assertEquals(integer.longValueExact(), value.asLong(), location)
            else assertThrows(ClassCastException::class.java, { value.asLong() }, location)
          }
        }
      }
    }
  }

  @Test fun invalidLaterInteropArgumentsCannotExecuteAnEarlierCurriedBody() {
    for (backend in listOf("ast", "bytecode")) {
      val output = ByteArrayOutputStream()
      Context.newBuilder("cadenza").allowExperimentalOptions(true)
        .option("cadenza.Backend", backend).out(output).build().use { context ->
          val function = context.eval("cadenza",
            "\\(x : Nat) -> let printed : Nat = printId x in \\(y : Nat) -> plus printed y")
          for (invalid in listOf(-1L, 1.5, Double.NaN, -0.0, "2")) {
            assertThrows(IllegalArgumentException::class.java) { function.execute(40L, invalid) }
            assertEquals("", output.toString(Charsets.UTF_8), "$backend: $invalid")
          }
          assertEquals(42, function.execute(40L, BigInteger.TWO).asInt())
          assertEquals("40\n", output.toString(Charsets.UTF_8), backend)
          assertThrows(IllegalArgumentException::class.java) { function.execute(40L, 2L, 0L) }
          assertEquals("40\n", output.toString(Charsets.UTF_8), backend)
          val partial = function.execute(7L)
          assertEquals("40\n7\n", output.toString(Charsets.UTF_8), backend)
          assertThrows(IllegalArgumentException::class.java) { partial.execute(0.5) }
          assertEquals("40\n7\n", output.toString(Charsets.UTF_8), backend)
          assertEquals(42, partial.execute(35L).asInt())
        }
    }
  }
}
