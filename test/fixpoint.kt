import cadenza.Language
import cadenza.data.Closure
import cadenza.jit.BuiltinRootNode
import cadenza.jit.CadenzaRootNode
import cadenza.jit.ClosureRootNode
import cadenza.jit.ClosureBody
import cadenza.jit.Code
import cadenza.jit.FrameAccess
import cadenza.jit.FixNatFNodeGen
import cadenza.jit.FrameLayout
import cadenza.jit.natFF
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.nodes.NodeUtil
import com.oracle.truffle.api.source.Source
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Engine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class FixpointTests {
  @Test fun directFixedBodiesRetainSelfWithoutRecursiveEqualityOrHashing() {
    Context.create("cadenza").use { context ->
      context.initialize("cadenza")
      context.enter()
      try {
        val language = Language.currentLanguage()
        val seen = mutableListOf<Closure>()
        val layout = FrameLayout()
        val selfSlot = layout.slot("self")
        val argumentSlot = layout.slot("n")
        val body = object : Code(null) {
          override fun execute(frame: VirtualFrame): Any {
            val self = FrameAccess.read(frame, selfSlot) as Closure
            seen += self
            val argument = FrameAccess.read(frame, argumentSlot) as Int
            return if (argument == 0) 0 else
              1 + (InteropLibrary.getUncached().execute(self, argument - 1) as Int)
          }
        }
        val root = ClosureRootNode(language, layout.build(), 2,
          argPreamble = arrayOf(selfSlot to 0, argumentSlot to 1), body = ClosureBody(body),
          source = Source.newBuilder("cadenza", "0", "fixed-body.za").build())
        val function = Closure(null, emptyArray(), 2, natFF, root.callTarget)
        val builtin = FixNatFNodeGen.create()
        BuiltinRootNode(language, builtin).callTarget
        val fixed = builtin.mkFix(function)
        assertSame(function.callTarget, fixed.callTarget)
        assertEquals(8, InteropLibrary.getUncached().execute(fixed, 8))
        assertEquals(9, seen.size)
        seen.forEach { assertSame(fixed, it) }

        val sameOwner = builtin.mkFix(function)
        assertEquals(fixed, sameOwner)
        assertEquals(sameOwner, fixed)
        assertEquals(fixed.hashCode(), sameOwner.hashCode())
        val zeroArgumentCopy = InteropLibrary.getUncached().execute(fixed) as Closure
        assertEquals(fixed, zeroArgumentCopy)
        assertEquals(fixed.hashCode(), zeroArgumentCopy.hashCode())

        val secondBuiltin = FixNatFNodeGen.create()
        BuiltinRootNode(language, secondBuiltin).callTarget
        val differentOwner = secondBuiltin.mkFix(function)
        assertNotEquals(fixed, differentOwner)
        assertNotEquals(differentOwner, fixed)
        val ordinaryPartial = function.pap(arrayOf(fixed))
        assertNotEquals(fixed, ordinaryPartial)
        assertNotEquals(ordinaryPartial, fixed)
        ordinaryPartial.hashCode() // Must terminate despite containing a fixed-point closure.
        assertTrue(NodeUtil.verify(root))
      } finally {
        context.leave()
      }
    }
  }

  @Test fun recursionReusesItsImmutableSelfClosure() {
    Context.create("cadenza").use { context ->
      context.initialize("cadenza")
      context.enter()
      try {
        val language = Language.currentLanguage()
        val seen = mutableListOf<Closure>()
        val body = object : CadenzaRootNode(language, FrameLayout().build()) {
          override fun execute(frame: VirtualFrame): Any {
            val self = frame.arguments[1] as Closure
            seen += self
            val argument = frame.arguments[2] as Int
            return if (argument == 0) 0 else
              1 + (InteropLibrary.getUncached().execute(self, argument - 1) as Int)
          }
        }
        val function = Closure(null, emptyArray(), 2, natFF, body.callTarget)
        val builtin = FixNatFNodeGen.create()
        val root = BuiltinRootNode(language, builtin).callTarget.rootNode
        val fixed = builtin.mkFix(function)
        assertEquals(8, InteropLibrary.getUncached().execute(fixed, 8))
        assertEquals(9, seen.size)
        seen.forEach { self -> assertSame(fixed, self) }
        assertEquals(fixed, builtin.mkFix(function))
        assertEquals(fixed.hashCode(), builtin.mkFix(function).hashCode())
        assertTrue(NodeUtil.verify(root))
        assertTrue(NodeUtil.verify(fixed.callTarget.rootNode))
      } finally {
        context.leave()
      }
    }
  }

  @Test fun fixedFunctionsKeepSeparateCapturedValuesAfterCacheSaturation() {
    for (backend in listOf("ast", "bytecode")) {
      Context.newBuilder("cadenza").allowExperimentalOptions(true)
        .option("cadenza.Backend", backend).build().use { context ->
          val make = context.eval("cadenza",
            "\\(step : Nat) -> fixNatF (\\(self : Nat -> Nat) (n : Nat) -> " +
              "if le n 0 then 0 else plus step (self (minus n 1)))")
          val functions = (1..8).map { make.execute(it) }
          repeat(3) {
            functions.forEachIndexed { index, function ->
              assertEquals(10 * (index + 1), function.execute(10).asInt(), backend)
            }
          }
        }
    }
  }

  @Test fun alternatingTailRootsRemainStackSafe() {
    for (backend in listOf("ast", "bytecode")) {
      Context.newBuilder("cadenza").allowExperimentalOptions(true)
        .option("cadenza.Backend", backend).build().use { context ->
          val function = context.eval("cadenza",
            "fixNatF (\\(self : Nat -> Nat) (n : Nat) -> " +
              "if le 100000 n then n else " +
              "let next : Nat -> Nat = \\(x : Nat) -> self x in next (plus n 1))")
          assertEquals(100000, function.execute(0).asInt(), backend)
        }
    }
  }

  @Test fun capturedPartiallyAppliedAndUnaryBodiesKeepTheirCallingConventions() {
    val programs = listOf(
      "let offset : Nat = 7 in fixNatF (\\(self : Nat -> Nat) (n : Nat) -> " +
        "if le n 0 then offset else plus 1 (self (minus n 1))) 35",
      "fixNatF ((\\(offset : Nat) (self : Nat -> Nat) (n : Nat) -> " +
        "if le n 0 then offset else self (minus n 1)) 42) 100000",
      "fixNatF (\\(self : Nat -> Nat) -> \\(n : Nat) -> " +
        "if le n 0 then 42 else self (minus n 1)) 10000"
    )
    for (backend in listOf("ast", "bytecode")) {
      Context.newBuilder("cadenza").allowExperimentalOptions(true)
        .option("cadenza.Backend", backend).build().use { context ->
          for (program in programs) assertEquals(42, context.eval("cadenza", program).asInt(), "$backend: $program")
        }
    }
  }

  @Test fun fixedClosureCanDirectlyTargetAClonedCapturedRoot() {
    Context.create("cadenza").use { context ->
      context.initialize("cadenza")
      context.enter()
      try {
        val language = Language.currentLanguage()
        val function = language.parse(Source.newBuilder("cadenza",
          "let answer : Nat = 42 in \\(self : Nat -> Nat) (n : Nat) -> " +
            "if le n 0 then answer else self (minus n 1)", "cloned-fixed-body.za").build()).call() as Closure
        val clone = NodeUtil.cloneNode(function.callTarget.rootNode)
        val clonedFunction = Closure(function.env, function.papArgs, function.arity, function.type, clone.callTarget)
        val builtin = FixNatFNodeGen.create()
        BuiltinRootNode(language, builtin).callTarget
        val fixed = builtin.mkFix(clonedFunction)
        assertSame(clone.callTarget, fixed.callTarget)
        assertEquals(42, InteropLibrary.getUncached().execute(fixed, 100000))
        assertTrue(NodeUtil.verify(clone))
        assertTrue(NodeUtil.verify(function.callTarget.rootNode))
      } finally {
        context.leave()
      }
    }
  }

  @Test fun retainedFixedClosureUsesTheCurrentSharedEngineContext() {
    Engine.create().use { engine ->
      val firstOutput = ByteArrayOutputStream()
      val secondOutput = ByteArrayOutputStream()
      Context.newBuilder("cadenza").engine(engine).out(firstOutput).build().use { first ->
        Context.newBuilder("cadenza").engine(engine).out(secondOutput).build().use { second ->
          first.initialize("cadenza")
          second.initialize("cadenza")
          first.enter()
          val fixed = try {
            val language = Language.currentLanguage()
            val function = language.parse(Source.newBuilder("cadenza",
              "\\(self : Nat -> Nat) (n : Nat) -> if le n 0 then printId 0 " +
                "else plus (printId n) (self (minus n 1))", "shared-fixed-body.za").build()).call() as Closure
            val builtin = FixNatFNodeGen.create()
            BuiltinRootNode(language, builtin).callTarget
            builtin.mkFix(function)
          } finally {
            first.leave()
          }
          fun evaluateIn(context: Context, argument: Int): Any? {
            context.enter()
            return try {
              InteropLibrary.getUncached().execute(fixed, argument)
            } finally {
              context.leave()
            }
          }
          assertEquals(6, evaluateIn(first, 3))
          assertEquals(3, evaluateIn(second, 2))
          assertEquals(1, evaluateIn(first, 1))
          assertEquals("3\n2\n1\n0\n1\n0\n", firstOutput.toString(Charsets.UTF_8))
          assertEquals("2\n1\n0\n", secondOutput.toString(Charsets.UTF_8))
        }
      }
    }
  }
}
