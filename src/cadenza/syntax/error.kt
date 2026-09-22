package cadenza.syntax

import com.oracle.truffle.api.exception.AbstractTruffleException
import com.oracle.truffle.api.interop.ExceptionType
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
class SyntaxError(val failure: Failure) : AbstractTruffleException(failure.diagnostic) {
  override fun toString(): String = failure.toString()
  @ExportMessage fun getExceptionType(): ExceptionType = ExceptionType.PARSE_ERROR
  @ExportMessage fun hasSourceLocation(): Boolean = true
  @ExportMessage fun getSourceLocation() = failure.sourceSection
}
