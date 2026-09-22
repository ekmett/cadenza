import cadenza.Language
import cadenza.data.Closure
import cadenza.jit.BuiltinRootNode
import com.oracle.truffle.api.nodes.NodeUtil
import com.oracle.truffle.api.source.Source
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Engine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CallTests {
  @Test fun hostCallsUseGuestConvention() {
    Context.create("cadenza").use { context ->
      assertEquals(42, context.eval("cadenza", "\\(x : Nat) -> plus x 1").execute(41).asInt())
      val captured = context.eval("cadenza", "\\(x : Nat) -> \\(y : Nat) -> plus x y").execute(40)
      assertEquals(42, captured.execute(2).asInt())
      assertEquals(42, context.eval("cadenza", "\\(x : Nat) -> \\(y : Nat) -> plus x y").execute(40, 2).asInt())
    }
  }

  @Test fun builtinPartialApplicationsWorkFromBothSides() {
    Context.create("cadenza").use { context ->
      assertEquals(42, context.eval("cadenza", "plus 40").execute(2).asInt())
      assertEquals(42, context.eval("cadenza", "(plus 40) 2").asInt())
      val plus = context.eval("cadenza", "plus")
      assertEquals(42, plus.execute(40).execute(2).asInt())
      assertEquals(42, plus.execute().execute(40, 2).asInt())
    }
  }

  @Test fun hostEntryCatchesTailCallsAndLetBodiesAreTailPositions() {
    Context.create("cadenza").use { context ->
      val counter = context.eval("cadenza",
        "let count : Nat -> Nat = \\(x : Nat) -> if le 100000 x then x else let y : Nat = plus x 1 in count y in count")
      assertEquals(100000, counter.execute(0).asInt())
      val fixed = context.eval("cadenza", "fixNatF (\\(f : Nat -> Nat) (x : Nat) -> if le 100000 x then x else f (plus x 1))")
      assertEquals(100000, fixed.execute(0).asInt())
      // fixNatF in a non-tail expression must return to the surrounding addition.
      assertEquals(43, context.eval("cadenza", "plus (fixNatF (\\(f : Nat -> Nat) (x : Nat) -> x) 42) 1").asInt())
    }
  }

  @Test fun fixedPointCacheHasAnUnboundedCorrectFallback() {
    Context.create("cadenza").use { context ->
      val fix = context.eval("cadenza", "fixNatF")
      val functions = (1..8).map { increment ->
        context.eval("cadenza", "\\(self : Nat -> Nat) (x : Nat) -> plus x $increment")
      }
      repeat(2) {
        functions.forEachIndexed { index, function ->
          assertEquals(41 + index, fix.execute(function, 40).asInt())
        }
      }
    }
  }

  @Test fun rootsOwnEachNodeOnceAndClonesWork() {
    Context.create("cadenza").use { context ->
      context.initialize("cadenza")
      context.enter()
      try {
        val language = Language.currentLanguage()
        val target = language.parse(Source.newBuilder("cadenza", "\\(x : Nat) -> plus (plus x 1) 1", "ownership.za").build())
        val closure = target.call() as Closure
        assertTrue(NodeUtil.verify(closure.callTarget.rootNode))
        val clone = NodeUtil.cloneNode(closure.callTarget.rootNode)
        assertTrue(NodeUtil.verify(clone))
        assertEquals(42, clone.callTarget.call(0L, 40))
        val first = language.parse(Source.newBuilder("cadenza", "plus", "first.za").build()).call() as Closure
        val second = language.parse(Source.newBuilder("cadenza", "plus", "second.za").build()).call() as Closure
        assertNotSame((first.callTarget.rootNode as BuiltinRootNode).builtin,
          (second.callTarget.rootNode as BuiltinRootNode).builtin)
        assertTrue(NodeUtil.verify(first.callTarget.rootNode))
        assertTrue(NodeUtil.verify(second.callTarget.rootNode))
      } finally { context.leave() }
    }
  }

  @Test fun aSharedEngineDoesNotShareBuiltinNodesAcrossContexts() {
    Engine.create().use { engine ->
      repeat(3) {
        Context.newBuilder("cadenza").engine(engine).build().use { context ->
          assertEquals(42, context.eval("cadenza", "plus 40").execute(2).asInt())
        }
      }
    }
  }
}
