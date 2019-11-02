package org.intelligence.pretty

import org.fusesource.jansi.Ansi

// assumptions
typealias W = Int // characters

sealed class Out {
  abstract fun emit(s: Ansi)
}

data class Annotated(val ann: Ann, val body: Out) : Out() {
  override fun emit(s: Ansi) {
    ann.set(s)
    try {
      body.emit(s)
    } finally {
      ann.reset(s)
    }
  }
}

data class Seq(val children: List<Out>) : Out() {
  override fun emit(s: Ansi) = children.forEach { it.emit(s) } // and include annotations
}

internal sealed class Atom : Out()

internal object Newline : Atom() {
  override fun emit(s: Ansi) { s.a(System.getProperty("line.separator")) }
}

internal sealed class Chunk : Atom() {
  abstract fun len(): W
}

internal data class Text(val text: CharSequence): Chunk() {
  override fun len(): W = text.length
  override fun emit(s: Ansi) { s.a(text) }
}

internal data class Space(val w: W): Chunk() {
  override fun len(): W = w
  override fun emit(s: Ansi) { for (i in 0 until w) s.a(' ') }
}

interface Ann {
  val delta: Format.Delta
  fun set(ansi: Ansi)
  fun reset(ansi: Ansi)
}

data class Color(val color: Ansi.Color, val bright: Boolean) {
  fun fg(ansi: Ansi) { if (bright) ansi.fg(color) else ansi.fgBright(color) }
  fun bg(ansi: Ansi) { if (bright) ansi.bg(color) else ansi.bgBright(color) }
}

class Format private constructor(
  val fg: Color = Color(Ansi.Color.DEFAULT, false),
  val bg: Color = Color(Ansi.Color.DEFAULT, false),
  val italic: Boolean = false,
  val bold: Boolean = false
) {
  fun set(s: Ansi) { // forcibly set the format
    // forcibly set this format
    s.reset()
    fg.fg(s)
    bg.bg(s)
    if (italic) s.a(Ansi.Attribute.ITALIC)
    if (bold) s.bold()
  }

  class Delta(
    val fg: Color? = null,
    val bg: Color? = null,
    val italic: Boolean? = null,
    val bold: Boolean? = null
  ) {
    operator fun plus(other: Delta) = Delta(
      other.fg ?: fg,
      other.bg ?: bg,
      other.italic ?: italic,
      other.bold ?: bold
    )
  }
  operator fun plus(other: Delta) = Format(
    other.fg ?: fg,
    other.bg ?: bg,
    other.italic ?: italic,
    other.bold ?: bold
  )

  companion object {
    val default: Format by lazy { Format() }
  }
}

class Pretty(
  var maxWidth : W = DEFAULT_MAX_WIDTH,
  var maxRibbon : W = DEFAULT_MAX_RIBBON, // i like big chonky ribbons
  var nesting : W = 0,
  var isFlat : Boolean = false, // if not flat we're in "break" mode
  var canFail : Boolean = false,
  var format : Format = Format.default,
  var curLineLen: W = 0, // memoized sum of the lengths of the elements on the current line
  var output : MutableList<Out> = mutableListOf()
) {
  fun tell(a: Out) = output.add(a)

  internal object Bad : RuntimeException() { override fun fillInStackTrace() = this }
  internal val bad: Nothing get() { assert(canFail); throw Bad }

  companion object {
    const val DEFAULT_MAX_WIDTH: Int = 80
    const val DEFAULT_MAX_RIBBON: Int = 60

    fun prep(maxWidth: W = DEFAULT_MAX_WIDTH, maxRibbon: W = DEFAULT_MAX_RIBBON, doc: Doc): Out {
      val printer = Pretty(maxWidth,maxRibbon)
      doc(printer)
      return Seq(printer.output)
    }

    fun ppString(maxWidth: W = DEFAULT_MAX_WIDTH, maxRibbon: W = DEFAULT_MAX_RIBBON, doc: Doc): String {
      val builder = Ansi.ansi()
      prep(maxWidth, maxRibbon, doc).emit(builder)
      return builder.toString()
    }

    fun pp(maxWidth: W = DEFAULT_MAX_WIDTH, maxRibbon: W = DEFAULT_MAX_RIBBON, doc: Doc) = println(ppString(maxWidth, maxRibbon, doc))
    fun doc(x: Doc): Doc = x

    fun Ansi.out(s: Out): Ansi = this.also { s.emit(this) }
  }

  fun text(t: CharSequence) = chunk(Text(t))
  fun char(c: Char) = text(c.toString())
  fun space(w: W) = chunk(Space(w))
  val space: Unit get() = space(spaceWidth)
  val hardLine: Unit get() { output.add(Newline); curLineLen = 0 }
  val newline: Unit get() { hardLine; space(nesting) }
  internal fun chunk(c: Chunk) {
    val newLineLen = curLineLen + c.len()
    if (canFail && (nesting + newLineLen > maxWidth || newLineLen > maxRibbon)) bad
    tell(c)
    curLineLen = newLineLen
  }
  fun measureText(s: String): W = s.length // it may some day care about current format, not in ansi, but elsewhere
  val spaceWidth: W get() = measureText(" ")
  val emWidth: W get() = measureText("M")
}

typealias D<A> = Pretty.() -> A
typealias Doc = D<Unit>

inline fun <A> Pretty.run(d: D<A>): A = d(this)

// Doc -> Doc
@Suppress("NOTHING_TO_INLINE")
inline fun <A> Pretty.grouped(body: D<A>): A {
  if (!isFlat) {
    val oldCanFail = canFail
    canFail = true
    isFlat = true
    try {
      return body(this)
    } catch(e: Pretty.Bad) {
    } finally {
      canFail = oldCanFail
      isFlat = false
    }
  }
  return body(this)
}

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
  nest(curLineLen - nesting, body)

@Suppress("NOTHING_TO_INLINE")
inline fun <A> Pretty.annotate(ann: Ann, body: D<A>): A {
  val oldOutput = output
  val oldFormat = format
  format += ann.delta
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
      *docs.drop(1).map {x -> Pretty.doc {hsep(sep, {align(x)}) } }.toTypedArray(),
      close
    )
  }
}

fun <T> Pretty.collectionBy(by: Pretty.(T) -> Unit, open: Doc, close: Doc, sep: Doc, vararg docs: T) {
  if (docs.isEmpty()) { open(this); close(this) }
  else grouped {
    hvsepTight(
      { hsepTight(open,{align{by(this,docs[0])}}) },
      *docs.drop(1).map {x -> Pretty.doc {hsep(sep, {align{by(this,x)}})}}.toTypedArray(),
      close
    )
  }
}

@Suppress("NOTHING_TO_INLINE")
inline fun <A> Pretty.expr(d: D<A>): A = align { grouped(d) }

// ansi color

fun <A> Pretty.fg(color: Ansi.Color, bright: Boolean = true, f: D<A>): A {
  val old: Format = format
  val new = Color(color, bright)
  val ann = object : Ann {
    override val delta: Format.Delta get() = Format.Delta(fg = new)
    override fun set(ansi: Ansi) = new.fg(ansi)
    override fun reset(ansi: Ansi) =
      if (old.fg.color == Ansi.Color.DEFAULT) old.set(ansi) // default colors are dumb, reset is the only way to be sure
      else old.fg.fg(ansi)
  }
  return annotate(ann,f)
}

fun <A> Pretty.red(bright: Boolean = true, f: D<A>): A = fg(Ansi.Color.RED, bright, f)
fun <A> Pretty.green(bright: Boolean = true, f: D<A>): A = fg(Ansi.Color.GREEN, bright, f)
fun <A> Pretty.blue(bright: Boolean = true, f: D<A>): A = fg(Ansi.Color.BLUE, bright, f)
fun <A> Pretty.magenta(bright: Boolean = true, f: D<A>): A = fg(Ansi.Color.MAGENTA, bright, f)
fun <A> Pretty.white(bright: Boolean = true, f: D<A>): A = fg(Ansi.Color.WHITE, bright, f)
fun <A> Pretty.black(bright: Boolean = true, f: D<A>): A = fg(Ansi.Color.BLACK, bright, f)
fun <A> Pretty.cyan(bright: Boolean = true, f: D<A>): A = fg(Ansi.Color.CYAN, bright, f)
fun <A> Pretty.yellow(bright: Boolean = true, f: D<A>): A = fg(Ansi.Color.YELLOW, bright, f)

fun <A> Pretty.bg(color: Ansi.Color, bright: Boolean = false, f: D<A>): A {
  val old: Format = format
  val new = Color(color, bright)
  val ann = object : Ann {
    override val delta: Format.Delta get() = Format.Delta(bg = new)
    override fun set(ansi: Ansi) = new.bg(ansi)
    override fun reset(ansi: Ansi) = if (old.bg.color == Ansi.Color.DEFAULT) old.set(ansi) else old.bg.bg(ansi)
  }
  return annotate(ann,f)
}

fun<A> Pretty.bold(f: D<A>): A =
  if (format.bold) f(this)
  else {
    val ann = object : Ann {
      override val delta: Format.Delta get() = Format.Delta(bold = true)
      override fun set(ansi: Ansi) { ansi.bold() }
      override fun reset(ansi: Ansi) { ansi.boldOff() }
    }
    annotate(ann, f)
  }

fun<A> Pretty.italic(f: D<A>): A =
  if (format.italic) f(this)
  else {
    val ann = object : Ann {
      override val delta: Format.Delta get() = Format.Delta(bold = true)
      override fun set(ansi: Ansi) { ansi.a(Ansi.Attribute.ITALIC) }
      override fun reset(ansi: Ansi) { ansi.a(Ansi.Attribute.ITALIC_OFF) }
    }
    annotate(ann, f)
  }

