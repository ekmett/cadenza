import cadenza.Language
import cadenza.data.Closure
import cadenza.jit.BuiltinRootNode
import cadenza.jit.CadenzaRootNode
import cadenza.jit.FixNatFNodeGen
import cadenza.jit.FrameLayout
import cadenza.jit.natFF
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.nodes.NodeUtil
import org.graalvm.polyglot.Context
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FixpointTests {
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
}
