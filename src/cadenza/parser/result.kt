package cadenza.parser

import com.oracle.truffle.api.source.Source
import org.intelligence.diagnostics.Severity
import org.intelligence.diagnostics.error
import org.intelligence.pretty.Pretty

sealed class Result<out T>
data class Success<T>(val value: T): Result<T>()
data class Failure(
  val source: Source,
  val pos: Int,
  val message: String? = null,
  val expected: List<Any> = emptyList()
): Result<Nothing>() {
  val line: Int get() = source.getLineNumber(pos)
  val col: Int get() = source.getColumnNumber(pos)
  val loc : String get() = "${source.name}:$line:$col"
  override fun toString(): String = Pretty.ppString {
    error(Severity.error, source, pos, message, *expected.toTypedArray())
  }
}