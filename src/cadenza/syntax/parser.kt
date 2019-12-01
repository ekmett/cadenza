package cadenza.syntax

import cadenza.semantics.*
import cadenza.semantics.Term.Companion.tlam
import cadenza.semantics.Term.Companion.tvar
import org.intelligence.parser.*


// expr = char '(' <* expr *> char ')'
//    <|> char '\' <* (Lam <$> many (var <* space) <*> expr)
//    <|> Var <$> var


val type: Parser<Type> = {choice({string("Nat"); Type.Nat })}

val ident: Parser<String> = {named("ident") {some { satisfy { it.isLetter() } }.joinToString() } }

val space: Parser<Unit> = {many { satisfy { it.isWhitespace() }}; Unit }

fun <A> Parse.parens(f: Parse.() -> A): A {
    char('(')
    space()
    val x = f()
    char(')')
    space()
    return x
}

val tele: Parser<Array<Pair<Name,Type>>> = { some {
    parens {val x = ident(); space(); char(':'); space(); val t = type(); Pair(x, t)}
}.toTypedArray() };

fun Parse.grammar(): Term {
    return choice({
        char('\\')
        val t = tele()
        string("->")
        space()
        tlam(t, grammar())
    }, {
        tvar(ident())
    }, {
        parens { grammar() }
    })
}
