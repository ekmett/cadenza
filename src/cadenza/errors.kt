package cadenza

import cadenza.semantics.TypeError
import com.oracle.truffle.api.exception.AbstractTruffleException
import com.oracle.truffle.api.interop.ExceptionType
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.source.SourceSection

/** A guest evaluation failure, distinguishable from a bug in the interpreter. */
class RuntimeError(message: String) : AbstractTruffleException(message)

/** Static checking fails during parsing and retains the source being checked. */
@ExportLibrary(InteropLibrary::class)
class TypeCheckError(error: TypeError, private val section: SourceSection) : AbstractTruffleException(error.toString()) {
  @ExportMessage fun getExceptionType(): ExceptionType = ExceptionType.PARSE_ERROR
  @ExportMessage fun hasSourceLocation(): Boolean = true
  @ExportMessage fun getSourceLocation(): SourceSection = section
}
