package core;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import core.values.*;
import java.math.BigInteger;
import java.util.function.Consumer;

@TypeSystem({boolean.class,long.class, Closure.class, BigNumber.class, Consumer.class})
public abstract class Types {
  @ImplicitCast
  @TruffleBoundary
  public static BigNumber castBig(long value) {
    return new BigNumber(BigInteger.valueOf(value));
  }
}
