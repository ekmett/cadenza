import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

/** Observable traces catch duplicated or reordered work even when the answer is unchanged. */
class EvaluationOrderTests {
  private fun withBackends(action: (String, Context, ByteArrayOutputStream) -> Unit) {
    for (backend in listOf("ast", "bytecode")) {
      val output = ByteArrayOutputStream()
      Context.newBuilder("cadenza").allowExperimentalOptions(true)
        .option("cadenza.Backend", backend).out(output).build().use {
          action(backend, it, output)
        }
    }
  }

  @Test fun applicationEvaluatesItsFunctionThenArgumentsBeforeEnteringTheBody() = withBackends { backend, context, output ->
    val program = """
      (let marker : Nat = printId 10 in
        \(x : Nat) -> let entered : Nat = printId 30 in
          \(y : Nat) -> plus x y)
        (printId 20) (printId 22)
    """.trimIndent()
    assertEquals(42, context.eval("cadenza", program).asInt(), backend)
    assertEquals("10\n20\n22\n30\n", output.toString(Charsets.UTF_8), backend)
    output.reset()
    // Nested applications have an intervening call; flattening them would move the effect.
    val nested = """
      ((let marker : Nat = printId 10 in
        \(x : Nat) -> let entered : Nat = printId 30 in
          \(y : Nat) -> plus x y) (printId 20)) (printId 22)
    """.trimIndent()
    assertEquals(42, context.eval("cadenza", nested).asInt(), backend)
    assertEquals("10\n20\n30\n22\n", output.toString(Charsets.UTF_8), backend)
  }

  @Test fun partialApplicationRunsArgumentsOnceAndDelaysTheBody() = withBackends { backend, context, output ->
    val partial = context.eval("cadenza", """
      (\(x : Nat) (y : Nat) -> plus (printId x) (printId y)) (printId 20)
    """.trimIndent())
    assertTrue(partial.canExecute(), backend)
    assertEquals("20\n", output.toString(Charsets.UTF_8), backend)
    output.reset()
    assertEquals(42, partial.execute(22).asInt(), backend)
    assertEquals(43, partial.execute(23).asInt(), backend)
    assertEquals("20\n22\n20\n23\n", output.toString(Charsets.UTF_8), backend)
  }

  @Test fun conditionalOnlyRunsTheSelectedBranchAcrossRepeatedCalls() = withBackends { backend, context, output ->
    val select = context.eval("cadenza", """
      \(n : Nat) -> if eq (printId n) 0 then printId 42
        else if eq n 1 then printId 43 else div (printId 99) 0
    """.trimIndent())
    for (argument in listOf(0, 1, 0, 1)) {
      output.reset()
      assertEquals(42 + argument, select.execute(argument).asInt(), backend)
      assertEquals("$argument\n${42 + argument}\n", output.toString(Charsets.UTF_8), backend)
    }
    output.reset()
    val failure = assertThrows(PolyglotException::class.java) { select.execute(2) }
    assertTrue(failure.isGuestException, backend)
    assertFalse(failure.isInternalError, backend)
    assertEquals("2\n99\n", output.toString(Charsets.UTF_8), backend)
    output.reset()
    assertEquals(42, select.execute(0).asInt(), backend)
    assertEquals("0\n42\n", output.toString(Charsets.UTF_8), backend)
  }

  @Test fun argumentFailurePreventsLaterArgumentsAndTheBodyAndLeavesTheContextUsable() = withBackends { backend, context, output ->
    val failure = assertThrows(PolyglotException::class.java) {
      context.eval("cadenza", """
        (\(x : Nat) (y : Nat) -> printId (plus x y))
          (div (printId 7) 0) (printId 99)
      """.trimIndent())
    }
    assertTrue(failure.isGuestException, backend)
    assertFalse(failure.isInternalError, backend)
    assertEquals("7\n", output.toString(Charsets.UTF_8), backend)
    output.reset()
    assertEquals(42, context.eval("cadenza", "printId 42").asInt(), backend)
    assertEquals("42\n", output.toString(Charsets.UTF_8), backend)
  }

  @Test fun tailCallsPreserveEffectsAndEachIterationCapturesItsOwnValue() = withBackends { backend, context, output ->
    val run = context.eval("cadenza", """
      \(limit : Nat) ->
        let go : Nat -> (Nat -> Nat) -> Nat = \(n : Nat) (previous : Nat -> Nat) ->
          if eq n limit then previous 0 else
            let observed : Nat = printId (previous 0) in
            go (plus n 1) (\(ignored : Nat) -> n)
        in go 0 (\(ignored : Nat) -> 99)
    """.trimIndent())
    for (limit in listOf(4, 2, 5)) {
      output.reset()
      assertEquals(limit - 1, run.execute(limit).asInt(), backend)
      val expectedTrace = (listOf(99) + (0 until limit - 1)).joinToString("\n", postfix = "\n")
      assertEquals(expectedTrace, output.toString(Charsets.UTF_8), backend)
    }
  }
}
