package org.intelligence.parser

import com.oracle.truffle.api.source.Source
import org.intelligence.diagnostics.Severity
import org.intelligence.diagnostics.error
import org.intelligence.pretty.Pretty
import java.util.regex.Matcher
import java.util.regex.Pattern

class Parse(val source: Source) {
  class Error(var pos: Int, message: String? = null) : Exception(message) {
    override fun fillInStackTrace() = this // don't record
    companion object { const val serialVersionUID : Long = 1L }
  }

  data class Expected(val what: Any, val next: Expected?)

  val characters: CharSequence = source.characters
  var expects: Expected? = null // var is intended, we swap it out regularly

  var pos: Int = 0
    set(value) {
      if (field != value) {
        field = value
        expects = null
      }
    }

  val expected: List<Any> get() = expects.toList()
}

fun Parse.Expected?.toList() : List<Any> {
  val out = mutableListOf<Any>()
  var current = this
  while (current != null) {
    out.add(current.what)
    current = current.next
  }
  return out
}

// these would nested classes but for KT-1395 screwing up my namespaces
sealed class ParseResult<out T>
data class ParseSuccess<T>(val value: T): ParseResult<T>()
data class ParseFailure(
  val source: Source,
  val pos: Int,
  val message: String? = null,
  val expected: List<Any> = emptyList()
): ParseResult<Nothing>() {
  val line: Int get() = source.getLineNumber(pos)
  val col: Int get() = source.getColumnNumber(pos)
  val loc : String get() = "${source.name}:$line:$col"
  override fun toString(): String = Pretty.ppString {
    error(Severity.error, source, pos, message, *expected.toTypedArray())
  }
}

val Parse.name: String get() = source.name
val Parse.path: String get() = source.path
val Parse.line: Int get() = source.getLineNumber(pos)
val Parse.col: Int get() = source.getColumnNumber(pos)

// parsers consume parsestate as this and throw Parsing.Errors on failure
typealias Parser<T> = Parse.() -> T

// mark/release support
inline class Mark(val pos: Int)
val Parse.mark: Mark get() = Mark(pos)
fun Parse.release(mark: Mark) { pos = mark.pos }

@Throws(Parse.Error::class)
fun Parse.fail(message: String? = null): Nothing = throw Parse.Error(pos, message)

@Throws(Parse.Error::class)
fun Parse.expected(what: Any): Nothing {
  expects = Parse.Expected(what, expects)
  throw Parse.Error(pos)
}

fun <A> parser(p: Parser<A>): Parser<A> = p

@Throws(Parse.Error::class)
fun expected2(what: Any): Parser<Nothing> = parser {
  expects = Parse.Expected(what, expects)
  throw Parse.Error(pos)
}

val Parse.eof: Unit
  @Throws(Parse.Error::class)
  get() {
    if (!atEof) expected("EOF")
  }

val Parse.atEof: Boolean get() = pos == characters.length

@Throws(Parse.Error::class)
fun Parse.char(c: Char): Char {
  if (pos >= characters.length || c != characters[pos]) expected(c)
  ++pos
  return c
}

@Throws(Parse.Error::class)
fun Parse.satisfy(predicate : (Char) -> Boolean): Char {
  if (pos >= characters.length) fail()
  val c = characters[pos]
  if (!predicate(c)) fail()
  ++pos
  return c
}

val Parse.next: Char
  @Throws(Parse.Error::class)
  get() {
    if (pos >= characters.length) expected("any character")
    return characters[pos++]
  }

inline fun <T> Parse.trying(what: Any, action: Parser<T>): T = pos.let {
  val oldExpected = expects
  expects = null
  try { action(this) }
  catch (e: Parse.Error) {
    pos = it
    expects = Parse.Expected(what, oldExpected)
    throw Parse.Error(it) // with no expectations or message
  }
}

fun <T> Parse.named(what: Any, action: Parser<T>): T = pos.let {
  val oldExpected = expects
  expects = null
  try { return action(this)
  } catch (e: Parse.Error) {
    if (e.pos == it) expects = Parse.Expected(what, oldExpected)
    throw e
  }
}

@Throws(Parse.Error::class)
fun Parse.match(pattern: Pattern) : Matcher =
  pattern.matcher(characters).also {
    it.useTransparentBounds(true)
    it.region(pos,characters.length)
    if (!it.lookingAt()) expected(pattern)
    pos = it.end()
  }

@Suppress("NOTHING_TO_INLINE") // really?
inline fun <T> Parse.choice(vararg alts: Parser<T>): T {
  val old = pos
  alts.forEach {
    try {
      return it(this)
    } catch (e: Parse.Error) {
      if (e.pos != old) throw e //  position didn't change, try next alternative
    }
  }
  throw Parse.Error(old)
}

@Throws(Parse.Error::class)
inline fun <T> Parse.many(item: Parser<T>): List<T> {
  val result = mutableListOf<T>()
  while (true) {
    val old = pos
    try {
      result.add(item(this))
    } catch (e: Parse.Error) {
      if (e.pos == old) return result // failed without consuming, success
      throw e
    }
  }
}

@Throws(Parse.Error::class)
inline fun <T> Parse.some(item: Parser<T>): List<T> {
  val result = mutableListOf<T>()
  result.add(item(this))
  while (true) {
    val old = pos
    try {
      result.add(item(this))
    } catch (e: Parse.Error) {
      if (e.pos == old) return result
      throw e
    }
  }
}

@Throws(Parse.Error::class)
inline fun <T> Parse.optional(item: Parser<T>): T? {
  val old = pos
  return try {
    item(this)
  } catch (e: Parse.Error) {
    if (pos == old) null
    else throw e
  }
}

@Throws(Parse.Error::class)
fun <T> Parse.parse(p: Parser<T>): T = p(this)

@Throws(Parse.Error::class)
inline fun <A,B> Parse.manyTillPair(p: Parser<A>, q: Parser<B>): Pair<List<A>,B> {
  val result = mutableListOf<A>()
  while (true) {
    val last = optional(q)
    if (last != null) return Pair(result,last)
    result.add(p(this))
  }
}

@Throws(Parse.Error::class)
inline fun <A> Parse.manyTill(p: Parser<A>, q: Parser<*>): List<A> {
  val result = mutableListOf<A>()
  while (true) {
    val last = optional(q)
    if (last != null) return result
    result.add(p(this))
  }
}

fun <T> Source.parse(parser: Parser<T>) : ParseResult<T> =
  Parse(this).let {
    try {
      ParseSuccess(parser(it))
    } catch (e: Parse.Error) {
      ParseFailure(this, it.pos, e.message, it.expects.toList())
    }
  }