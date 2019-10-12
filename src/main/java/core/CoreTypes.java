package core;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import core.values.*;
import java.math.BigInteger;
import java.util.function.Consumer;

@TypeSystem({boolean.class,long.class, Closure.class, BigNumber.class})
public abstract class CoreTypes {
  @ImplicitCast
  @TruffleBoundary
  public static BigNumber castBig(long value) {
    return new BigNumber(BigInteger.valueOf(value));
  }
}
