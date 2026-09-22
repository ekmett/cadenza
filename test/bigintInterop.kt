import cadenza.data.BigInt
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.UnsupportedMessageException
import org.graalvm.polyglot.Context
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigInteger

class BigIntInteropTests {
  private val interop = InteropLibrary.getUncached()
  private val one = BigInteger.ONE

  @Test fun floatingConversionsAcceptExactIntegersBeyondTheConsecutiveIntegerRange() {
    val floatCases = listOf(
      BigInteger.ZERO,
      one,
      one.shiftLeft(24) - one,
      one.shiftLeft(24),
      one.shiftLeft(24) + BigInteger.TWO,
      one.shiftLeft(31),
      one.shiftLeft(63),
      one.shiftLeft(100),
      one.shiftLeft(100) + one.shiftLeft(77),
      one.shiftLeft(127),
      BigDecimal(Float.MAX_VALUE.toDouble()).toBigIntegerExact()
    )
    for (integer in floatCases.flatMap { listOf(it, it.negate()) }) {
      val number = BigInt(integer)
      assertTrue(interop.fitsInFloat(number), integer.toString())
      assertEquals(integer, BigDecimal(interop.asFloat(number).toDouble()).toBigIntegerExact())
      assertTrue(interop.fitsInDouble(number), integer.toString())
      assertEquals(integer, BigDecimal(interop.asDouble(number)).toBigIntegerExact())
    }

    val doubleCases = listOf(
      one.shiftLeft(53) - one,
      one.shiftLeft(53),
      one.shiftLeft(53) + BigInteger.TWO,
      one.shiftLeft(100) + one.shiftLeft(48),
      one.shiftLeft(128),
      one.shiftLeft(1023),
      BigDecimal(Double.MAX_VALUE).toBigIntegerExact()
    )
    for (integer in doubleCases.flatMap { listOf(it, it.negate()) }) {
      val number = BigInt(integer)
      assertTrue(interop.fitsInDouble(number), integer.toString())
      assertEquals(integer, BigDecimal(interop.asDouble(number)).toBigIntegerExact())
    }
  }

  @Test fun floatingConversionsRejectRoundingAndOverflow() {
    val inexactFloat = listOf(
      one.shiftLeft(24) + one,
      one.shiftLeft(100) + one.shiftLeft(76),
      BigDecimal(Float.MAX_VALUE.toDouble()).toBigIntegerExact() + one,
      one.shiftLeft(128),
      one.shiftLeft(4096)
    )
    for (integer in inexactFloat.flatMap { listOf(it, it.negate()) }) {
      val number = BigInt(integer)
      assertFalse(interop.fitsInFloat(number), integer.toString())
      assertThrows(UnsupportedMessageException::class.java) { interop.asFloat(number) }
    }
    val inexactDouble = listOf(
      one.shiftLeft(53) + one,
      one.shiftLeft(100) + one.shiftLeft(47),
      BigDecimal(Double.MAX_VALUE).toBigIntegerExact() + one,
      one.shiftLeft(1024),
      one.shiftLeft(4096)
    )
    for (integer in inexactDouble.flatMap { listOf(it, it.negate()) }) {
      val number = BigInt(integer)
      assertFalse(interop.fitsInDouble(number), integer.toString())
      assertThrows(UnsupportedMessageException::class.java) { interop.asDouble(number) }
    }
  }

  @Test fun representabilityAgreesWithExactDecimalRoundTripsAtExponentBoundaries() {
    // BigDecimal(double) preserves the actual binary value rather than its shortest
    // printed decimal. This independently checks the bit-based representability test.
    val exponents = listOf(0, 1, 23, 24, 25, 31, 52, 53, 54, 63, 100, 126, 127, 128, 129, 511, 1022, 1023, 1024)
    for (exponent in exponents) {
      val power = one.shiftLeft(exponent)
      val values = listOf(power - one, power, power + one, power + BigInteger.TWO)
      for (integer in values.flatMap { listOf(it, it.negate()) }) {
        val number = BigInt(integer)
        val asFloat = integer.toFloat()
        val asDouble = integer.toDouble()
        val exactFloat = asFloat.isFinite() && BigDecimal(asFloat.toDouble()).toBigIntegerExact() == integer
        val exactDouble = asDouble.isFinite() && BigDecimal(asDouble).toBigIntegerExact() == integer
        assertEquals(exactFloat, interop.fitsInFloat(number), "float: $integer")
        assertEquals(exactDouble, interop.fitsInDouble(number), "double: $integer")
      }
    }
  }

  @Test fun polyglotValuesExposeExactConversionsAndRejectLossyOnes() {
    Context.create("cadenza").use { context ->
      val floatMaximum = context.asValue(BigInt(BigDecimal(Float.MAX_VALUE.toDouble()).toBigIntegerExact()))
      assertTrue(floatMaximum.fitsInFloat())
      assertEquals(Float.MAX_VALUE, floatMaximum.asFloat())

      val doubleMaximum = context.asValue(BigInt(BigDecimal(Double.MAX_VALUE).toBigIntegerExact()))
      assertTrue(doubleMaximum.fitsInDouble())
      assertEquals(Double.MAX_VALUE, doubleMaximum.asDouble())
      assertFalse(doubleMaximum.fitsInFloat())
      assertThrows(ClassCastException::class.java) { doubleMaximum.asFloat() }

      val largeNegative = context.asValue(BigInt(one.shiftLeft(100).negate()))
      assertTrue(largeNegative.fitsInFloat())
      assertTrue(largeNegative.fitsInDouble())
      assertEquals(-Math.scalb(1.0f, 100), largeNegative.asFloat())
      assertEquals(-Math.scalb(1.0, 100), largeNegative.asDouble())

      val inexact = context.asValue(BigInt(one.shiftLeft(53) + one))
      assertFalse(inexact.fitsInDouble())
      assertThrows(ClassCastException::class.java) { inexact.asDouble() }
      val overflow = context.asValue(BigInt(one.shiftLeft(1024)))
      assertFalse(overflow.fitsInDouble())
      assertThrows(ClassCastException::class.java) { overflow.asDouble() }
    }
  }
}
