package cadenza.syntax

import cadenza.semantics.Name
import cadenza.semantics.Term
import cadenza.semantics.Term.Companion.tapp
import cadenza.semantics.Term.Companion.tif
import cadenza.semantics.Term.Companion.tlitNat
import cadenza.semantics.Term.Companion.tlam
import cadenza.semantics.Term.Companion.tvar
import cadenza.semantics.Type
import org.intelligence.parser.*

val reserved = arrayOf("if","then","else","Nat")


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
val Parse.lit: Term get() = tlitNat(some {satisfy { it.isDigit() }}.joinToString("").toInt())
inline fun <T,A> T.token(f: T.() -> A): A where T : Parse { val a = f(); space; return a }
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

val Parse.grammar: Term get() = choice({
  char('\\')
  val t = tele
  tok("->")
  tlam(t, grammar)
},{
  tok("if")
  val cond = grammar
  tok("then")
  val then = grammar
  tok("else")
  val else_ = grammar
  tif(cond, then, else_)
},{
  val x = some {
    choice(
      { parens { grammar }},
      { tvar( token { ident })},
      { token { lit }}
    )
  }
  if (x.size == 1) {
    x[0]
  } else {
    tapp(x[0], *(x.drop(1).toTypedArray()))
  }
})
