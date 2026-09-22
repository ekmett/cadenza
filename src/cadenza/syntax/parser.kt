package cadenza.syntax

import cadenza.Loc
import cadenza.data.BigInt
import cadenza.semantics.Name
import cadenza.semantics.Term
import cadenza.semantics.Term.*
import cadenza.semantics.Type
import org.intelligence.parser.*
import java.math.BigInteger

val reserved = setOf("if", "then", "else", "let", "in", "Nat", "Bool")


val Parse.type: Type get() {
  val x = choice({ tok("Nat"); Type.Nat }, { tok("Bool"); Type.Bool }, { parens { type } })
  return choice({ tok("->"); Type.Arr(x, type) }, { x })
}
val Parse.ident: String get() = trying("ident") {
  val x = some { satisfy { it.isLetter() } }.joinToString("")
  if (reserved.contains(x)) { fail("reserved ident $x") }
  else { x }
}
val Parse.space: Unit get() { many { satisfy { it.isWhitespace() }} }
val Parse.lit: Term get() {
  val (digits, loc) = spanned { some { satisfy { it.isDigit() } }.joinToString("") }
  val small = digits.toIntOrNull()
  return if (small != null) TLitNat(small, loc) else TLitBigNat(BigInt(BigInteger(digits)), loc)
}
inline fun <T,A> T.token(f: T.() -> A): A where T : Parse { val a = f(); space; return a }
@Suppress("NOTHING_TO_INLINE")
inline fun <T>T.tok(x : String): String where T : Parse {
  // Check before consuming: an identifier beginning with a keyword must remain available
  // to the identifier alternative, rather than commit to the keyword's grammar production.
  if (x.lastOrNull()?.isLetter() == true) {
    val end = pos + x.length
    if (end < characters.length && (characters[end].isLetterOrDigit() || characters[end] == '_')) {
      expected(x)
    }
  }
  return token { string(x) }
}

/** The language entry consumes one complete source; grammar also serves nested expressions. */
val Parse.program: Term get() {
  space
  val result = grammar
  eof
  return result
}

inline fun <T,A> T.parens(f: T.() -> A): A where T : Parse {
  tok("(")
  val x = f()
  tok(")")
  return x
}

val Parse.tele: Array<Pair<Name,Type>> get() = some {
  parens {val x = token { ident }; tok(":"); val t = type; Pair(x, t)}
}.toTypedArray()

val Parse.grammar: Term get() = choice(
  {
    val (a, loc) = spanned {
      tok("\\")
      val t = tele
      tok("->")
      Pair(t, grammar)
    }
    TLam(a.first, a.second, loc)
  },{
  val start = pos
  tok("if")
  val cond = grammar
  tok("then")
  val then = grammar
  tok("else")
  val else_ = grammar
  TIf(cond, then, else_, Loc.Range(start, pos - start))
},{
  val start = pos
  tok("let")
  val nm = token { ident }
  tok(":")
  val ty = type
  tok("=")
  val vl = grammar
  tok("in")
  val bd = grammar
  TLet(nm, ty, vl, bd, Loc.Range(start, pos-start))
},{
  val (x, loc) = spanned { some {
    choice(
      { parens { grammar }},
      {
        val (a, loc) = spanned { token { ident } }
        TVar(a, loc)
      },
      { token { lit }}
    )
  }}
  if (x.size == 1) {
    x[0]
  } else {
    TApp(x[0], x.drop(1).toTypedArray(), loc)
  }
})
