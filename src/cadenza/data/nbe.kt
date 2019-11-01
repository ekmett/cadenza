package cadenza.data

import cadenza.jit.Builtin
import cadenza.Type
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.interop.ArityException
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.nodes.SlowPathException

class NeutralException(val type: Type, val term: Neutral) : SlowPathException() {
  @Suppress("NOTHING_TO_INLINE")
  inline fun get() = NeutralValue(type, term)

  fun apply(rands: Array<out Any?>): Nothing {
    val len = rands.size
    var currentType = type
    for (i in 0 until len) currentType = (currentType as Type.Arr).result
    neutral(currentType, term.apply(rands))
  }

  companion object {
    const val serialVersionUID : Long = 5587798688299594259L
  }
}

@Throws(NeutralException::class)
@Suppress("NOTHING_TO_INLINE")
inline fun neutral(type: Type, term: Neutral) : Nothing {
  throw NeutralException(type, term)
}

@CompilerDirectives.ValueType
abstract class Neutral {
  open fun apply(args: Array<out Any?>) = NApp(this, args)

  data class NIf(val body: Neutral, val thenValue: Any?, val elseValue: Any?) : Neutral()

  data class NCallBuiltin(val builtin: Builtin, val arg: Neutral) : Neutral()

  data class NApp(val rator: Neutral, val rands: Array<out Any?>) : Neutral() {
    override fun apply(args: Array<out Any?>) = NApp(rator, arrayOf(*rands, *args))
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

}

// should only be seen under executeAny. execute should _never_ see one of these.
@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary::class)
class NeutralValue(val type: Type, private val term : Neutral) : TruffleObject {
  @ExportMessage
  fun isExecutable(): Boolean = true

  @ExportMessage
  @Throws(ArityException::class)
  fun execute(vararg arguments: Any?): NeutralValue {
    var resultType = type
    for (i in arguments.indices) {
      if (resultType !is Type.Arr) throw ArityException.create(i, arguments.size)
      resultType = resultType.result
    }
    return NeutralValue(resultType, term.apply(arguments))
  }

  // assumes this has been built legally. fails via unchecked null pointer exception
  @Suppress("unused")
  fun apply(arguments: Array<out Any?>): NeutralValue {
    var resultType = type
    for (i in arguments.indices)
      resultType = (resultType as Type.Arr).result
    return NeutralValue(resultType, term.apply(arguments))
  }

  fun raise() : Nothing = throw NeutralException(type, term)
}

fun <T> throwIfNeutralValue(x: T): T = if (x !is NeutralValue) x else x.raise()
