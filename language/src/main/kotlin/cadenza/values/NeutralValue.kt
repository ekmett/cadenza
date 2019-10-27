package cadenza.values

import cadenza.types.*
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.interop.*
import com.oracle.truffle.api.library.*

@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary::class)
class NeutralValue(val type: Type, val term : Neutral) : TruffleObject {
  @ExportMessage
  fun isExecutable(): Boolean = true

  @ExportMessage
  @Throws(ArityException::class)
  fun execute(vararg arguments: Any?): NeutralValue {
    var resultType = type
    for (i in arguments.indices) {
      if (resultType !is Arr) throw ArityException.create(i, arguments.size)
      resultType = resultType.result
    }
    return NeutralValue(resultType, term.apply(arguments))
  }

  // assumes this has been built legally. fails via unchecked null pointer exception
  @Suppress("unused")
  fun apply(arguments: Array<out Any?>): NeutralValue {
    var resultType = type
    for (i in arguments.indices)
      resultType = (resultType as Arr).result
    return NeutralValue(resultType, term.apply(arguments))
  }
}