package cadenza.nbe

import cadenza.nodes.Builtin
import com.oracle.truffle.api.CompilerDirectives

@CompilerDirectives.ValueType // screw your reference equality
abstract class Neutral {
  open fun apply(arguments: Array<Any?>): NApp {
    return NApp(this, arguments)
  }

  class NIf(val body: Neutral, val thenValue: Any?, val elseValue: Any?) : Neutral()
  class NCallBuiltin(val builtin: Builtin, val arg: Neutral) : Neutral()
  class NApp(val rator: Neutral, val rands: Array<Any?>) : Neutral() {
    override fun apply(arguments: Array<Any?>): NApp {
      return NApp(rator, arrayOf(*rands, *arguments))
    }
  }
}
