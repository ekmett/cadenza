package core;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import core.values.*;

@TypeSystem({
  Closure.class,
  boolean.class,
  int.class,
  BigNumber.class
})
public abstract class Types {
  @ImplicitCast
  @TruffleBoundary
  public static BigNumber castBigNumber(int value) { return new BigNumber(value); }
}
