package cadenza.syntax

import cadenza.semantics.Name
import cadenza.semantics.Term
import cadenza.semantics.Term.Companion.tapp
import cadenza.semantics.Term.Companion.tlam
import cadenza.semantics.Term.Companion.tvar
import cadenza.semantics.Type
import org.intelligence.parser.*

val Parse.type: Type get() = choice({token { string("Nat") }; Type.Nat })
val Parse.ident: String get() = trying("ident") { some { satisfy { it.isLetter() } }.joinToString() }
val Parse.space: Unit get() { many { satisfy { it.isWhitespace() }} }
inline fun <T,A> T.token(f: T.() -> A): A where T : Parse {
  val a = f()
  space
  return a
}

inline fun <T,A> T.parens(f: T.() -> A): A where T : Parse {
  token { char('(') }
  val x = f()
  token { char(')') }
  return x
}

val Parse.tele: Array<Pair<Name,Type>> get() = some {
  parens {val x = token { ident }; token { char(':') }; val t = type; Pair(x, t)}
}.toTypedArray()

val Parse.grammar: Term get() = choice({
  char('\\')
  val t = tele
  token { string("->") }
  tlam(t, grammar)
}, {
  val x = some {
    choice(
      { parens { grammar }},
      { tvar( token { ident })
    })
  }
  if (x.size == 1) {
    x[0]
  } else {
    tapp(x[0], *(x.drop(1).toTypedArray()))
  }
})
