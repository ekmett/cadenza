package cadenza

import cadenza.nodes.Builtin
import cadenza.types.Type
import cadenza.values.NeutralValue
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.nodes.SlowPathException

@Throws(NeutralException::class)
fun neutral(type: Type, term: Neutral) : Nothing {
  throw NeutralException(type,term)
}

@CompilerDirectives.ValueType
abstract class Neutral {
  open fun apply(arguments: Array<Any?>): NApp {
    return NApp(this, arguments)
  }

  data class NIf(val body: Neutral, val thenValue: Any?, val elseValue: Any?) : Neutral()

  data class NCallBuiltin(val builtin: Builtin, val arg: Neutral) : Neutral()

  data class NApp(val rator: Neutral, val rands: Array<Any?>) : Neutral() {
    override fun apply(arguments: Array<Any?>): NApp {
      return NApp(rator, arrayOf(*rands, *arguments))
    }
  }
}

class NeutralException(val type: Type, val term: Neutral) : SlowPathException() {
  fun get(): NeutralValue {
    return NeutralValue(type, term)
  }

  fun apply(rands: Array<Any?>): Nothing {
    val len = rands.size
    var currentType = type
    for (i in 0 until len) currentType = (currentType as Type.Arr).result
    throw NeutralException(currentType, term.apply(rands))
  }

  companion object {
    internal const val serialVersionUID : Long = 5587798688299594259L
  }
}