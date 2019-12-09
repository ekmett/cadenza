package cadenza.data

import cadenza.jit.Builtin
import cadenza.semantics.Type
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.interop.ArityException
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

sealed class Neutral {
  open fun apply(args: Array<out Any?>) = NApp(this, args)

  data class NIf(val body: Neutral, val thenValue: Any?, val elseValue: Any?) : Neutral()

  data class NCallBuiltin(val builtin: Builtin, val args: Array<Any?>) : Neutral()

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
class NeutralValue(val type: Type, val term : Neutral) : TruffleObject {
  @ExportMessage
  fun isExecutable(): Boolean = true

  @ExportMessage
  @Throws(ArityException::class)
  fun execute(vararg arguments: Any?) = NeutralValue(
    arguments.indices.fold(type) { resultType, i ->
      (resultType as Type.Arr? ?: throw ArityException.create(i, arguments.size)).result
    },
    term.apply(arguments)
  )

  // assumes this has been built legally. fails via unchecked null pointer exception
  @Suppress("unused")
  fun apply(arguments: Array<out Any?>) = NeutralValue(
    arguments.indices.fold(type) { resultType, _ -> (resultType as Type.Arr).result },
    term.apply(arguments)
  )
}
