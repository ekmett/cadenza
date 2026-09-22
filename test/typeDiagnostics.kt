import cadenza.jit.initialCtx
import cadenza.semantics.Type
import cadenza.syntax.Success
import cadenza.syntax.parse
import cadenza.syntax.program
import com.oracle.truffle.api.source.Source
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class TypeDiagnosticTests {
  private val nat = Type.Nat
  private val bool = Type.Bool
  private fun arrow(argument: Type, result: Type) = Type.Arr(argument, result)
  private fun infer(text: String): Type {
    val parsed = Source.newBuilder("cadenza", text, "type.za").build().parse { program }
    return (parsed as Success).value.infer(initialCtx).type
  }

  @Test fun inferredHigherOrderTypesMatchIndependentStructuralExpectations() {
    val predicate = arrow(nat, bool)
    val consumePredicate = arrow(predicate, nat)
    val examples = listOf(
      "\\(x : Nat) (x : Bool) -> if x then 1 else 0" to arrow(nat, arrow(bool, nat)),
      "\\(ignored : Nat) (condition : Bool) -> if condition then 1 else 0" to arrow(nat, arrow(bool, nat)),
      "\\(f : (Nat -> Bool) -> Nat) -> f (\\(x : Nat) -> eq x 0)" to arrow(consumePredicate, nat),
      "\\(f : Nat -> Bool) -> \\(g : Bool -> Nat) -> \\(x : Nat) -> g (f x)" to
        arrow(predicate, arrow(arrow(bool, nat), arrow(nat, nat))),
      "\\(a : Nat) -> \\(b : Bool) -> if b then (\\(x : Nat) -> a) else (\\(x : Nat) -> x)" to
        arrow(nat, arrow(bool, arrow(nat, nat))),
      "let plus : Bool -> Nat = \\(b : Bool) -> if b then 1 else 0 in \\(b : Bool) -> plus b" to arrow(bool, nat),
      "\\(x : Bool) -> let x : Nat = 7 in \\(ignored : Bool) -> x" to arrow(bool, arrow(bool, nat)),
      "\\(f : Nat -> Bool -> Nat) -> f 1" to arrow(arrow(nat, arrow(bool, nat)), arrow(bool, nat))
    )
    for ((source, expected) in examples) {
      assertEquals(expected, infer(source), source)
      // Parentheses and leading/trailing whitespace cannot change arrow associativity.
      assertEquals(expected, infer("\n ( $source ) \t"), source)
    }
    // These annotations differ only in parentheses but denote different function types.
    assertNotEquals(infer("\\(f : Nat -> Bool -> Nat) -> f"),
      infer("\\(f : (Nat -> Bool) -> Nat) -> f"))
  }

  private data class BadTerm(val before: String, val term: String, val after: String, val message: String = "type mismatch")

  @Test fun nestedTypeFailuresPointAtTheOffendingTermAfterSourcePrefixes() {
    val failures = listOf(
      BadTerm("let x : Nat = 1 in\n", "missing", " \n", "unknown variable missing"),
      BadTerm("let x : Nat = (", "eq 0 0", ") in x"),
      BadTerm("if ", "0", " then 1 else 2"),
      BadTerm("plus 1 (", "eq 0 0", ")"),
      BadTerm("if eq 0 0 then 1 else (", "eq 0 0", ")"),
      BadTerm("", "1", " 2", "not a function type"),
      BadTerm("plus 1 2 ", "3", "", "not a function type"),
      BadTerm("(\\(f : Nat -> Nat) -> f 0) (", "\\(b : Bool) -> 0", ")"),
      BadTerm("(\\(f : Nat -> Nat) -> f 0) (", "\\(n : Nat) -> eq n 0", ")"),
      BadTerm("\\(x : Nat) (x : Bool) -> plus ", "x", " 1")
    )
    val prefixes = listOf("", " \n\t", "#!/usr/bin/env cadenza\n")
    for (backend in listOf("ast", "bytecode")) {
      Context.newBuilder("cadenza").allowExperimentalOptions(true).option("cadenza.Backend", backend).build().use { context ->
        for (failure in failures) for (prefix in prefixes) {
          val source = prefix + failure.before + failure.term + failure.after
          val error = assertThrows(PolyglotException::class.java, { context.eval("cadenza", source) }, "$backend: $source")
          assertTrue(error.isSyntaxError, source)
          assertFalse(error.isInternalError, source)
          assertTrue(error.message!!.contains(failure.message), error.message)
          val section = error.sourceLocation!!
          assertEquals(prefix.length + failure.before.length, section.charIndex, "$backend: $source")
          assertEquals(failure.term, section.characters.toString(), "$backend: $source")
        }
        assertEquals(42, context.eval("cadenza", "plus 20 22").asInt(), "context survives rejected parses")
      }
    }
  }

  @Test fun staticRejectionChecksUnusedTermsBeforeAnyGuestOutputAndContextRecovers() {
    val rejected = listOf(
      "let printed : Nat = printId 7 in if eq 0 0 then 42 else eq 0 0",
      "if eq 0 0 then printId 7 else missing",
      "let unused : Nat -> Bool = \\(x : Nat) -> x in printId 7",
      "let printed : Nat = printId 7 in (\\(f : (Nat -> Bool) -> Nat) -> f (\\(x : Nat) -> x)) (\\(p : Nat -> Bool) -> 0)",
      "let printed : Nat = printId 7 in 42 @"
    )
    for (backend in listOf("ast", "bytecode")) {
      val output = ByteArrayOutputStream()
      Context.newBuilder("cadenza").allowExperimentalOptions(true).option("cadenza.Backend", backend)
        .out(output).build().use { context ->
          for (source in rejected) {
            val error = assertThrows(PolyglotException::class.java) { context.eval("cadenza", source) }
            assertTrue(error.isSyntaxError, "$backend: $source")
            assertFalse(error.isInternalError, "$backend: $source")
            assertEquals("", output.toString(Charsets.UTF_8), "$backend evaluated statically rejected source: $source")
            assertEquals(42, context.eval("cadenza", "plus 20 22").asInt())
          }
          assertEquals(11, context.eval("cadenza", "printId 11").asInt())
          assertEquals("11\n", output.toString(Charsets.UTF_8))
        }
    }
  }
}
