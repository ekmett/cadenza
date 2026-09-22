package cadenza

import cadenza.semantics.TypeError
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.exception.AbstractTruffleException
import com.oracle.truffle.api.interop.ExceptionType
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.source.SourceSection
import com.oracle.truffle.api.nodes.Node

/** A guest evaluation failure, distinguishable from a bug in the interpreter. */
class RuntimeError @JvmOverloads constructor(
  message: String,
  location: Node? = null,
  private var section: SourceSection? = location?.encapsulatingSourceSection
) : AbstractTruffleException(message, location) {

  /** A caller supplies a location only when the failing operation had none of its own. */
  @TruffleBoundary
  fun at(sourceSection: SourceSection?): RuntimeError {
    if (section == null && sourceSection?.isAvailable == true) section = sourceSection
    return this
  }

  override fun getEncapsulatingSourceSection(): SourceSection? = section
}

/** Static checking fails during parsing and retains the source being checked. */
@ExportLibrary(InteropLibrary::class)
class TypeCheckError(error: TypeError, private val section: SourceSection) : AbstractTruffleException(error.toString()) {
  @ExportMessage fun getExceptionType(): ExceptionType = ExceptionType.PARSE_ERROR
  @ExportMessage fun hasSourceLocation(): Boolean = true
  @ExportMessage fun getSourceLocation(): SourceSection = section
}
