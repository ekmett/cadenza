import cadenza.Language
import cadenza.data.Closure
import cadenza.jit.GenericInteropApplyRootNode
import cadenza.semantics.Type
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.nodes.NodeUtil
import com.oracle.truffle.api.source.Source
import org.graalvm.polyglot.Context
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PartialApplicationTests {
  private fun context(backend: String) = Context.newBuilder("cadenza")
    .allowExperimentalOptions(true).option("cadenza.Backend", backend).build()

  private fun parse(program: String): Closure = Language.currentLanguage().parse(
    Source.newBuilder("cadenza", program, "partial.za").build()).call() as Closure

  @Test fun rangedPartialApplicationKeepsEnvironmentEarlierArgumentsAndSnapshot() {
    for (backend in listOf("ast", "bytecode")) {
      context(backend).use { context ->
        context.initialize("cadenza")
        context.enter()
        try {
          val function = parse("(\\(base : Nat) -> \\(a : Nat) (b : Nat) (c : Nat) (d : Nat) -> " +
            "plus base (plus (mult 1000 a) (plus (mult 100 b) (plus (mult 10 c) d)))) 10000")
          val earlier = function.pap(arrayOf(1))
          val arguments = arrayOf<Any?>(999, 2, 3, 888)
          val partial = earlier.pap(arguments, 1, 2)
          arguments[1] = 20
          arguments[2] = 30
          assertEquals(1, partial.arity)
          assertEquals(Type.Arr(Type.Nat, Type.Nat), partial.type)
          assertSame(function.callTarget, partial.callTarget)
          assertSame(function.env, partial.env)
          assertEquals(11234, InteropLibrary.getUncached().execute(partial, 4))
          assertEquals(11234, InteropLibrary.getUncached().execute(earlier, 2, 3, 4))
          val unchanged = partial.pap(arguments, arguments.size, 0)
          assertEquals(partial, unchanged)
          assertEquals(11234, InteropLibrary.getUncached().execute(unchanged, 4))
        } finally {
          context.leave()
        }
      }
    }
  }

  @Test fun genericUnderapplicationWorksAtZeroAndNonzeroOffsets() {
    for (backend in listOf("ast", "bytecode")) {
      context(backend).use { context ->
        context.initialize("cadenza")
        context.enter()
        try {
          val generic = GenericInteropApplyRootNode(Language.currentLanguage()).callTarget
          val flat = parse("\\(a : Nat) (b : Nat) (c : Nat) (d : Nat) -> " +
            "plus (mult 1000 a) (plus (mult 100 b) (plus (mult 10 c) d))")
          val first = generic.call(flat, arrayOf<Any?>(1, 2)) as Closure
          assertEquals(1234, InteropLibrary.getUncached().execute(first, 3, 4))
          val curried = parse("\\(a : Nat) -> \\(b : Nat) (c : Nat) (d : Nat) -> " +
            "plus (mult 1000 a) (plus (mult 100 b) (plus (mult 10 c) d))")
          val arguments = arrayOf<Any?>(1, 2, 3)
          val second = generic.call(curried, arguments) as Closure
          arguments.fill(9)
          assertEquals(1, second.arity)
          assertEquals(1234, InteropLibrary.getUncached().execute(second, 4))
          assertTrue(NodeUtil.verify(generic.rootNode))
        } finally {
          context.leave()
        }
      }
    }
  }

  @Test fun saturatedDispatchCachePreservesRemainingArgumentOrder() {
    for (backend in listOf("ast", "bytecode")) {
      context(backend).use { context ->
        val apply = context.eval("cadenza", "\\(f : Nat -> Nat -> Nat -> Nat -> Nat -> Nat) -> f 1 2 3")
        val functions = (0..7).map { offset ->
          val binders = if (offset % 2 == 0)
            "\\(a : Nat) -> \\(b : Nat) (c : Nat) (d : Nat) (e : Nat)" else
            "\\(a : Nat) (b : Nat) -> \\(c : Nat) (d : Nat) (e : Nat)"
          context.eval("cadenza", "$binders -> plus $offset " +
            "(plus (mult 10000 a) (plus (mult 1000 b) (plus (mult 100 c) (plus (mult 10 d) e))))")
        }
        repeat(3) {
          functions.forEachIndexed { offset, function ->
            val partial = apply.execute(function)
            assertEquals(12345 + offset, partial.execute(4, 5).asInt(), backend)
          }
        }
      }
    }
  }
}
