package stg.nodes;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.ImplicitCast;
//import com.oracle.truffle.api.dsl.TypeCast;
//import com.oracle.truffle.api.dsl.TypeCheck;
import com.oracle.truffle.api.dsl.TypeSystem;
import java.math.BigInteger;

@TypeSystem(
  { boolean.class
  , int.class
  , long.class
  , BigInteger.class
//  , byte.class
//  , short.class
//  , char.class
//  , double.class
//  , float.class
//  , byte[].class
  }
)
public abstract class StgTypes {
  @ImplicitCast
  @TruffleBoundary
  public static BigInteger castBigInteger(long value) {
    return BigInteger.valueOf(value);
  }
}