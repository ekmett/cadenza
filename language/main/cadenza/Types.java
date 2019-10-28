package cadenza;

import cadenza.Closure;
import cadenza.BigInt;
import cadenza.NeutralValue;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.ImplicitCast;
import com.oracle.truffle.api.dsl.TypeCast;
import com.oracle.truffle.api.dsl.TypeCheck;
import com.oracle.truffle.api.dsl.TypeSystem;
import kotlin.Unit;

// Interesting runtime types
@TypeSystem({
  Closure.class,
  boolean.class,
  int.class,
  BigInt.class,
  NeutralValue.class, // probably unused, but we'll see
  Unit.class // with manual typecheck of looking for null?
})
public abstract class Types {
  @ImplicitCast
  @CompilerDirectives.TruffleBoundary
  public static BigInt castBigNumber(int value) { return new BigInt(value); }

  @TypeCheck(Unit.class)
  public static boolean isUnit(Object value) { return value == Unit.INSTANCE; }

  @SuppressWarnings("SameReturnValue")
  @TypeCast(Unit.class)
  public static Unit asUnit(Object value) {
    assert value == Unit.INSTANCE;
    return Unit.INSTANCE;
  }
}
