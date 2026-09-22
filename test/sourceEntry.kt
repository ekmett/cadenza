import cadenza.LANGUAGE_MIME_TYPE
import cadenza.Language
import cadenza.Loc
import cadenza.data.Closure
import cadenza.jit.Code
import cadenza.jit.ClosureRootNode
import cadenza.semantics.Term
import cadenza.syntax.Success
import cadenza.syntax.parse
import cadenza.syntax.program
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.frame.MaterializedFrame
import com.oracle.truffle.api.instrumentation.TruffleInstrument
import com.oracle.truffle.api.nodes.ExecutableNode
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.source.Source
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Engine
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.io.IOAccess
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** Test-only instrument exercises the public tool-facing inline parsing contract. */
@TruffleInstrument.Registration(id = "cadenza-source-entry-test", services = [SourceEntryInlineProbe::class])
class SourceEntryTestInstrument : TruffleInstrument() {
  override fun onCreate(env: Env) { env.registerService(SourceEntryInlineProbe(env)) }
}

class SourceEntryInlineProbe(private val env: TruffleInstrument.Env) {
  fun parse(source: Source, location: Node, frame: MaterializedFrame): ExecutableNode? =
    env.parseInline(source, location, frame)
}

class SourceEntryTests {
  @TempDir lateinit var directory: Path

  @TestFactory fun scriptsKeepTheirFirstLineAndEvaluateOnBothBackends(): List<DynamicTest> {
    val headers = listOf(
      "#!/usr/bin/env cadenza\n",
      "#!/usr/bin/env -S cadenza --experimental-options\n",
      "#! /usr/local/bin/cadenza\n",
      "#!/usr/bin/cadenza\r\n"
    )
    return listOf("ast", "bytecode").flatMap { backend -> headers.map { header ->
      DynamicTest.dynamicTest("$backend: ${header.trim()}") {
        Context.newBuilder("cadenza").allowExperimentalOptions(true)
          .option("cadenza.Backend", backend).build().use { context ->
            assertEquals(42, context.eval("cadenza", header + "plus 20 22").asInt())
            val error = assertThrows(PolyglotException::class.java) {
              context.eval("cadenza", header + "42 @")
            }
            assertTrue(error.isSyntaxError)
            assertFalse(error.isInternalError)
            assertEquals(header.length + 3, error.sourceLocation!!.charIndex)
            assertEquals(2, error.sourceLocation!!.startLine)
            assertEquals(4, error.sourceLocation!!.startColumn)
          }
      }
    } }
  }

  @Test fun shebangSkippingPreservesTermAndInstrumentedNodeOffsets() {
    val header = "#!/usr/bin/env cadenza\n"
    val body = "\\(x : Nat) -> plus x 1"
    val source = Source.newBuilder("cadenza", header + body, "script.za").build()
    val term = (source.parse { program } as Success).value as Term.TLam
    assertEquals(Loc.Range(header.length, body.length), term.loc)
    Context.create("cadenza").use { context ->
      context.initialize("cadenza")
      context.enter()
      try {
        val closure = Language.currentLanguage().parse(source).call() as Closure
        val section = closure.callTarget.rootNode.sourceSection!!
        assertSame(source, section.source)
        assertEquals(header.length, section.charIndex)
        assertEquals(2, section.startLine)
        assertEquals(body, section.characters.toString())
        assertEquals(42, closure.callTarget.call(0L, 41))
      } finally { context.leave() }
    }
  }

  @Test fun shebangsAreOnlyAllowedAtOffsetZeroAndNeedABody() {
    Context.create("cadenza").use { context ->
      for (program in listOf(" #!/usr/bin/env cadenza\n42", "\n#!/usr/bin/env cadenza\n42",
        "42\n#!/usr/bin/env cadenza\n", "#!/usr/bin/env cadenza")) {
        val error = assertThrows(PolyglotException::class.java) { context.eval("cadenza", program) }
        assertTrue(error.isSyntaxError, program)
        assertFalse(error.isInternalError, program)
        assertNotNull(error.sourceLocation)
      }
    }
  }

  @Test fun detectorUsesExactExtensionsAndCompleteInterpreterNames() {
    Context.newBuilder("cadenza").allowIO(IOAccess.ALL).build().use { context ->
      context.initialize("cadenza")
      context.enter()
      try {
        val cases = listOf(
          Triple("program.za", "42", true),
          Triple("fooza", "42", false),
          Triple("za", "42", false),
          Triple("program.za.bak", "42", false),
          Triple("env-script", "#!/usr/bin/env cadenza\n42", true),
          Triple("env-options-script", "#!/usr/bin/env -S cadenza --experimental-options\n42", true),
          Triple("absolute-script", "#!/opt/cadenza/bin/cadenza\n42", true),
          Triple("root-script", "#!/cadenza\n42", true),
          Triple("interpreter-prefix", "#!/usr/bin/cadenzaplus\n42", false),
          Triple("env-prefix", "#!/usr/bin/env cadenza-extra\n42", false),
          Triple("other-interpreter", "#!/usr/bin/env python\n42", false),
          Triple("late-header", "\n#!/usr/bin/env cadenza\n42", false)
        )
        val detector = Language.Detector()
        for ((name, contents, recognized) in cases) {
          val path = directory.resolve(name)
          Files.writeString(path, contents)
          val file = Language.currentContext().env.getPublicTruffleFile(path.toString())
          val expected = if (recognized) LANGUAGE_MIME_TYPE else null
          assertEquals(expected, detector.findMimeType(file), name)
          // Also exercise registered detector discovery instead of only its direct method.
          assertEquals(if (recognized) "cadenza" else null,
            org.graalvm.polyglot.Source.findLanguage(path.toFile()), name)
          if (recognized) assertEquals(42, context.eval(org.graalvm.polyglot.Source.newBuilder("cadenza", path.toFile()).build()).asInt())
        }
      } finally { context.leave() }
    }
  }

  @Test fun unsupportedInlineParsingReturnsNullInsteadOfThrowingAStubError() {
    Engine.create().use { engine ->
      val probe = engine.instruments.getValue("cadenza-source-entry-test").lookup(SourceEntryInlineProbe::class.java)
      Context.newBuilder("cadenza").engine(engine).build().use { context ->
        context.initialize("cadenza")
        context.enter()
        try {
          val source = Source.newBuilder("cadenza", "\\(x : Nat) -> plus x 1", "inline-host.za").build()
          val closure = Language.currentLanguage().parse(source).call() as Closure
          val root = closure.callTarget.rootNode as ClosureRootNode
          var location: Node? = null
          root.accept { node -> if (node is Code.Var) location = node; true }
          assertNotNull(location)
          val frame = Truffle.getRuntime().createVirtualFrame(arrayOf(0L, 41), root.frameDescriptor)
          root.buildFrame(frame.arguments, frame)
          val inline = Source.newBuilder("cadenza", "x", "inline.za").build()
          assertNull(probe.parse(inline, location!!, frame.materialize()))
        } finally { context.leave() }
      }
    }
  }
}
