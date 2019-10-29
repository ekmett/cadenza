package cadenza.parsing

import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.source.Source
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.lang.StringBuilder


// generate an oxford comma separated list, TODO: de-dupe by sending it to a set first?
fun List<Any>.oxford(): String = StringBuilder().also { this.oxford(it) }.toString()
fun List<Any>.oxford(it: StringBuilder) {
  val n = this.size
  it.append(n)
  it.append(" item")
  if (n != 1) it.append('s')
  it.append(": ")
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

class ParseState(val source: Source) {
  val characters: CharSequence = source.characters
  var expected: Expected? = null // var is intended, we swap it out regularly
    set(value) {
      println("set expected: ${value.toList().oxford()}")
      field = value
    }
  var pos: Int = 0
    set(value) {
      if (field != value) {
        field = value
        //println("advancing to ${field} old expected: ${expected.toList().oxford()}")
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
    pos = it
    expected = Expected(what, oldExpected)
    throw ParseError(it) // with no expectations or message
  }
}

// <?> replaces any expected exceptions at the current location with the new name
fun <T> ParseState.named(what: Any, action: Parser<T>): T = pos.let {
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
fun ParseState.match(pattern: Pattern) : Matcher =
  pattern.matcher(characters).also {
    it.useTransparentBounds(true)
    it.region(pos,characters.length)
    if (!it.lookingAt()) expected(pattern)
    pos = it.end()
  }

@Suppress("NOTHING_TO_INLINE") // really?
fun <T> ParseState.choice(vararg alts: Parser<T>): T {
  val old = pos
  alts.forEach {
    println("alt!!!")
    try {
      return it(this)
    } catch (e: ParseError) {
      if (e.pos != old) throw e; //  position didn't change, try next alternative
    }
  }
  throw ParseError(old)
}

@Throws(ParseError::class)
fun <T> ParseState.many(item: Parser<T>): List<T> {
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
data class Failure(val pos: Int, val source: Source, val message: String? = null, val expected: Expected? = null): ParseResult<Nothing>() {
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
        expected === null -> append(message ?: "expected nothing")
        message === null -> {
          append("expected ")
          append(expected.toList().oxford())
        }
        else -> {
          append(message)
          append(", expected ")
          expected.toList().oxford(this)
        }
      }
      append("\n\n")
      val lineStart = source.getLineStartOffset(l)
      val lineLength = source.getLineLength(l);
      append(source.characters.subSequence(lineStart, lineStart + lineLength))
      append('\n')
      for(i in 0 until c - 1) append(' ')
      append("^\n\n")
    }
  }
  val loc : String get() = "${source.name}:$line:$col"
  override fun toString(): String = StringBuilder(120).also { emit(it) }.toString()
}

fun <T> Source.parse(parser: Parser<T>) : ParseResult<T> =
  ParseState(this).let {
    try {
      Success(parser(it))
    } catch (e: ParseError) {
      Failure(it.pos, this, e.message, it.expected)
    }
  }
