package coda

import com.oracle.truffle.api.dsl.{ ImplicitCast, TypeSystem }
import java.math.BigInteger

@TypeSystem(
  Array(
    classOf[Boolean],
    classOf[Float],
    classOf[Double],
    classOf[Int],
    classOf[Long],
    classOf[Short],
    classOf[Byte],
    classOf[Char],
    classOf[BigInteger],
    classOf[String]
  )
)
class CoreTypes {}
object CoreTypes {
  // used so we can have an unboxed long-based fast path for "small" big integer computations
  @ImplicitCast
  def asBigInteger(value: Long): BigInteger = BigInteger.valueOf(value)
}
