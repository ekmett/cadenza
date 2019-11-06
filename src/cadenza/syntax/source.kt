package cadenza.syntax

import com.oracle.truffle.api.source.Source
import org.intelligence.parser.Parse

class SourceParse(val source: Source) : Parse(source.characters)

typealias SourceParser<T> = SourceParse.() -> T

fun <T> Source.parse(parser: SourceParser<T>) : Result<T> =
  SourceParse(this).let {
    try {
      Success(parser(it))
    } catch (e: Parse.Error) {
      Failure(this, it.pos, e.message, it.expected)
    }
  }

val SourceParse.name: String get() = source.name
val SourceParse.path: String get() = source.path
val SourceParse.line: Int get() = source.getLineNumber(pos)
val SourceParse.col: Int get() = source.getColumnNumber(pos)