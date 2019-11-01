package cadenza.data

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.CompilerDirectives.ValueType
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnsupportedMessageException
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import java.math.BigInteger

private const val LONG_MAX_SAFE_DOUBLE = 9007199254740991L // 2 ** 53 - 1
private const val INT_MAX_SAFE_FLOAT = 16777215 // 2 ** 24 - 1

private fun inSafeDoubleRange(l: Long): Boolean =
  l >= -LONG_MAX_SAFE_DOUBLE && l <= LONG_MAX_SAFE_DOUBLE

private fun inSafeFloatRange(i: Int): Boolean =
  i >= -INT_MAX_SAFE_FLOAT && i <= INT_MAX_SAFE_FLOAT

@ValueType
@ExportLibrary(InteropLibrary::class)
class BigInt(val value: BigInteger) : TruffleObject, Comparable<BigInt> {
  constructor(value: Long) : this(BigInteger.valueOf(value))

  @ExportMessage
  @TruffleBoundary
  fun isNumber() = fitsInLong()

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
  fun fitsInFloat() = fitsInInt() && inSafeFloatRange(value.toInt())

  @ExportMessage
  @TruffleBoundary
  fun fitsInDouble() = fitsInLong() && inSafeDoubleRange(value.toLong())

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
