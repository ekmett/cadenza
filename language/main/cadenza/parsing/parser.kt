package cadenza.parsing

import com.oracle.truffle.api.source.Source
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.lang.StringBuilder

// generate an oxford comma separated list, TODO: de-dupe by sending it to a set first?
fun List<Any>.oxford(): String = StringBuilder().also { this.oxford(it) }.toString()
fun List<Any>.oxford(it: StringBuilder) {
  val n = this.size
  when (n) {
    0 -> {}
    1 -> it.append(this[0])
    2 -> {
      it.append(this[0])
      it.append(" or ")
      it.append(this[1])
    }
    else -> {
      for (i in 0 until n - 1) {
        it.append(this[i])
        it.append(", ")
      }
      it.append("or ")
      it.append(this[n - 1])
    }
  }
}

open class ParseError(var pos: Int, message: String? = null) : Exception(message) {
  constructor(pos: Int, message: String? = null, cause: Throwable): this(pos,message) {
    initCause(cause)
  }
  override fun fillInStackTrace() = this // don't record
  companion object { const val serialVersionUID : Long = 1L }
  override fun toString(): String = message ?: super.toString()
}

data class Expected(val what: Any, val next: Expected?)

fun Expected?.toList() : List<Any> {
  val out = mutableListOf<Any>()
  var current = this;
  while (current != null) {
    out.add(current.what)
    current = current.next
  }
  return out
}

open class ParseState(val characters: CharSequence) {
  var expected: Expected? = null // var is intended, we swap it out regularly
  var pos: Int = 0
    set(value) {
      if (field != value) {
        field = value
        expected = null
      }
    }
}

// parsers consume parsestate as this and throw ParseErrors on failure
typealias Parser<T> = ParseState.() -> T

// mark/release support
inline class Mark(val pos: Int)
val ParseState.mark: Mark get() = Mark(pos)
fun ParseState.release(mark: Mark) { pos = mark.pos }

@Throws(ParseError::class)
fun ParseState.fail(message: String? = null): Nothing = throw ParseError(pos, message)

@Throws(ParseError::class)
fun ParseState.expected(what: Any): Nothing {
  expected = Expected(what, expected)
  throw ParseError(pos)
}

val ParseState.eof: Unit
  @Throws(ParseError::class)
  get() {
    if (!atEof) expected("EOF")
  }

val ParseState.atEof: Boolean get() = pos == characters.length

@Throws(ParseError::class)
fun ParseState.char(c: Char): Char {
  if (pos >= characters.length || c != characters[pos]) expected(c)
  ++pos
  return c
}

// use exceptions for control flow?
val ParseState.next: Char
  @Throws(ParseError::class)
  get() {
    if (pos >= characters.length) expected("any character")
    return characters[pos++]
  }

// trying("foo") { ... } // executes the body and reports any failures inside as "expected foo", parsec-style `try`
inline fun <T> ParseState.trying(what: Any, action: Parser<T>): T = pos.let {
  val oldExpected = expected
  expected = null
  try { action(this) }
  catch (e: ParseError) {
    expected = Expected(what, oldExpected)
    throw ParseError(it) // with no expectations or message
  }
}

// <?> replaces any expected exceptions at the current location with the new name
inline fun <T> ParseState.named(what: Any, action: Parser<T>): T = pos.let {
  val oldExpected = expected
  expected = null
  try { return action(this)
  } catch (e: ParseError) {
    if (e.pos == it) expected = Expected(what, oldExpected)
    throw e
  }
}

// apply a regular expression to the current input, succeeding if we're looking at a hit for the regex
// positions reported by the matcher are global positions, not local ones.
@Throws(ParseError::class)
fun<T> ParseState.match(pattern: Pattern) : Matcher =
  pattern.matcher(characters).also {
    it.useTransparentBounds(true)
    it.region(pos,characters.length)
    if (!it.lookingAt()) expected(pattern)
    pos = it.end()
  }

  // choices can accumulate expectations
@Suppress("NOTHING_TO_INLINE") // really?
inline fun <T> ParseState.choice(vararg alts: Parser<T>): T {
  alts.forEach {
    try {
      return it(this)
    } catch (e: ParseError) {
      if (e.pos != pos) throw e; //  position didn't change, try next alternative
    }
  }
  throw ParseError(pos)
}

typealias SourceParser<T> = SourceParseState.() -> T
open class SourceParseState(val source: Source) : ParseState(source.characters)
val SourceParseState.name: String get() = source.name
val SourceParseState.path: String get() = source.path

sealed class SourceParseResult<out T>
data class Success<T>(val value: T): SourceParseResult<T>()
data class Failure(val pos: Int, val source: Source, val message: String? = null, val expected: Expected? = null): SourceParseResult<Nothing>() {
  val col: Int get() = source.getColumnNumber(pos)
  val line: Int get() = source.getLineNumber(pos)
  fun emit(builder: StringBuilder) {
    builder.run {
      val l = line
      val c = col
      append(source.name)
      append(':')
      append(l)
      append(':')
      append(c)
      append(" error: ")
      when {
        expected == null -> append(message ?: "expected nothing")
        message == null -> expected.toList().oxford()
        else -> {
          append(message)
          append(", expected ")
          expected.toList().oxford(this)
        }
      }
      append("\n\n")
      val lineStart = source.getLineStartOffset(l)
      val lineLength = source.getLineLength(l);
      append(subSequence(lineStart, lineStart + lineLength))
      append('\n')
      for(i in 0 until c) append(' ')
      append("^\n\n")
    }
  }
  val loc : String get() = "${source.name}:$line:$col"
  override fun toString(): String = StringBuilder(120).also { emit(it) }.toString()
}

fun <T> Source.parse(parser: SourceParser<T>) : SourceParseResult<T> =
  SourceParseState(this).let {
    try {
      Success(parser(it))
    } catch (e: ParseError) {
      Failure(it.pos, this, e.message, it.expected)
    }
  }

