package cadenza.values

import cadenza.types.Neutral
import cadenza.types.Type
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.interop.ArityException
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary::class)
class NeutralValue(val type: Type, val term: Neutral) : TruffleObject {
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
    @Suppress("UNCHECKED_CAST")
    return NeutralValue(resultType, term.apply(arguments as Array<Any?>))
  }

  // assumes this has been built legally. fails via unchecked null pointer exception
  fun apply(arguments: Array<out Any?>): NeutralValue {
    var resultType = type
    for (i in arguments.indices)
      resultType = (resultType as Type.Arr).result
    return NeutralValue(resultType, term.apply(arguments))
  }
}