package cadenza.syntax

import cadenza.semantics.Name
import cadenza.semantics.Term
import cadenza.semantics.Term.Companion.tlam
import cadenza.semantics.Term.Companion.tvar
import cadenza.semantics.Type
import org.intelligence.parser.*

val Parse.type: Type get() = choice({token { string("Nat") }; Type.Nat })
val Parse.ident: String get() = named("ident") {some { satisfy { it.isLetter() } }.joinToString() }
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

val Parse.grammar: Term get() =
  choice({
    char('\\')
    val t = tele
    token { string("->") }
    tlam(t, grammar)
  }, {
    tvar(ident)
  }, {
    parens { grammar }
  })