import cadenza.Language
import cadenza.data.Closure
import cadenza.jit.*
import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.frame.MaterializedFrame
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.instrumentation.ExecutionEventNode
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory
import com.oracle.truffle.api.instrumentation.Instrumenter
import com.oracle.truffle.api.instrumentation.SourceSectionFilter
import com.oracle.truffle.api.instrumentation.StandardTags
import com.oracle.truffle.api.nodes.DirectCallNode
import com.oracle.truffle.api.nodes.NodeUtil
import com.oracle.truffle.api.nodes.RootNode
import com.oracle.truffle.api.source.Source
import org.graalvm.polyglot.Context
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.IdentityHashMap

class SplittingTests {
  private class Driver(language: Language, target: RootCallTarget) :
    CadenzaRootNode(language, FrameLayout().build()) {
    @Child var direct: DirectCallNode = DirectCallNode.create(target)
    @Child private var trampoline = TailCallLoop()
    var escaped = 0

    override fun execute(frame: VirtualFrame): Any? = try {
      CallUtils.callDirect(direct, frame.arguments)
    } catch (tail: TailCallException) {
      escaped++
      trampoline.execute(tail)
    }

    fun split(): ClosureRootNode {
      callTarget // Adopt the call node before requesting a real runtime split.
      assertTrue(direct.isCallTargetCloningAllowed)
      assertTrue(direct.cloneCallTarget())
      assertTrue(direct.isCallTargetCloned)
      return direct.currentRootNode as ClosureRootNode
    }
  }

  private fun withLanguage(action: (Language) -> Unit) {
    Context.create("cadenza").use { context ->
      context.initialize("cadenza")
      context.enter()
      try { action(Language.currentLanguage()) } finally { context.leave() }
    }
  }

  private fun source(text: String) = Source.newBuilder("cadenza", text, "split.za").build()

  private fun arguments(closure: Closure, argument: Int, mask: Long = 0L): Array<Any?> {
    val prefix = if (closure.env != null) arrayOf<Any?>(mask, closure.env) else arrayOf<Any?>(mask)
    return cadenza.data.append(cadenza.data.append(prefix, closure.papArgs), arrayOf<Any?>(argument))
  }

  private class Observation(root: ClosureRootNode) : AutoCloseable {
    val frames: MutableSet<MaterializedFrame> = Collections.newSetFromMap(IdentityHashMap())
    val roots: MutableSet<RootNode> = Collections.newSetFromMap(IdentityHashMap())
    var visits = 0
    private val section = root.sourceSection!!
    private val binding = Language.currentContext().env.lookup(Instrumenter::class.java)
      .attachExecutionEventFactory(
        SourceSectionFilter.newBuilder().sourceIs(section.source)
          .tagIs(StandardTags.RootBodyTag::class.java).build(),
        ExecutionEventNodeFactory { event ->
          val instrumented = event.instrumentedNode.rootNode
          val currentSection = instrumented.sourceSection
          val watched = currentSection != null && currentSection.charIndex == section.charIndex &&
            currentSection.charLength == section.charLength
          object : ExecutionEventNode() {
            override fun onEnter(frame: VirtualFrame) {
              if (watched) {
                visits++
                frames += frame.materialize()
                roots += instrumented
              }
            }
          }
        })

    override fun close() = binding.dispose()
  }

  @Test fun forcedRuntimeSplitKeepsRecursionInsideItsOwnFrame() = withLanguage { language ->
    val closure = language.parse(source(
      "let count : Nat -> Nat = \\(n : Nat) -> if le n 0 then 42 else count (minus n 1) in count"
    )).call() as Closure
    val original = closure.callTarget.rootNode as ClosureRootNode
    val driver = Driver(language, closure.callTarget)
    val split = driver.split()
    assertNotSame(original, split)
    assertEquals(original.mask, split.mask)
    Observation(original).use { observation ->
      assertEquals(42, driver.callTarget.call(*arguments(closure, 100)))
      assertEquals(0, driver.escaped, "split self calls should not fall back to the outer trampoline")
      assertEquals(101, observation.visits)
      assertEquals(1, observation.frames.size, "self-tail iterations should reuse the split root's frame")
      assertEquals(setOf(split), observation.roots)
    }
    assertTrue(NodeUtil.verify(driver))
    assertTrue(NodeUtil.verify(split))
  }

  @Test fun splitSelfCallsRefreshCapturedValuesAndPartialArguments() = withLanguage { language ->
    val closure = language.parse(source(
      "let make : Nat -> (Nat -> Nat) -> Nat -> Nat = " +
        "\\(offset : Nat) -> \\(next : Nat -> Nat) (n : Nat) -> if le n 0 then offset else next (minus n 1) in " +
        "let first : Nat -> Nat = " +
        "let second : Nat -> Nat = \\(n : Nat) -> make 99 first n in make 42 second in first"
    )).call() as Closure
    assertNotNull(closure.env)
    assertEquals(1, closure.papArgs.size)
    val original = closure.callTarget.rootNode as ClosureRootNode
    val driver = Driver(language, closure.callTarget)
    val split = driver.split()
    Observation(original).use { observation ->
      assertEquals(42, driver.callTarget.call(*arguments(closure, 100)))
      assertEquals(99, driver.callTarget.call(*arguments(closure, 101)))
      assertEquals(0, driver.escaped)
      assertEquals(203, observation.visits)
      assertEquals(2, observation.frames.size, "each entry should retain one frame across changing environments")
      assertEquals(setOf(split), observation.roots)
    }
    assertTrue(NodeUtil.verify(split))
  }

  @Test fun distinctBodiesStillEscapeTheSplitSelfLoopOnBloomCollisions() = withLanguage { language ->
    val closure = language.parse(source(
      "let finish : Nat -> Nat = \\(x : Nat) -> plus x 1 in " +
        "let count : Nat -> Nat = \\(n : Nat) -> if le n 0 then finish 41 else count (minus n 1) in count"
    )).call() as Closure
    val original = closure.callTarget.rootNode as ClosureRootNode
    val driver = Driver(language, closure.callTarget)
    val split = driver.split()
    Observation(original).use { observation ->
      // Deliberately saturate the bloom filter so the final call to a different body
      // must throw instead of being unrolled as a direct call.
      assertEquals(42, driver.callTarget.call(*arguments(closure, 100, -1L)))
      assertEquals(1, driver.escaped, "the final call targets a different logical body")
      assertEquals(101, observation.visits)
      assertEquals(1, observation.frames.size)
      assertEquals(setOf(split), observation.roots)
    }
  }

  @Test fun repeatedCloningPreservesBodyIdentityAndStackSafety() = withLanguage { language ->
    val closure = language.parse(source(
      "let count : Nat -> Nat = \\(n : Nat) -> if le n 0 then 42 else count (minus n 1) in count"
    )).call() as Closure
    val first = NodeUtil.cloneNode(closure.callTarget.rootNode)
    val second = NodeUtil.cloneNode(first)
    assertEquals(42, second.callTarget.call(*arguments(closure, 100000)))
    assertTrue(NodeUtil.verify(second))
  }
}
