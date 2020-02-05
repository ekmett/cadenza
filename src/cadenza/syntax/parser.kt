package cadenza.syntax

import cadenza.Loc
import cadenza.semantics.Name
import cadenza.semantics.Term
import cadenza.semantics.Term.*
import cadenza.semantics.Type
import org.intelligence.parser.*

val reserved = arrayOf("if","then","else","Nat","=","in")


val Parse.type: Type get() {
  val x = choice({tok("Nat"); Type.Nat })
  return choice({ tok("->"); Type.Arr(x, type) }, { x })
}
val Parse.ident: String get() = trying("ident") {
  val x = some { satisfy { it.isLetter() } }.joinToString("")
  if (reserved.contains(x)) { fail("reserved ident $x") }
  else { x }
}
val Parse.space: Unit get() { many { satisfy { it.isWhitespace() }} }
val Parse.lit: Term get() {
  val (x, loc) = spanned { some {satisfy { it.isDigit() }}.joinToString("").toInt() }
  return TLitNat(x, loc)
}
inline fun <T,A> T.token(f: T.() -> A): A where T : Parse { val a = f(); space; return a }
@Suppress("NOTHING_TO_INLINE")
inline fun <T>T.tok(x : String): String where T : Parse { return token { string(x) } }

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
      char('\\')
      val t = tele
      tok("->")
      Pair(t, grammar)
    }
    TLam(a.first, a.second, loc)
  },{
  tok("if")
  val cond = grammar
  tok("then")
  val then = grammar
  tok("else")
  val else_ = grammar
  TIf(cond, then, else_)
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

