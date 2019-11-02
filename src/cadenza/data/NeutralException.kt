package cadenza.data

import cadenza.semantics.Type
import com.oracle.truffle.api.nodes.SlowPathException

class NeutralException(val type: Type, val term: Neutral) : SlowPathException() {
  fun get() = NeutralValue(type, term)

  fun apply(rands: Array<out Any?>): Nothing = neutral(
    rands.indices.fold(type) { currentType, _ -> (currentType as Type.Arr).result },
    term.apply(rands)
  )

  companion object { const val serialVersionUID : Long = 1L }
}

@Throws(NeutralException::class)
fun neutral(type: Type, term: Neutral) : Nothing = throw NeutralException(type, term)

fun NeutralValue.raise() : Nothing = throw NeutralException(type, term)

fun <T> throwIfNeutralValue(x: T): T = if (x !is NeutralValue) x else x.raise()