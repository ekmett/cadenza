package org.intelligence.pretty

// assumptions
typealias W = Int // characters
typealias Format = Unit
typealias Ann = Unit // eventually colors
val emptyFormat: Format = Unit
fun merge(x: Format, y: Format): Format = Unit
fun Pretty.measure(l: Line): W = l.sumBy { it.chunk.getChunkLen() }

sealed class Out
data class Annotated(val ann: Ann, val body: List<Out>) : Out()
data class Seq(val children: List<Out>) : Out()
sealed class Atom() : Out()
object Newline : Atom()
sealed class Chunk() : Atom() {
  abstract fun getChunkLen(): W
}
data class Text(val s: String): Chunk() {
  override fun getChunkLen(): W = s.length
}
data class Space(val w: W): Chunk() {
  override fun getChunkLen(): W = w
}

data class FormattedChunk(val fmt: Format, val chunk: Chunk)
typealias Line = MutableList<FormattedChunk>

class Fail : RuntimeException() { override fun fillInStackTrace() = this }
val fail: Nothing get() { throw Fail() }

abstract class Pretty(
  // env
  var maxWidth : W,
  var maxRibbon : W,
  var nesting : W,
  var isFlat : Boolean, // if not flat we're in "break" mode
  var canFail : Boolean,
  var format : Format,
  var formatAnn : (Ann) -> Format,
  // state
  val curLine : Line = mutableListOf(),
  // output
  var output : MutableList<Out>
) {
  abstract fun formatAnn(a: Ann): Format
  fun tell(a: Out) = output.add(a)
}

typealias M<A> = Pretty.() -> A
typealias Doc = M<Unit>

// Chunk -> Doc
fun Pretty.chunk(c: Chunk) {
  output.add(c)
  curLine.add(FormattedChunk(format, c))
  if (canFail) measure(curLine).let { if (nesting + it > maxWidth || it > maxRibbon) fail }
}

// Doc -> Doc
inline fun <A> Pretty.grouped(body: M<A>): A {
  if (!isFlat) {
    val oldCanFail = canFail
    canFail = true
    isFlat = true
    try {
      return body(this)
    } catch(e: Fail) {
    } finally {
      canFail = oldCanFail
      isFlat = false
    }
  }
  return body(this)
}

fun Pretty.text(t: String) = chunk(Text(t))
fun Pretty.char(c: Char) = text(c.toString())
fun Pretty.space(w: W) = chunk(Space(w))
val Pretty.hardLine: Unit get() {
  output.add(Newline)
  curLine.clear()
}
val Pretty.newline: Unit get() {
  hardLine
  space(nesting)
}

inline fun <A> Pretty.nest(w: W, body: M<A>): A {
  val oldNesting = nesting
  nesting += w
  try {
    return body(this)
  } finally {
    nesting = oldNesting
  }
}

inline fun <A> Pretty.align(body: M<A>): A =
  nest(measure(curLine) - nesting, body)

inline fun <A> Pretty.annotate(ann: Ann, body: M<A>): A {
  val fmt = formatAnn(ann)
  val oldOutput = output
  val oldFormat = format
  format = merge(format,fmt)
  output = mutableListOf()
  try {
    return body(this)
  } finally {
    oldOutput.add(Annotated(ann,output))
    output = oldOutput
    format = oldFormat
  }
}

//inline fun Pretty.hsep(vararg docs: Doc) {
//}