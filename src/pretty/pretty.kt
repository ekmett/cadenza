package cadenza.pretty

import com.oracle.truffle.api.source.Source

// assumptions
typealias W = Int // characters
typealias Format = Unit
typealias Ann = Unit // eventually colors

val emptyFormat: Format = Unit
fun merge(@Suppress("UNUSED_PARAMETER") x: Format, @Suppress("UNUSED_PARAMETER") y: Format): Format = Unit
@Suppress("unused")
fun Pretty.measure(l: List<FormattedChunk>): W = l.sumBy { it.len() }

sealed class Out {
  abstract fun emit(s: StringBuilder)
}
data class Annotated(val ann: Ann, val body: Out) : Out() {
  override fun emit(s: StringBuilder) = body.emit(s) // and include annotations
}
data class Seq(val children: List<Out>) : Out() {
  override fun emit(s: StringBuilder) = children.forEach { it.emit(s) } // and include annotations
}
sealed class Atom : Out()
object Newline : Atom() {
  override fun emit(s: StringBuilder) { s.append('\n') }
}
sealed class Chunk : Atom() {
  abstract fun len(): W
}
data class Text(val text: CharSequence): Chunk() {
  override fun len(): W = text.length
  override fun emit(s: StringBuilder) { s.append(text) }
}
data class Space(val w: W): Chunk() {
  override fun len(): W = w
  override fun emit(s: StringBuilder) { for (i in 0 until w) s.append(' ') }
}

data class FormattedChunk(val fmt: Format, val chunk: Chunk) {
  fun len(): W = chunk.len()
}

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
  var format : Format = Unit,
  // state
  val curLine : Line = mutableListOf(),
  // output
  var output : MutableList<Out> = mutableListOf()
) {
  abstract fun formatAnn(a: Ann): Format
  fun tell(a: Out) = output.add(a)
}

typealias D<A> = Pretty.() -> A
typealias Doc = D<Unit>

fun <A> Pretty.run(d: D<A>): A = d(this)

// Chunk -> Doc
fun Pretty.chunk(c: Chunk) {
  curLine.add(FormattedChunk(format, c))
  if (canFail)
    measure(curLine).let {
      if (nesting + it > maxWidth || it > maxRibbon) {
        curLine.removeAt(curLine.size-1)
        fail
      }
    }
  tell(c)
}

// Doc -> Doc
@Suppress("NOTHING_TO_INLINE")
inline fun <A> Pretty.grouped(body: D<A>): A {
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

fun Pretty.text(t: CharSequence) = chunk(Text(t))
fun Pretty.char(c: Char) = text(c.toString())
fun Pretty.space(w: W) = chunk(Space(w))
val Pretty.space: Unit get() = space(spaceWidth)
val Pretty.hardLine: Unit get() { output.add(Newline); curLine.clear() }
val Pretty.newline: Unit get() { hardLine; space(nesting) }

@Suppress("NOTHING_TO_INLINE")
inline fun <A> Pretty.nest(w: W, body: D<A>): A {
  val oldNesting = nesting
  nesting += w
  try {
    return body(this)
  } finally {
    nesting = oldNesting
  }
}

@Suppress("NOTHING_TO_INLINE")
inline fun <A> Pretty.align(body: D<A>): A =
  nest(measure(curLine) - nesting, body)

@Suppress("NOTHING_TO_INLINE")
inline fun <A> Pretty.annotate(ann: Ann, body: D<A>): A {
  val fmt = formatAnn(ann)
  val oldOutput = output
  val oldFormat = format
  format = merge(format,fmt)
  output = mutableListOf()
  try {
    return body(this)
  } finally {
    oldOutput.add(Annotated(ann,Seq(output)))
    output = oldOutput
    format = oldFormat
  }
}

@Suppress("NOTHING_TO_INLINE")
inline fun Pretty.intersperse(delim: Doc, vararg docs: Doc) = intersperseBy(Pretty::run, delim, *docs)

@Suppress("NOTHING_TO_INLINE")
inline fun <T> Pretty.intersperseBy(by: Pretty.(T) -> Unit, delim: Doc, vararg docs: T) {
  var first = true
  docs.forEach {
    if (first) { first = false }
    else { delim(this) }
    by(this,it)
  }
}

fun Pretty.measureText(s: String): W = measure(listOf(FormattedChunk(format, Text(s))))
val Pretty.spaceWidth: W get() = measureText(" ")
val Pretty.emWidth: W get() = measureText(" ")

@Suppress("NOTHING_TO_INLINE")
inline fun Pretty.hsep(vararg docs: Doc) = intersperse({ text(" ") }, *docs)

@Suppress("NOTHING_TO_INLINE")
inline fun <T> Pretty.hsepBy(by: Pretty.(T) -> Unit, vararg docs: T) = intersperseBy(by, { text(" ") }, *docs)

@Suppress("NOTHING_TO_INLINE")
inline fun Pretty.vsep(vararg docs: Doc) = intersperse({ newline }, *docs)

@Suppress("NOTHING_TO_INLINE")
inline fun <T> Pretty.vsepBy(by: Pretty.(T) -> Unit, vararg docs: T) = intersperseBy(by, { newline }, *docs)

@Suppress("NOTHING_TO_INLINE")
inline fun Pretty.hvsep(vararg docs: Doc) {
  val s = spaceWidth
  grouped { intersperse({ if (isFlat) space(s) else newline }, *docs) }
}

@Suppress("NOTHING_TO_INLINE")
inline fun <T> Pretty.hvsepBy(by: Pretty.(T) -> Unit, vararg docs: T) {
  val s = spaceWidth
  grouped { intersperseBy(by, { if (isFlat) space(s) else newline }, *docs) }
}

@Suppress("NOTHING_TO_INLINE")
inline fun Pretty.hsepTight(vararg docs: Doc) {
  val s = spaceWidth
  grouped { intersperse({ if (!isFlat) space(s) }, *docs) }
}

@Suppress("NOTHING_TO_INLINE")
inline fun <T> Pretty.hsepByTight(by: Pretty.(T) -> Unit, vararg docs: T) {
  val s = spaceWidth
  grouped { intersperseBy(by, { if (!isFlat) space(s) }, *docs) }
}

@Suppress("NOTHING_TO_INLINE")
inline fun Pretty.hvsepTight(vararg docs: Doc) = grouped { intersperse({ if (!isFlat) newline }, *docs) }

@Suppress("NOTHING_TO_INLINE")
inline fun <T> Pretty.hvsepByTight(by: Pretty.(T) -> Unit, vararg docs: T) = grouped { intersperseBy(by, { if (!isFlat) newline }, *docs) }

fun doc(x: Doc): Doc = x

fun Pretty.guttered(t: String) {
  val s = spaceWidth
  if (isFlat) space(s)
  else {
    hardLine
    val delta = measureText(t) + s
    space(
      if (nesting >= delta) nesting - delta
      else nesting
    ) // try to push into gutter, otherwise give up and ungutter it completely
  }
  text(t)
  space(s)
}

fun Pretty.simple(t: Any?) { text(t?.toString() ?: "null") }

fun <T> Pretty.oxfordBy(by: Pretty.(T) -> Unit = Pretty::simple, conjunction: String = "or", vararg docs: T) =
  grouped {
    val s = spaceWidth
    val n = docs.size
    align {
      when (n) {
        0 -> text("nothing")
        1 -> by(this,docs[0])
        2 -> {
          by(this,docs[0])
          guttered(conjunction)
          by(this,docs[1])
        }
        else -> {
          for (i in docs.indices) {
            by(this,docs[i])
            when (i) {
              n - 1 -> {}
              n - 2 -> { text(","); guttered(conjunction) }
              else -> { text(","); if (isFlat) space(s) else newline }
            }
          }
        }
      }
    }
  }


fun Pretty.oxford(conjunction: String = "or", vararg docs: Doc) = oxfordBy(Pretty::run, conjunction, *docs)

fun Pretty.collection(open: Doc, close: Doc, sep: Doc, vararg docs: Doc) {
  if (docs.isEmpty()) { open(this); close(this) }
  else grouped {
    hvsepTight(
      { hsepTight(open,{align(docs[0])}) },
      *docs.drop(1).map {x -> doc {hsep(sep, {align(x)}) } }.toTypedArray(),
      close
    )
  }
}

fun <T> Pretty.collectionBy(by: Pretty.(T) -> Unit, open: Doc, close: Doc, sep: Doc, vararg docs: T) {
  if (docs.isEmpty()) { open(this); close(this) }
  else grouped {
    hvsepTight(
      { hsepTight(open,{align{by(this,docs[0])}}) },
      *docs.drop(1).map {x -> doc {hsep(sep, {align{by(this,x)}})}}.toTypedArray(),
      close
    )
  }
}

@Suppress("NOTHING_TO_INLINE")
inline fun <A> Pretty.expr(d: D<A>): A = align { grouped(d) }

const val DEFAULT_MAX_WIDTH: Int = 80
const val DEFAULT_MAX_RIBBON: Int = 60

fun prep(maxWidth: W = DEFAULT_MAX_WIDTH, maxRibbon: W = DEFAULT_MAX_RIBBON, doc: Doc): Out {
  val printer = object : Pretty(maxWidth,maxRibbon, 0, false, false) {
    override fun formatAnn(a: Ann) {}
  }
  doc(printer)
  return Seq(printer.output)
}

fun ppString(maxWidth: W = DEFAULT_MAX_WIDTH, maxRibbon: W = DEFAULT_MAX_RIBBON, doc: Doc): String {
  val builder = StringBuilder()
  prep(maxWidth, maxRibbon, doc).emit(builder)
  return builder.toString()
}

fun pp(maxWidth: W = DEFAULT_MAX_WIDTH, maxRibbon: W = DEFAULT_MAX_RIBBON, doc: Doc) = println(ppString(maxWidth, maxRibbon, doc))

// convenient pretty printer
fun Pretty.error(source: Source, pos: Int, message: String? = null, vararg expected: Any) {
  val l = source.getLineNumber(pos)
  val c = source.getColumnNumber(pos)
  text(source.name); char(':'); simple(l); char(':'); simple(c); space
  text("error:"); space
  nest(2) {
    if (expected.isEmpty()) text(message ?: "expected nothing")
    else {
      if (message != null) { text(message);text(",");space }
      text("expected")
      space
      oxfordBy(by = Pretty::simple, conjunction = "or", docs = *expected)
    }
  }
  hardLine
  val ls = source.getLineStartOffset(l)
  val ll = source.getLineLength(l)
  text(source.characters.subSequence(ls, ls+ll))
  nest(c-1) { newline; char('^') }
  hardLine
}
