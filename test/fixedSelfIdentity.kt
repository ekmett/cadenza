import cadenza.Language
import cadenza.data.Closure
import cadenza.jit.*
import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.frame.MaterializedFrame
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.DirectCallNode
import com.oracle.truffle.api.nodes.NodeUtil
import com.oracle.truffle.api.nodes.RootNode
import com.oracle.truffle.api.source.Source
import org.graalvm.polyglot.Context
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.IdentityHashMap

class FixedSelfIdentityTests {
  private fun withLanguage(action: (Language) -> Unit) {
    Context.create("cadenza").use { context ->
      context.initialize("cadenza")
      context.enter()
      try { action(Language.currentLanguage()) } finally { context.leave() }
    }
  }

  private fun fixedOwner(language: Language) = FixNatFNodeGen.create().also {
    BuiltinRootNode(language, it).callTarget
  }

  private class Body(language: Language) {
    private val layout = FrameLayout()
    private val selfSlot = layout.slot("self")
    private val argumentSlot = layout.slot("n")
    private val iterations = 128
    private var schedule: List<Closure> = emptyList()
    private val seen = mutableListOf<Closure>()
    private val frames: MutableSet<MaterializedFrame> = Collections.newSetFromMap(IdentityHashMap())
    private val roots: MutableSet<RootNode> = Collections.newSetFromMap(IdentityHashMap())
    val root = ClosureRootNode(language, layout.build(), 2,
      argPreamble = arrayOf(selfSlot to 0, argumentSlot to 1),
      body = ClosureBody(object : Code(null) {
        @Child private var dispatch: Dispatch = DispatchNodeGen.create(1, true)

        override fun execute(frame: VirtualFrame): Any? {
          seen += FrameAccess.read(frame, selfSlot) as Closure
          frames += frame.materialize()
          roots += rootNode
          val n = FrameAccess.read(frame, argumentSlot) as Int
          if (n == 0) return 42
          val next = schedule[(iterations - n + 1) % schedule.size]
          return dispatch.executeDispatch(frame, next, arrayOf(n - 1))
        }
      }), source = Source.newBuilder("cadenza", "0", "fixed-self-identity.za").build())
    val function = Closure(null, emptyArray(), 2, natFF, root.callTarget)

    fun check(sequence: List<Closure>, target: RootCallTarget, expectedRoot: RootNode) {
      schedule = sequence
      seen.clear()
      frames.clear()
      roots.clear()
      // The initial call uses the same physical prefix as the recursive dispatcher.
      val arguments = arrayOf<Any?>(0L, sequence.first().papArgs.single(), iterations)
      assertEquals(42, CallUtils.callTarget(target, arguments))
      assertEquals(iterations + 1, seen.size)
      seen.forEachIndexed { index, actual ->
        assertSame(sequence[index % sequence.size], actual, "incoming self was replaced at iteration $index")
      }
      assertEquals(1, frames.size, "changing fixed self should reuse the same logical body's frame")
      assertEquals(setOf(expectedRoot), roots, "self-tail calls must remain inside the selected root")
    }
  }

  private class Driver(language: Language, target: RootCallTarget) :
    CadenzaRootNode(language, FrameLayout().build()) {
    @Child var direct: DirectCallNode = DirectCallNode.create(target)
    override fun execute(frame: VirtualFrame): Any? = CallUtils.callDirect(direct, frame.arguments)

    fun split(): ClosureRootNode {
      callTarget
      assertTrue(direct.cloneCallTarget(), "this test requires an actual runtime split")
      assertTrue(direct.isCallTargetCloned)
      return direct.currentRootNode as ClosureRootNode
    }
  }

  private fun exercise(split: Boolean) = withLanguage { language ->
    val body = Body(language)
    val owner = fixedOwner(language)
    val first = owner.mkFix(body.function)
    val equal = owner.mkFix(body.function)
    val otherOwner = fixedOwner(language).mkFix(body.function)
    assertNotSame(first, equal)
    assertEquals(first, equal)
    assertNotEquals(first, otherOwner)
    body.check(listOf(first), body.root.callTarget, body.root)

    val driver = if (split) Driver(language, body.root.callTarget) else null
    val selectedRoot = driver?.split() ?: body.root
    val target = driver?.callTarget ?: body.root.callTarget
    body.check(listOf(first, equal, first), target, selectedRoot)
    body.check(listOf(otherOwner, equal, first, otherOwner), target, selectedRoot)
    body.check(listOf(first, equal, first), target, selectedRoot)
    // Splitting must leave the original body valid as well.
    body.check(listOf(equal, first, otherOwner), body.root.callTarget, body.root)
    assertTrue(NodeUtil.verify(body.root))
    assertTrue(NodeUtil.verify(selectedRoot))
    if (driver != null) assertTrue(NodeUtil.verify(driver))
  }

  @Test fun selfTailFrameReusePreservesEachIncomingFixedClosure() = exercise(false)

  @Test fun runtimeSplitPreservesChangingFixedSelfReferencesInItsOwnFrame() = exercise(true)
}
