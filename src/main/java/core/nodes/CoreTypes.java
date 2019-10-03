package core.nodes;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.ImplicitCast;
import com.oracle.truffle.api.dsl.TypeSystem;
import core.values.CoreBigInteger;
import java.math.BigInteger;

@TypeSystem({
  boolean.class,
  int.class,
  long.class
})
public abstract class CoreTypes {
  @ImplicitCast
  @TruffleBoundary
  public static CoreBigInteger castBigInteger(long value) {
    return new CoreBigInteger(BigInteger.valueOf(value));
  }
}
