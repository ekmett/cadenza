import cadenza.Language
import cadenza.data.Closure
import cadenza.frame.CaptureLayout
import cadenza.jit.*
import cadenza.semantics.Type
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.frame.FrameSlotKind
import com.oracle.truffle.api.nodes.NodeUtil
import com.oracle.truffle.api.source.Source
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class ScopingTests {
  private fun context(backend: String) = Context.newBuilder("cadenza")
    .allowExperimentalOptions(true).option("cadenza.Backend", backend).build()

  @TestFactory fun shadowingAndRecursionRespectLexicalBindings(): List<DynamicTest> {
    val programs = listOf(
      "let x : Nat = 40 in plus (let x : Nat = 2 in x) x" to 42,
      "let x : Nat = 40 in plus (if le 1 2 then let x : Nat = 2 in x else 0) x" to 42,
      "(\\(x : Nat) -> plus (let x : Nat = 2 in x) x) 40" to 42,
      "let x : Nat = 40 in (\\(y : Nat) -> plus (let x : Nat = y in x) x) 2" to 42,
      "let x : Nat = 40 in plus ((\\(x : Nat) -> x) 2) x" to 42,
      "let x : Nat = 40 in let f : Nat -> Nat = \\(y : Nat) -> plus x y in let x : Nat = 100 in f 2" to 42,
      "let x : Nat = 40 in plus (let x : Bool = eq 1 1 in if x then 2 else 0) x" to 42,
      "(\\(x : Nat) (x : Nat) -> x) 1 42" to 42,
      "let f : Nat -> Nat = \\(n : Nat) -> if le n 0 then 42 else let m : Nat = minus n 1 in f m in f 100000" to 42,
      "let f : Nat -> Nat = \\(n : Nat) -> if le n 0 then 42 else (\\(g : Nat -> Nat) -> g (minus n 1)) f in f 10000" to 42
    )
    return listOf("ast", "bytecode").flatMap { backend -> programs.map { (program, expected) ->
      DynamicTest.dynamicTest("$backend: $program") {
        context(backend).use { context -> assertEquals(expected, context.eval("cadenza", program).asInt()) }
      }
    } }
  }

  @TestFactory fun recursiveValuesCannotBeDemandedDuringInitialization(): List<DynamicTest> {
    val programs = listOf(
      "let x : Nat = x in x",
      "let x : Nat = plus x 1 in x",
      "let x : Nat = (\\(f : Nat -> Nat) -> f 2) (\\(y : Nat) -> plus x y) in x",
      "let f : Nat -> Nat = (\\(g : Nat -> Nat) -> g) f in f 0"
    )
    return listOf("ast", "bytecode").flatMap { backend -> programs.map { program ->
      DynamicTest.dynamicTest("$backend: $program") {
        context(backend).use { context ->
          val error = assertThrows(PolyglotException::class.java) { context.eval("cadenza", program) }
          assertFalse(error.isInternalError)
          assertTrue(error.message!!.contains("recursive binding read before initialization"), error.message)
        }
      }
    } }
  }

  @TestFactory fun illTypedBindingsAndApplicationsAreGuestTypeErrors(): List<DynamicTest> {
    val programs = listOf(
      "let x : Nat = eq 1 1 in x",
      "let f : Nat -> Nat = 1 in f 2",
      "let x : Bool = 1 in if x then 2 else 3",
      "1 2",
      "missingVariable"
    )
    return listOf("ast", "bytecode").flatMap { backend -> programs.map { program ->
      DynamicTest.dynamicTest("$backend: $program") {
        context(backend).use { context ->
          val error = assertThrows(PolyglotException::class.java) { context.eval("cadenza", program) }
          assertFalse(error.isInternalError)
          assertTrue(error.isSyntaxError)
        }
      }
    } }
  }

  @Test fun capturedNaturalCellIsForcedAfterInitialization() {
    // A cell can be captured while uninitialized. Its later value must be read as Nat,
    // rather than exposed as the function-shaped thunk used by the old representation.
    context("ast").use { context ->
      context.initialize("cadenza")
      context.enter()
      try {
        val language = Language.currentLanguage()
        val source = Source.newBuilder("cadenza", "42", "capture-cell.za").build()
        val parentLayout = FrameLayout()
        val parentSlot = parentLayout.slot("x")
        val parentFrame = Truffle.getRuntime().createVirtualFrame(emptyArray(), parentLayout.build())
        val cell = Indirection()
        FrameAccess.write(parentFrame, parentSlot, cell)
        val captures = CaptureLayout(language, arrayOf(Type.Nat))
        val environment = captures.capture(parentFrame, arrayOf(parentSlot))
        val childLayout = FrameLayout()
        val childSlot = childLayout.slot("x")
        val root = ClosureRootNode(language, childLayout.build(), 0,
          arrayOf(childSlot to 0), emptyArray(), ClosureBody(Code.Var(childSlot)), source, null, captures)
        cell.value = 42
        cell.set = true
        assertEquals(42, root.callTarget.call(0L, environment))
        assertTrue(NodeUtil.verify(root))
      } finally {
        context.leave()
      }
    }
  }

  @Test fun nonrecursiveLetRetainsPrimitiveFrameStorage() {
    context("ast").use { context ->
      context.initialize("cadenza")
      context.enter()
      try {
        val closure = Language.currentLanguage().parse(Source.newBuilder("cadenza",
          "\\(x : Nat) -> let y : Nat = plus x 1 in y", "primitive-let.za").build()).call() as Closure
        assertEquals(42, closure.callTarget.call(0L, 41))
        val descriptor = closure.callTarget.rootNode.frameDescriptor
        val local = (0 until descriptor.numberOfSlots).single { descriptor.getSlotName(it) == "y" }
        assertEquals(FrameSlotKind.Int, descriptor.getSlotKind(local))
      } finally {
        context.leave()
      }
    }
  }
}
