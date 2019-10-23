package cadenza.types

import cadenza.values.Closure
import cadenza.values.BigInt
import cadenza.values.NeutralValue
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.dsl.ImplicitCast
import com.oracle.truffle.api.dsl.TypeCast
import com.oracle.truffle.api.dsl.TypeCheck
import com.oracle.truffle.api.dsl.TypeSystem

// Interesting runtime types
@TypeSystem(Closure::class, Boolean::class, Int::class, BigInt::class, NeutralValue::class, Unit::class)
open class Types {
  companion object {
    @ImplicitCast
    @CompilerDirectives.TruffleBoundary
    fun castBigInt(value: Int): BigInt {
      return BigInt(value.toLong())
    }

    @TypeCheck(Unit::class)
    fun isUnit(value: Any?): Boolean {
      return value == Unit
    }

    @TypeCast(Unit::class)
    fun asUnit(value: Any?): Unit {
      assert(isUnit(value))
      return
    }
  }
}
