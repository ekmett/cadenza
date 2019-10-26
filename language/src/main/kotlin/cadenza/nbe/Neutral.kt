package cadenza.nbe

import cadenza.nodes.Builtin
import com.oracle.truffle.api.CompilerDirectives

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
