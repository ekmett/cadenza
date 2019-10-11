package core;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.ImplicitCast;
import com.oracle.truffle.api.dsl.TypeSystem;
import core.values.BigNumber;

import java.math.BigInteger;

@TypeSystem({boolean.class,long.class})
public abstract class Types {
  @ImplicitCast
  @TruffleBoundary
  public static BigNumber castBigInteger(long value) {
    return new BigNumber(BigInteger.valueOf(value));
  }
}
