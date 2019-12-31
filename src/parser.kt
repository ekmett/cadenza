package org.intelligence.parser

import cadenza.Loc
import java.util.regex.Matcher
import java.util.regex.Pattern

open class Parse(val characters: CharSequence) {
  class Error(var pos: Int, message: String? = null) : Exception(message) {
    override fun fillInStackTrace() = this // don't record
    companion object { const val serialVersionUID : Long = 1L }
  }
  data class Expected(val what: Any, val next: Expected?)
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
    out.add(0,current.what)
    current = current.next
  }
  return out
}


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
fun Parse.string(s: String): String {
  if (pos + s.length >= characters.length || characters.subSequence(pos, pos + s.length) != s) expected(s)
  pos += s.length
  return s
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

inline fun <P,T> P.trying(what: Any, action: P.() -> T): T where P : Parse = pos.let {
  val oldExpected = expects
  expects = null
  try { action(this) }
  catch (e: Parse.Error) {
    pos = it
    expects = Parse.Expected(what, oldExpected)
    throw Parse.Error(it) // with no expectations or message
  }
}

inline fun <P,T> P.named(what: Any, action: P.() -> T): T where P : Parse = pos.let {
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
inline fun <P,T> P.choice(vararg alts: P.() -> T): T where P : Parse {
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
inline fun <P,T> P.many(item: P.() -> T): List<T> where P : Parse {
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
inline fun <P,T> P.some(item: P.() -> T): List<T> where P : Parse {
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
inline fun <P,T> P.optional(item: P.() -> T): T? where P : Parse {
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
inline fun <P,A,B> P.manyTillPair(p: P.() -> A, q: P.() -> B): Pair<List<A>,B> where P : Parse {
  val result = mutableListOf<A>()
  while (true) {
    val last = optional(q)
    if (last != null) return Pair(result,last)
    result.add(p(this))
  }
}

@Throws(Parse.Error::class)
inline fun <P,A> P.manyTill(p: P.() -> A, q: P.() -> Any): List<A> where P : Parse {
  val result = mutableListOf<A>()
  while (true) {
    val last = optional(q)
    if (last != null) return result
    result.add(p(this))
  }
}

@Throws(Parse.Error::class)
inline fun <P,A> P.spanned(p: P.() -> A): Pair<A,Loc> where P : Parse {
  val a = pos
  val x = p()
  val b = pos
  return Pair(x, Loc.Range(a,b-a))
}
