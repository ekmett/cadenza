package cadenza.values

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

private fun inSafeDoubleRange(l: Long): Boolean {
  return l >= -LONG_MAX_SAFE_DOUBLE && l <= LONG_MAX_SAFE_DOUBLE
}

private fun inSafeFloatRange(i: Int): Boolean {
  return i >= -INT_MAX_SAFE_FLOAT && i <= INT_MAX_SAFE_FLOAT
}

@ValueType
@ExportLibrary(InteropLibrary::class)
class BigInt(val value: BigInteger) : TruffleObject, Comparable<BigInt> {
  constructor(value: Long) : this(BigInteger.valueOf(value))

  @ExportMessage
  @TruffleBoundary
  fun isNumber(): Boolean = fitsInLong()

  fun isNatural(): Boolean = value >= BigInteger.ZERO

  @TruffleBoundary
  override fun compareTo(other: BigInt): Int {
    return value.compareTo(other.value)
  }

  @TruffleBoundary
  override fun toString(): String {
    return value.toString()
  }

  override fun hashCode(): Int {
    return value.hashCode()
  }

  @TruffleBoundary
  override fun equals(other: Any?): Boolean {
    return other is BigInt && value == other.value
  }

  @ExportMessage
  @TruffleBoundary
  fun fitsInByte(): Boolean {
    return value.bitLength() < 8
  }

  @ExportMessage
  @TruffleBoundary
  fun fitsInShort(): Boolean {
    return value.bitLength() < 16
  }

  @ExportMessage
  @TruffleBoundary
  fun fitsInInt(): Boolean {
    return value.bitLength() < 32
  }

  @ExportMessage
  @TruffleBoundary
  fun fitsInLong(): Boolean {
    return value.bitLength() < 64
  }

  @ExportMessage
  @TruffleBoundary
  fun fitsInFloat(): Boolean {
    return fitsInInt() && inSafeFloatRange(value.toInt())
  }

  @ExportMessage
  @TruffleBoundary
  fun fitsInDouble(): Boolean {
    return fitsInLong() && inSafeDoubleRange(value.toLong())
  }

  @ExportMessage
  @TruffleBoundary
  @Throws(UnsupportedMessageException::class)
  fun asByte(): Byte {
    if (fitsInByte()) return value.toByte()
    throw UnsupportedMessageException.create()
  }

  @ExportMessage
  @TruffleBoundary
  @Throws(UnsupportedMessageException::class)
  fun asShort(): Short {
    if (fitsInShort()) return value.toShort()
    throw UnsupportedMessageException.create()
  }

  @ExportMessage
  @TruffleBoundary
  @Throws(UnsupportedMessageException::class)
  fun asInt(): Int {
    if (fitsInInt()) return value.toInt()
    throw UnsupportedMessageException.create()
  }

  @ExportMessage
  @TruffleBoundary
  @Throws(UnsupportedMessageException::class)
  fun asLong(): Long {
    if (fitsInLong()) return value.toLong()
    throw UnsupportedMessageException.create()
  }

  @ExportMessage
  @TruffleBoundary
  @Throws(UnsupportedMessageException::class)
  fun asFloat(): Float {
    if (fitsInFloat()) return value.toFloat()
    throw UnsupportedMessageException.create()
  }

  @ExportMessage
  @TruffleBoundary
  @Throws(UnsupportedMessageException::class)
  fun asDouble(): Double {
    if (fitsInDouble()) return value.toDouble()
    throw UnsupportedMessageException.create()
  }
}
