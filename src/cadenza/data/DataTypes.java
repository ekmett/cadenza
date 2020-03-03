package cadenza.data;

import cadenza.jit.Indirection;
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
  Indirection.class,
  NeutralValue.class,
  Unit.class
})
public abstract class DataTypes {
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