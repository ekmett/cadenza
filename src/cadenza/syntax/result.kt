package cadenza.syntax

import com.oracle.truffle.api.source.Source
import com.oracle.truffle.api.source.SourceSection
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
  val line: Int get() = if (source.length == 0) 1 else source.getLineNumber(pos)
  val col: Int get() = if (source.length == 0) 1 else source.getColumnNumber(pos)
  val loc : String get() = "${source.name}:$line:$col"
  val diagnostic: String get() {
    val details = buildList {
      message?.let { add(it) }
      if (expected.isNotEmpty()) add("expected ${expected.distinct().joinToString(" or ")}")
    }.joinToString(", ").ifEmpty { "unexpected input" }
    return "$loc: $details"
  }
  override fun toString(): String = Pretty.ppString {
    if (source.length == 0) text(diagnostic)
    else error(Severity.error, source, pos, message, *expected.toTypedArray())
  }
  val sourceSection: SourceSection? get() = source.createSection(pos,0)
}
