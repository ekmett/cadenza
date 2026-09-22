package cadenza.data

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.CompilerDirectives.ValueType
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnsupportedMessageException
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import java.math.BigInteger

@ValueType
@ExportLibrary(InteropLibrary::class)
class BigInt(val value: BigInteger) : TruffleObject, Comparable<BigInt> {
  constructor(value: Long) : this(BigInteger.valueOf(value))

  @ExportMessage
  @TruffleBoundary
  fun isNumber() = true

  @ExportMessage
  fun fitsInBigInteger() = true

  @ExportMessage
  fun asBigInteger(): BigInteger = value

  fun isNatural() = value >= BigInteger.ZERO

  @TruffleBoundary
  override fun compareTo(other: BigInt) = value.compareTo(other.value)

  @TruffleBoundary
  override fun toString() = value.toString()

  override fun hashCode() = value.hashCode()

  @TruffleBoundary
  override fun equals(other: Any?) = other is BigInt && value == other.value

  @ExportMessage
  @TruffleBoundary
  fun fitsInByte() = value.bitLength() < 8

  @ExportMessage
  @TruffleBoundary
  fun fitsInShort() = value.bitLength() < 16

  @ExportMessage
  @TruffleBoundary
  fun fitsInInt() = value.bitLength() < 32

  @ExportMessage
  @TruffleBoundary
  fun fitsInLong() = value.bitLength() < 64

  @ExportMessage
  @TruffleBoundary
  fun fitsInFloat() = fitsInBinaryFloat(24, java.lang.Float.MAX_EXPONENT)

  @ExportMessage
  @TruffleBoundary
  fun fitsInDouble() = fitsInBinaryFloat(53, java.lang.Double.MAX_EXPONENT)

  private fun fitsInBinaryFloat(precision: Int, maxExponent: Int): Boolean {
    val magnitude = value.abs()
    val bits = magnitude.bitLength()
    // Trailing zeroes consume exponent range, not significand precision. Check
    // the magnitude because BigInteger.bitLength uses two's complement for negatives.
    return bits <= maxExponent + 1 &&
      (bits == 0 || bits - magnitude.lowestSetBit <= precision)
  }

  @ExportMessage
  @TruffleBoundary
  @Throws(UnsupportedMessageException::class)
  fun asByte() = if (fitsInByte()) value.toByte() else throw UnsupportedMessageException.create()

  @ExportMessage
  @TruffleBoundary
  @Throws(UnsupportedMessageException::class)
  fun asShort() = if (fitsInShort()) value.toShort() else throw UnsupportedMessageException.create()

  @ExportMessage
  @TruffleBoundary
  @Throws(UnsupportedMessageException::class)
  fun asInt() = if (fitsInInt()) value.toInt() else throw UnsupportedMessageException.create()

  @ExportMessage
  @TruffleBoundary
  @Throws(UnsupportedMessageException::class)
  fun asLong() = if (fitsInLong()) value.toLong() else throw UnsupportedMessageException.create()

  @ExportMessage
  @TruffleBoundary
  @Throws(UnsupportedMessageException::class)
  fun asFloat() = if (fitsInFloat()) value.toFloat() else throw UnsupportedMessageException.create()

  @ExportMessage
  @TruffleBoundary
  @Throws(UnsupportedMessageException::class)
  fun asDouble() = if (fitsInDouble()) value.toDouble() else throw UnsupportedMessageException.create()
}
