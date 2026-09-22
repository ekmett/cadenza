import cadenza.Language
import cadenza.RuntimeError
import cadenza.data.Closure
import cadenza.data.append
import cadenza.jit.FrameAccess
import cadenza.jit.FrameLayout
import cadenza.jit.ClosureRootNode
import cadenza.jit.TailCallException
import cadenza.jit.BuiltinRootNode
import cadenza.jit.FixNatFNodeGen
import com.oracle.truffle.api.frame.MaterializedFrame
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.instrumentation.ExecutionEventNode
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory
import com.oracle.truffle.api.instrumentation.Instrumenter
import com.oracle.truffle.api.instrumentation.ProbeNode
import com.oracle.truffle.api.instrumentation.SourceSectionFilter
import com.oracle.truffle.api.instrumentation.StandardTags
import com.oracle.truffle.api.nodes.NodeUtil
import com.oracle.truffle.api.source.Source
import org.graalvm.polyglot.Context
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.IdentityHashMap

class InstrumentationTests {
  private fun withLanguage(action: (Language, Instrumenter) -> Unit) {
    Context.create("cadenza").use { context ->
      context.initialize("cadenza")
      context.enter()
      try {
        action(Language.currentLanguage(), Language.currentContext().env.lookup(Instrumenter::class.java))
      } finally {
        context.leave()
      }
    }
  }

  private fun source(text: String) = Source.newBuilder("cadenza", text, "instrumentation.za").build()

  private fun filter(source: Source) = SourceSectionFilter.newBuilder().sourceIs(source)
    .rootNameIs { it == "closure" }
    .tagIs(StandardTags.RootTag::class.java, StandardTags.RootBodyTag::class.java).build()

  private fun slot(frame: VirtualFrame, name: String): Int =
    (0 until frame.frameDescriptor.numberOfSlots).single { frame.frameDescriptor.getSlotName(it) == name }

  private fun arguments(function: Closure, vararg arguments: Any?): Array<Any?> {
    val prefix = if (function.env == null) arrayOf<Any?>(0L) else arrayOf<Any?>(0L, function.env)
    return append(append(prefix, function.papArgs), arguments)
  }

  @Test fun rootEventsDescribeInvocationAndEncloseTheArgumentPreamble() = withLanguage { language, instrumenter ->
    val source = source("\\(x : Nat) -> plus x 1")
    val trace = mutableListOf<String>()
    val binding = instrumenter.attachExecutionEventFactory(filter(source), ExecutionEventNodeFactory { event ->
      val kind = if (event.hasTag(StandardTags.RootTag::class.java)) "root" else "body"
      object : ExecutionEventNode() {
        override fun onEnter(frame: VirtualFrame) {
          val index = (0 until frame.frameDescriptor.numberOfSlots)
            .singleOrNull { frame.frameDescriptor.getSlotName(it) == "x" }
          trace += "$kind enter ${index?.let { FrameAccess.read(frame, it) }}"
          if (kind == "body") assertTrue(frame.isLong(FrameLayout.BLOOM_FILTER))
        }
        override fun onReturnValue(frame: VirtualFrame, result: Any?) { trace += "$kind return $result" }
      }
    })
    try {
      val function = language.parse(source).call() as Closure
      assertTrue(trace.isEmpty(), "Constructing a closure must not report a function invocation: $trace")
      assertEquals(42, function.callTarget.call(*arguments(function, 41)))
      assertEquals(listOf("root enter null", "body enter 41", "body return 42", "root return 42"), trace)
      assertTrue(NodeUtil.verify(function.callTarget.rootNode))
    } finally {
      binding.dispose()
    }
  }

  @Test fun rootEventsContainTailIterationsAndExposeTheFailingFrameWithoutReusingIt() = withLanguage { language, instrumenter ->
    val source = source("""
      let go : Nat -> Nat -> Nat -> Nat = \(n : Nat) (acc : Nat) (divisor : Nat) ->
        if le n 0 then div acc divisor else go (minus n 1) (plus acc n) divisor
      in go
    """.trimIndent())
    val function = language.parse(source).call() as Closure
    var rootEntries = 0
    var rootReturns = 0
    var rootErrors = 0
    var tailExits = 0
    var bodyErrors = 0
    val counts = mutableListOf<Int>()
    val frames: MutableSet<MaterializedFrame> = Collections.newSetFromMap(IdentityHashMap())
    val binding = instrumenter.attachExecutionEventFactory(filter(source), ExecutionEventNodeFactory { event ->
      val root = event.hasTag(StandardTags.RootTag::class.java)
      object : ExecutionEventNode() {
        override fun onEnter(frame: VirtualFrame) {
          if (root) rootEntries++ else {
            counts += FrameAccess.read(frame, slot(frame, "n")) as Int
            frames += frame.materialize()
          }
        }
        override fun onReturnValue(frame: VirtualFrame, result: Any?) { if (root) rootReturns++ }
        override fun onReturnExceptional(frame: VirtualFrame, exception: Throwable) {
          if (root) {
            assertTrue(exception is RuntimeError, "A self-tail transfer must stay inside the invocation")
            rootErrors++
          } else when (exception) {
            is TailCallException -> tailExits++
            is RuntimeError -> bodyErrors++
            else -> throw AssertionError("Unexpected instrumented exception $exception")
          }
        }
      }
    })
    try {
      assertThrows(RuntimeError::class.java) { function.callTarget.call(*arguments(function, 4, 100, 0)) }
      assertEquals(1, rootEntries)
      assertEquals(0, rootReturns)
      assertEquals(1, rootErrors)
      assertEquals(4, tailExits)
      assertEquals(1, bodyErrors)
      assertEquals(listOf(4, 3, 2, 1, 0), counts)
      assertEquals(1, frames.size)
      val failed = frames.single()
      val acc = (0 until failed.frameDescriptor.numberOfSlots).single { failed.frameDescriptor.getSlotName(it) == "acc" }
      assertEquals(110, FrameAccess.read(failed, acc))
      assertEquals(206, function.callTarget.call(*arguments(function, 3, 200, 1)))
      assertEquals(2, rootEntries)
      assertEquals(1, rootReturns)
      assertEquals(1, rootErrors)
      assertEquals(2, frames.size, "A later invocation must get a different frame")
      assertEquals(110, FrameAccess.read(failed, acc), "A retained failed frame must not change on the next invocation")
      assertTrue(NodeUtil.verify(function.callTarget.rootNode))
    } finally {
      binding.dispose()
    }
  }

  @Test fun rootUnwindReentryRerunsThePreambleWithChangedArguments() = withLanguage { language, instrumenter ->
    val source = source("\\(divisor : Nat) -> div 42 divisor")
    val function = language.parse(source).call() as Closure
    val rootEntries = mutableListOf<Any?>()
    val bodyEntries = mutableListOf<Any?>()
    val retry = Any()
    var retried = false
    val binding = instrumenter.attachExecutionEventFactory(filter(source), ExecutionEventNodeFactory { event ->
      val root = event.hasTag(StandardTags.RootTag::class.java)
      object : ExecutionEventNode() {
        override fun onEnter(frame: VirtualFrame) {
          val value = FrameAccess.read(frame, slot(frame, "divisor"))
          if (root) rootEntries += value else bodyEntries += value
        }
        override fun onReturnExceptional(frame: VirtualFrame, exception: Throwable) {
          if (root && !retried) {
            assertTrue(exception is RuntimeError)
            retried = true
            frame.arguments[1] = 2
            throw event.createUnwind(retry)
          }
        }
        override fun onUnwind(frame: VirtualFrame, info: Any?): Any? {
          assertTrue(root)
          assertSame(retry, info)
          return ProbeNode.UNWIND_ACTION_REENTER
        }
      }
    })
    try {
      assertNull(function.env)
      assertEquals(21, function.callTarget.call(*arguments(function, 0)))
      assertTrue(retried)
      assertEquals(listOf(null, 0), rootEntries, "Root entry precedes each argument copy, including re-entry")
      assertEquals(listOf(0, 2), bodyEntries, "Re-entry must reload arguments before executing the body")
      assertTrue(NodeUtil.verify(function.callTarget.rootNode))
    } finally {
      binding.dispose()
    }
    assertThrows(RuntimeError::class.java) { function.callTarget.call(*arguments(function, 0)) }
    assertEquals(14, function.callTarget.call(*arguments(function, 3)))
  }

  @Test fun cloningAnInstrumentedRootKeepsOneInvocationAroundItsSelfLoop() = withLanguage { language, instrumenter ->
    val source = source("let count : Nat -> Nat = \\(n : Nat) -> if le n 0 then 42 else count (minus n 1) in count")
    val function = language.parse(source).call() as Closure
    var entries = 0
    var returns = 0
    var bodyEntries = 0
    val binding = instrumenter.attachExecutionEventFactory(filter(source), ExecutionEventNodeFactory { event ->
      val root = event.hasTag(StandardTags.RootTag::class.java)
      object : ExecutionEventNode() {
        override fun onEnter(frame: VirtualFrame) { if (root) entries++ else bodyEntries++ }
        override fun onReturnValue(frame: VirtualFrame, result: Any?) { if (root) returns++ }
      }
    })
    try {
      assertEquals(42, function.callTarget.call(*arguments(function, 2)))
      val cloned = NodeUtil.cloneNode(function.callTarget.rootNode)
      entries = 0
      returns = 0
      bodyEntries = 0
      assertEquals(42, cloned.callTarget.call(*arguments(function, 100)))
      assertEquals(1, entries)
      assertEquals(1, returns)
      assertEquals(101, bodyEntries)
      assertTrue(NodeUtil.verify(cloned))
      assertTrue(NodeUtil.verify(function.callTarget.rootNode))
    } finally {
      binding.dispose()
    }
  }

  @Test fun statementUnitsCountSourceAndFunctionBodiesButNotOperands() = withLanguage { language, instrumenter ->
    val source = source("(\\(x : Nat) -> plus (mult x 2) 1) 20")
    val statements = mutableListOf<String>()
    val roots = mutableListOf<String>()
    val bodySections = mutableListOf<String>()
    val filter = SourceSectionFilter.newBuilder().sourceIs(source)
      .tagIs(StandardTags.RootTag::class.java, StandardTags.StatementTag::class.java).build()
    val binding = instrumenter.attachExecutionEventFactory(filter, ExecutionEventNodeFactory { event ->
      val root = event.instrumentedNode.rootNode
      object : ExecutionEventNode() {
        override fun onEnter(frame: VirtualFrame) {
          if (event.hasTag(StandardTags.RootTag::class.java)) roots += root.name
          if (event.hasTag(StandardTags.StatementTag::class.java)) {
            statements += root.name
            if (root is ClosureRootNode) bodySections += event.instrumentedSourceSection.characters.toString()
          }
        }
      }
    })
    try {
      val target = language.parse(source)
      assertEquals(41, target.call())
      assertEquals(listOf("program root", "closure"), roots)
      assertEquals(listOf("program root", "closure"), statements)
      assertEquals(listOf("plus (mult x 2) 1"), bodySections)
      assertTrue(NodeUtil.verify((target as com.oracle.truffle.api.RootCallTarget).rootNode))
    } finally {
      binding.dispose()
    }
  }

  @Test fun cloningAnInstrumentedRootPreservesArgumentsOnUnwindAndTailFrames() = withLanguage { language, instrumenter ->
    val source = source("""
      \(self : Nat -> Nat) (n : Nat) ->
        if eq n 0 then div 42 n else if le n 2 then n else self (minus n 1)
    """.trimIndent())
    val function = language.parse(source).call() as Closure
    val owner = FixNatFNodeGen.create().also { BuiltinRootNode(language, it).callTarget }
    val plain = owner.mkFix(function)
    var expectedRoot = function.callTarget.rootNode
    val entries = mutableListOf<Any?>()
    val argumentsSeen = mutableListOf<Int>()
    val frames: MutableSet<MaterializedFrame> = Collections.newSetFromMap(IdentityHashMap())
    var returns = 0
    var retried = false
    val errorSections = mutableListOf<String?>()
    val retry = Any()
    val binding = instrumenter.attachExecutionEventFactory(filter(source), ExecutionEventNodeFactory { event ->
      val rootEvent = event.hasTag(StandardTags.RootTag::class.java)
      object : ExecutionEventNode() {
        override fun onEnter(frame: VirtualFrame) {
          assertSame(expectedRoot, event.instrumentedNode.rootNode)
          assertSame(expectedRoot.frameDescriptor, frame.frameDescriptor)
          val n = FrameAccess.read(frame, slot(frame, "n"))
          if (rootEvent) entries += n else {
            assertSame(plain, FrameAccess.read(frame, slot(frame, "self")))
            argumentsSeen += n as Int
            frames += frame.materialize()
          }
        }
        override fun onReturnValue(frame: VirtualFrame, result: Any?) { if (rootEvent) returns++ }
        override fun onReturnExceptional(frame: VirtualFrame, exception: Throwable) {
          if (rootEvent) {
            assertTrue(exception is RuntimeError)
            assertFalse(retried)
            errorSections += (exception as RuntimeError).encapsulatingSourceSection?.characters?.toString()?.trim()
            assertEquals(3, frame.arguments.size, "The cloned binary root must preserve its physical argument convention")
            frame.arguments[2] = 5
            retried = true
            throw event.createUnwind(retry)
          }
        }
        override fun onUnwind(frame: VirtualFrame, info: Any?): Any? {
          assertTrue(rootEvent)
          assertSame(retry, info)
          return ProbeNode.UNWIND_ACTION_REENTER
        }
      }
    })
    try {
      // Populate instrument wrappers and event nodes before making a real AST copy.
      assertEquals(2, function.callTarget.call(*arguments(function, plain, 2)))
      entries.clear(); argumentsSeen.clear(); frames.clear(); returns = 0
      val cloned = NodeUtil.cloneNode(function.callTarget.rootNode)
      val target = cloned.callTarget
      expectedRoot = cloned
      assertNotSame(function.callTarget.rootNode, cloned)
      assertSame(function.callTarget.rootNode.frameDescriptor, cloned.frameDescriptor)
      assertEquals(2, target.call(*arguments(function, plain, 4)))
      assertEquals(listOf<Int?>(null), entries)
      assertEquals(listOf(4, 3, 2), argumentsSeen)
      assertEquals(1, frames.size)
      assertEquals(2, target.call(*arguments(function, plain, 0)))
      assertTrue(retried)
      assertEquals(listOf("div 42 n"), errorSections)
      assertEquals(listOf(null, null, 0), entries)
      assertEquals(listOf(4, 3, 2, 0, 5, 4, 3, 2), argumentsSeen)
      assertEquals(2, frames.size, "Re-entry reuses its invocation frame; a later call gets a fresh one")
      assertEquals(2, returns)
      assertTrue(NodeUtil.verify(expectedRoot))
      assertTrue(NodeUtil.verify(function.callTarget.rootNode))
    } finally {
      binding.dispose()
    }
  }
}
