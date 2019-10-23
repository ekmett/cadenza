package cadenza.nbe

import cadenza.types.Type
import cadenza.values.NeutralValue
import com.oracle.truffle.api.nodes.SlowPathException

// this is used to suck the program through a straw using normalization-by-evaluation
// to produce a beta-eta-long normal form versions of the resulting program that remains

class NeutralException(val type: Type, val term: Neutral) : SlowPathException() {
  fun get(): NeutralValue {
    return NeutralValue(type, term)
  }

  // TODO: throw NeutralException and return Nothing
  fun apply(rands: Array<Any?>): NeutralException {
    val len = rands.size
    var currentType = type
    for (i in 0 until len) currentType = (currentType as Type.Arr).result
    return NeutralException(currentType, term.apply(rands))
  }

  companion object {
    private val serialVersionUID = 5587798688299594259L
  }
}
