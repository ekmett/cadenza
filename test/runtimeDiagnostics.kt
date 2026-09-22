import cadenza.Language
import cadenza.RuntimeError
import cadenza.data.Closure
import cadenza.jit.CallUtils
import cadenza.jit.CadenzaRootNode
import cadenza.jit.FrameLayout
import cadenza.jit.TailCallException
import cadenza.jit.TailCallLoop
import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.DirectCallNode
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Source
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RuntimeDiagnosticTests {
  private fun context(backend: String) = Context.newBuilder("cadenza").allowExperimentalOptions(true)
    .option("cadenza.Backend", backend).build()

  private fun checkLocation(error: PolyglotException, text: String, name: String, failing: String) {
    assertTrue(error.isGuestException)
    assertFalse(error.isInternalError)
    assertFalse(error.isSyntaxError)
    val section = error.sourceLocation
    assertNotNull(section, "Missing runtime location: $name\n$text\n${error.polyglotStackTrace.joinToString("\n")}")
    val index = text.indexOf(failing)
    assertTrue(index >= 0)
    assertEquals(name, section!!.source.name)
    assertEquals(index, section.charIndex, text)
    assertEquals(failing, section.characters.toString(), text)
    assertEquals(text.take(index).count { it == '\n' } + 1, section.startLine, text)
    assertEquals(index - text.lastIndexOf('\n', index - 1), section.startColumn, text)
  }

  @Test fun runtimeLocationsIdentifyOperationsAndReadsAfterSourcePrefixes() {
    val cases = listOf(
      "div 1 0" to "div 1 0",
      "mod 2147483648 0" to "mod 2147483648 0",
      "plus 1 (div 1 (minus 2147483648 2147483648))" to "div 1 (minus 2147483648 2147483648)",
      "(div 1) 0" to "(div 1) 0",
      "let quotient : Nat -> Nat -> Nat = div in quotient 1 0" to "quotient 1 0",
      "let value : Nat = value in value" to "value in value",
      "let value : Nat = (\\(ignored : Nat) -> value) 0 in value" to "value) 0 in value"
    )
    val prefixes = listOf("", " \n\t", "#!/usr/bin/env cadenza\r\n  ")
    for (backend in listOf("ast", "bytecode")) context(backend).use { context ->
      for ((body, marker) in cases) for (prefix in prefixes) {
        val text = prefix + body
        val name = "$backend-runtime.za"
        val error = assertThrows(PolyglotException::class.java) {
          context.eval(Source.newBuilder("cadenza", text, name).build())
        }
        // The longer marker disambiguates the failing variable from its declaration.
        if (marker.startsWith("value")) {
          val section = error.sourceLocation
          assertNotNull(section, text)
          assertEquals(text.indexOf(marker), section!!.charIndex, text)
          assertEquals("value", section.characters.toString(), text)
          assertTrue(error.message!!.contains("recursive binding read before initialization"))
        } else {
          checkLocation(error, text, name, marker)
          assertTrue(error.message!!.contains(if (body.startsWith("mod")) "modulo by zero" else "division by zero"))
        }
        assertEquals(42, context.eval("cadenza", "plus 20 22").asInt())
      }
    }
  }

  @Test fun nestedCrossSourceCallsKeepTheCalleeFailureAndTheCallerFrame() {
    val callerText = " \n\\(f : Nat -> Nat) -> plus 1 (f 0)"
    for (backend in listOf("ast", "bytecode")) for (operation in listOf("div 1 n", "(div 1) n")) context(backend).use { context ->
      val calleeText = "#!/usr/bin/env cadenza\n\\(n : Nat) -> $operation"
      val callee = context.eval(Source.newBuilder("cadenza", calleeText, "callee.za").build())
      val caller = context.eval(Source.newBuilder("cadenza", callerText, "caller.za").build())
      val error = assertThrows(PolyglotException::class.java) { caller.execute(callee) }
      checkLocation(error, calleeText, "callee.za", operation)
      val guestLocations = error.polyglotStackTrace.filter { it.isGuestFrame }.mapNotNull { it.sourceLocation }
      // A first-class builtin tail call may eliminate its caller's physical frame.
      if (operation == "div 1 n") assertTrue(guestLocations.any { it.source.name == "callee.za" },
        error.polyglotStackTrace.joinToString("\n"))
      assertTrue(guestLocations.any { it.source.name == "caller.za" && it.characters.toString() == "f 0" },
        error.polyglotStackTrace.joinToString("\n"))
      assertEquals(1, callee.execute(1).asInt(), "$backend function survives runtime failure")
      val safe = context.eval("cadenza", "\\(n : Nat) -> plus n 41")
      assertEquals(42, caller.execute(safe).asInt())
    }
  }

  @Test fun recursiveFailuresKeepTheirLeafLocationAndReusableFunction() {
    for (backend in listOf("ast", "bytecode")) context(backend).use { context ->
      for (tail in listOf(true, false)) {
        val recurse = if (tail) "loop (minus n 1) denominator" else "plus 1 (loop (minus n 1) denominator)"
        val text = "#!/usr/bin/env cadenza\nlet loop : Nat -> Nat -> Nat = \\(n : Nat) (denominator : Nat) -> " +
          "if eq n 0 then (div 2147483648 denominator) else ($recurse) in loop"
        val name = "$backend-recursion-$tail.za"
        val function = context.eval(Source.newBuilder("cadenza", text, name).build())
        val n = if (tail) 1000 else 20
        val error = assertThrows(PolyglotException::class.java) { function.execute(n, 0) }
        checkLocation(error, text, name, "div 2147483648 denominator")
        assertEquals(1073741824 + if (tail) 0 else n, function.execute(n, 2).asInt())
      }
    }
  }

  @Test fun runtimeSitesStayPreciseWhenTheSameFunctionMovesPastItsUncachedPhase() {
    val text = "\n\\(mode : Nat) -> if eq mode 0 then (mod 2147483648 0) else " +
      "if eq mode 1 then (let delayed : Nat = delayed in delayed) else 42"
    for (backend in listOf("ast", "bytecode")) context(backend).use { context ->
      val name = "$backend-phases.za"
      val function = context.eval(Source.newBuilder("cadenza", text, name).build())
      for (phase in 0..1) {
        if (phase == 1) repeat(32) { assertEquals(42, function.execute(2).asInt()) }
        val arithmetic = assertThrows(PolyglotException::class.java) { function.execute(0) }
        checkLocation(arithmetic, text, name, "mod 2147483648 0")
        val recursive = assertThrows(PolyglotException::class.java) { function.execute(1) }
        val section = recursive.sourceLocation!!
        assertEquals(text.indexOf("delayed in delayed"), section.charIndex, "$backend phase=$phase")
        assertEquals("delayed", section.characters.toString())
        val leaf = recursive.polyglotStackTrace.first { it.isGuestFrame }.sourceLocation
        assertNotNull(leaf, "$backend phase=$phase must locate the failing guest stack frame")
        assertEquals(name, leaf!!.source.name)
        assertEquals("delayed", leaf.characters.toString())
      }
    }
  }

  private class SaturatedCaller(language: Language, target: RootCallTarget) :
    CadenzaRootNode(language, FrameLayout().build()) {
    @Child private var call = DirectCallNode.create(target)
    @Child private var trampoline = TailCallLoop()
    var bounces = 0
    override fun execute(frame: VirtualFrame): Any? = try {
      CallUtils.callDirect(call, frame.arguments)
    } catch (tail: TailCallException) {
      bounces++
      trampoline.execute(tail)
    }
  }

  @Test fun aForcedBuiltinTailBounceKeepsItsOriginalApplicationSource() {
    context("ast").use { context ->
      context.initialize("cadenza")
      context.enter()
      try {
        val text = "#!/usr/bin/env cadenza\n\\(n : Nat) -> (div 1) n"
        val source = com.oracle.truffle.api.source.Source.newBuilder("cadenza", text, "bounce.za").build()
        val language = Language.currentLanguage()
        val closure = language.parse(source).call() as Closure
        val driver = SaturatedCaller(language, closure.callTarget)
        // Saturating the existing bloom mask forces the bounce regardless of target hashes.
        val error = assertThrows(RuntimeError::class.java) { driver.callTarget.call(-1L, 0) }
        assertEquals(1, driver.bounces)
        val section = error.encapsulatingSourceSection!!
        assertSame(source, section.source)
        assertEquals(text.indexOf("(div 1) n"), section.charIndex)
        assertEquals("(div 1) n", section.characters.toString())
        assertEquals(0, driver.callTarget.call(-1L, 2))
        assertEquals(2, driver.bounces)
      } finally { context.leave() }
    }
  }

}
