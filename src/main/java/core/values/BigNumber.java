package core.values;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import java.math.BigInteger;

@ValueType
@ExportLibrary(InteropLibrary.class)
public final class BigNumber implements TruffleObject, Comparable<BigNumber> {
  public BigNumber(BigInteger i) { this.value = i; }
  public BigNumber(long value) { this(BigInteger.valueOf(value)); }

  public final BigInteger value;

  @TruffleBoundary
  public int compareTo(BigNumber o) {
    return value.compareTo(o.value);
  }

  @Override
  @TruffleBoundary
  public String toString() { return value.toString(); }

  @Override
  public int hashCode() { return value.hashCode(); }

  @Override
  @TruffleBoundary
  public boolean equals(Object obj) {
    if (obj instanceof BigNumber) return value.equals(((BigNumber)obj).value);
    return false;
  }
 
  @ExportMessage
  @TruffleBoundary
  public boolean fitsInByte() { return value.bitLength() < 8; }

  @ExportMessage
  @TruffleBoundary
  public boolean fitsInShort() { return value.bitLength() < 16; }

  @ExportMessage
  @TruffleBoundary
  public boolean fitsInInt() { return value.bitLength() < 32; }
  
  @ExportMessage
  @TruffleBoundary
  public boolean fitsInLong() { return value.bitLength() < 64; }

  @ExportMessage
  @TruffleBoundary
  public boolean fitsInFloat() {
    return fitsInInt() && inSafeFloatRange(value.intValue());
  }

  @ExportMessage
  @TruffleBoundary
  public boolean fitsInDouble() {
    return fitsInLong() && inSafeDoubleRange(value.longValue());
  }

  @ExportMessage
  @TruffleBoundary
  public byte asByte() throws UnsupportedMessageException {
    if (fitsInByte()) return value.byteValue();
    throw UnsupportedMessageException.create();
  }

  @ExportMessage
  @TruffleBoundary
  public short asShort() throws UnsupportedMessageException {
    if (fitsInShort()) return value.shortValue();
    throw UnsupportedMessageException.create();
  }

  @ExportMessage
  @TruffleBoundary
  public int asInt() throws UnsupportedMessageException {
    if (fitsInInt()) return value.intValue();
    throw UnsupportedMessageException.create();
  }

  @ExportMessage
  @TruffleBoundary
  public long asLong() throws UnsupportedMessageException {
    if (fitsInLong()) return value.longValue();
    throw UnsupportedMessageException.create();
  }

  @ExportMessage
  @TruffleBoundary
  public float asFloat() throws UnsupportedMessageException {
    if (fitsInFloat()) return value.floatValue();
    throw UnsupportedMessageException.create();
  }

  @ExportMessage
  @TruffleBoundary
  public double asDouble() throws UnsupportedMessageException {
    if (fitsInDouble()) return value.doubleValue();
    throw UnsupportedMessageException.create();
  }

  @ExportMessage
  @TruffleBoundary
  public boolean isNumber() { return fitsInLong(); } // this is small enough to marshal

  private static final long LONG_MAX_SAFE_DOUBLE = 9007199254740991L; // 2 ** 53 - 1
  private static final int INT_MAX_SAFE_FLOAT = 16777215; // 2 ** 24 - 1

  private static boolean inSafeDoubleRange(long l) {
    return l >= -LONG_MAX_SAFE_DOUBLE && l <= LONG_MAX_SAFE_DOUBLE;
  }

  private static boolean inSafeFloatRange(int i) {
    return i >= -INT_MAX_SAFE_FLOAT && i <= INT_MAX_SAFE_FLOAT;
  }
}
