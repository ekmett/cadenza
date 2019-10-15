package cadenza;

import cadenza.values.Closure;
import cadenza.values.Int;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.ImplicitCast;
import com.oracle.truffle.api.dsl.TypeSystem;

@TypeSystem({
  Closure.class,
  boolean.class,
  int.class,
  Int.class
})
public abstract class Types {
  @ImplicitCast
  @CompilerDirectives.TruffleBoundary
  public static Int castBigNumber(int value) { return new Int(value); }
}
