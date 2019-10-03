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
public final class CoreBigInteger implements TruffleObject, Comparable<CoreBigInteger> {
  public CoreBigInteger(BigInteger i) { this.value = i; }
  private final BigInteger value;
  public BigInteger getValue() { return value; }

  @TruffleBoundary
  public int compareTo(CoreBigInteger o) {
    return value.compareTo(o.getValue());
  }

  @Override
  @TruffleBoundary
  public String toString() { return value.toString(); }

  @Override
  public int hashCode() { return value.hashCode(); }

  @Override
  @TruffleBoundary
  public boolean equals(Object obj) {
    if (obj instanceof CoreBigInteger) return value.equals(((CoreBigInteger)obj).value);  
    return false;
  }
 
  @ExportMessage
  @TruffleBoundary
  boolean fitsInByte() { return value.bitLength() < 8; }

  @ExportMessage
  @TruffleBoundary
  boolean fitsInShort() { return value.bitLength() < 16; }

  @ExportMessage
  @TruffleBoundary
  boolean fitsInInt() { return value.bitLength() < 32; }
  
  @ExportMessage
  @TruffleBoundary
  boolean fitsInLong() { return value.bitLength() < 64; }

  @ExportMessage
  @TruffleBoundary
  boolean fitsInFloat() {
    return fitsInInt() && inSafeFloatRange(value.intValue());
  }

  @ExportMessage
  @TruffleBoundary
  boolean fitsInDouble() {
    return fitsInLong() && inSafeDoubleRange(value.longValue());
  }

  @ExportMessage
  @TruffleBoundary
  byte asByte() throws UnsupportedMessageException {
    if (fitsInByte()) return value.byteValue();
    throw UnsupportedMessageException.create();
  }

  @ExportMessage
  @TruffleBoundary
  short asShort() throws UnsupportedMessageException { 
    if (fitsInShort()) return value.shortValue();
    throw UnsupportedMessageException.create();
  }

  @ExportMessage
  @TruffleBoundary
  int asInt() throws UnsupportedMessageException { 
    if (fitsInInt()) return value.intValue();
    throw UnsupportedMessageException.create();
  }

  @ExportMessage
  @TruffleBoundary
  long asLong() throws UnsupportedMessageException { 
    if (fitsInLong()) return value.longValue();
    throw UnsupportedMessageException.create();
  }

  @ExportMessage
  @TruffleBoundary
  float asFloat() throws UnsupportedMessageException {
    if (fitsInFloat()) return value.floatValue();
    throw UnsupportedMessageException.create();
  }

  @ExportMessage
  @TruffleBoundary
  double asDouble() throws UnsupportedMessageException {
    if (fitsInDouble()) return value.doubleValue();
    throw UnsupportedMessageException.create();
  }

  @ExportMessage
  @TruffleBoundary
  boolean isNumber() { return fitsInLong(); } // this is small enough to marshal

  private static final long LONG_MAX_SAFE_DOUBLE = 9007199254740991L; // 2 ** 53 - 1
  private static final int INT_MAX_SAFE_FLOAT = 16777215; // 2 ** 24 - 1

  private static boolean inSafeDoubleRange(long l) {
    return l >= -LONG_MAX_SAFE_DOUBLE && l <= LONG_MAX_SAFE_DOUBLE;
  }

  private static boolean inSafeFloatRange(int i) {
    return i >= -INT_MAX_SAFE_FLOAT && i <= INT_MAX_SAFE_FLOAT;
  }
}
