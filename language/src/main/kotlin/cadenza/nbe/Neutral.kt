package cadenza.nbe

import cadenza.nodes.Builtin
import com.oracle.truffle.api.CompilerDirectives

// neutral terms allow for normalization-by-evaluation

// neutral terms are values that are are 'stuck' carried by normalization by evaluation
// eventually these will carry types to help guide the walk, return arity, etc.

// for now these are strict, but if large neutral terms become a problem we _could_ render them lazy
// by materializing frames at the call sites and putting thunks that use materialized frames here instead
// for lazier alpha equivalence checking
@CompilerDirectives.ValueType // screw your reference equality
abstract class Neutral {
  // other people can execute this, but it just builds a bigger and bigger NApp
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
