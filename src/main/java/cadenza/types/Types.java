package cadenza.types;

import cadenza.values.Closure;
import cadenza.values.Int;
import cadenza.values.NeutralValue;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.ImplicitCast;
import com.oracle.truffle.api.dsl.TypeCast;
import com.oracle.truffle.api.dsl.TypeCheck;
import com.oracle.truffle.api.dsl.TypeSystem;

// Interesting runtime types
@TypeSystem({
  Closure.class,
  boolean.class,
  int.class,
  Int.class,
  NeutralValue.class, // probably unused, but we'll see
  Void.class // with manual typecheck of looking for null?
})
public abstract class Types {
  @ImplicitCast
  @CompilerDirectives.TruffleBoundary
  public static Int castBigNumber(int value) { return new Int(value); }

  @TypeCheck(Void.class)
  public static boolean isVoid(Object value) { return value == null; }

  @TypeCast(Void.class)
  public static Void asVoid(Object value) {
    assert value == null;
    return null;
  }
}