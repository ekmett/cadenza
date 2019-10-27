package cadenza.types

import cadenza.nodes.Builtin
import cadenza.values.NeutralValue
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.nodes.SlowPathException

@Throws(NeutralException::class)
@Suppress("NOTHING_TO_INLINE")
inline fun neutral(type: Type, term: Neutral) : Nothing {
  throw NeutralException(type, term)
}

@CompilerDirectives.ValueType
abstract class Neutral {
  open fun apply(args: Array<Any?>) = NApp(this, args)
}

data class NIf(val body: Neutral, val thenValue: Any?, val elseValue: Any?) : Neutral()

data class NCallBuiltin(val builtin: Builtin, val arg: Neutral) : Neutral()

data class NApp(val rator: Neutral, val rands: Array<Any?>) : Neutral() {
  override fun apply(args: Array<Any?>) = NApp(rator, arrayOf(*rands, *args))
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as NApp
    return rator == other.rator && rands.contentEquals(other.rands)
  }

  override fun hashCode(): Int {
    var result = rator.hashCode()
    result = 31 * result + rands.contentHashCode()
    return result
  }
}

class NeutralException(val type: Type, val term: Neutral) : SlowPathException() {
  @Suppress("NOTHING_TO_INLINE")
  inline fun get() = NeutralValue(type, term)

  fun apply(rands: Array<Any?>): Nothing {
    val len = rands.size
    var currentType = type
    for (i in 0 until len) currentType = (currentType as Type.Arr).result
    neutral(currentType, term.apply(rands))
  }

  companion object {
    const val serialVersionUID : Long = 5587798688299594259L
  }
}