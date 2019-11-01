package cadenza.parser

import cadenza.pretty.*
import com.oracle.truffle.api.source.Source
import java.util.regex.Matcher
import java.util.regex.Pattern

class ParseError(var pos: Int, message: String? = null) : Exception(message) {
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
  var current = this
  while (current != null) {
    out.add(current.what)
    current = current.next
  }
  return out
}

class ParseState(val source: Source) {
  val characters: CharSequence = source.characters
  var expected: Expected? = null // var is intended, we swap it out regularly
  var pos: Int = 0
    set(value) {
      if (field != value) {
        field = value
        expected = null
      }
    }
}

val ParseState.name: String get() = source.name
val ParseState.path: String get() = source.path
val ParseState.line: Int get() = source.getLineNumber(pos)
val ParseState.col: Int get() = source.getColumnNumber(pos)

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

fun <A> parser(p: Parser<A>): Parser<A> = p

@Throws(ParseError::class)
fun expected2(what: Any): Parser<Nothing> = parser {
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

@Throws(ParseError::class)
fun ParseState.satisfy(predicate : (Char) -> Boolean): Char {
  if (pos >= characters.length) fail()
  val c = characters[pos]
  if (!predicate(c)) fail()
  ++pos
  return c
}

val ParseState.next: Char
  @Throws(ParseError::class)
  get() {
    if (pos >= characters.length) expected("any character")
    return characters[pos++]
  }

inline fun <T> ParseState.trying(what: Any, action: Parser<T>): T = pos.let {
  val oldExpected = expected
  expected = null
  try { action(this) }
  catch (e: ParseError) {
    pos = it
    expected = Expected(what, oldExpected)
    throw ParseError(it) // with no expectations or message
  }
}

fun <T> ParseState.named(what: Any, action: Parser<T>): T = pos.let {
  val oldExpected = expected
  expected = null
  try { return action(this)
  } catch (e: ParseError) {
    if (e.pos == it) expected = Expected(what, oldExpected)
    throw e
  }
}

@Throws(ParseError::class)
fun ParseState.match(pattern: Pattern) : Matcher =
  pattern.matcher(characters).also {
    it.useTransparentBounds(true)
    it.region(pos,characters.length)
    if (!it.lookingAt()) expected(pattern)
    pos = it.end()
  }

@Suppress("NOTHING_TO_INLINE") // really?
inline fun <T> ParseState.choice(vararg alts: Parser<T>): T {
  val old = pos
  alts.forEach {
    try {
      return it(this)
    } catch (e: ParseError) {
      if (e.pos != old) throw e //  position didn't change, try next alternative
    }
  }
  throw ParseError(old)
}

@Throws(ParseError::class)
inline fun <T> ParseState.many(item: Parser<T>): List<T> {
  val result = mutableListOf<T>()
  while (true) {
    val old = pos
    try {
      result.add(item(this))
    } catch (e: ParseError) {
      if (e.pos == old) return result // failed without consuming, success
      throw e
    }
  }
}

@Throws(ParseError::class)
inline fun <T> ParseState.some(item: Parser<T>): List<T> {
  val result = mutableListOf<T>()
  result.add(item(this))
  while (true) {
    val old = pos
    try {
      result.add(item(this))
    } catch (e: ParseError) {
      if (e.pos == old) return result
      throw e
    }
  }
}

@Throws(ParseError::class)
inline fun <T> ParseState.optional(item: Parser<T>): T? {
  val old = pos
  return try {
    item(this)
  } catch (e: ParseError) {
    if (pos == old) null
    else throw e
  }
}

@Throws(ParseError::class)
fun <T> ParseState.parse(p: Parser<T>): T = p(this)

@Throws(ParseError::class)
inline fun <A,B> ParseState.manyTillPair(p: Parser<A>, q: Parser<B>): Pair<List<A>,B> {
  val result = mutableListOf<A>()
  while (true) {
    val last = optional(q)
    if (last != null) return Pair(result,last)
    result.add(p(this))
  }
}

@Throws(ParseError::class)
inline fun <A> ParseState.manyTill(p: Parser<A>, q: Parser<*>): List<A> {
  val result = mutableListOf<A>()
  while (true) {
    val last = optional(q)
    if (last != null) return result
    result.add(p(this))
  }
}

sealed class ParseResult<out T>
data class Success<T>(val value: T): ParseResult<T>()
data class Failure(
  val source: Source,
  val pos: Int,
  val message: String? = null,
  val expected: List<Any> = emptyList()
): ParseResult<Nothing>() {
  val line: Int get() = source.getLineNumber(pos)
  val col: Int get() = source.getColumnNumber(pos)
  val loc : String get() = "${source.name}:$line:$col"
  override fun toString(): String = ppString { error(source, pos, message, *expected.toTypedArray()) }
}

fun <T> Source.parse(parser: Parser<T>) : ParseResult<T> =
  ParseState(this).let {
    try {
      Success(parser(it))
    } catch (e: ParseError) {
      Failure(this, it.pos, e.message, it.expected.toList())
    }
  }
