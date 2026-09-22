package cadenza.tests

import cadenza.bytecode.BytecodeRoot
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.instrumentation.EventContext
import com.oracle.truffle.api.instrumentation.ExecutionEventListener
import com.oracle.truffle.api.instrumentation.Instrumenter
import com.oracle.truffle.api.instrumentation.SourceSectionFilter
import com.oracle.truffle.api.instrumentation.StandardTags
import com.oracle.truffle.api.instrumentation.TruffleInstrument
import com.oracle.truffle.api.nodes.RootNode
import com.oracle.truffle.api.source.SourceSection
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Engine
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.ResourceLimits
import org.graalvm.polyglot.Source
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Observe the public instrumentation surface, independently of bytecode introspection. */
@TruffleInstrument.Registration(id = "cadenza-bytecode-tags-test", services = [BytecodeTagProbe::class])
class BytecodeTagTestInstrument : TruffleInstrument() {
  override fun onCreate(env: Env) { env.registerService(BytecodeTagProbe(env.instrumenter)) }
}

class BytecodeTagProbe(private val instrumenter: Instrumenter) {
  data class Event(val root: RootNode, val section: SourceSection?, val rootTag: Boolean,
                   val bodyTag: Boolean, val statementTag: Boolean)

  inner class Trace(sourceName: String) : AutoCloseable {
    val entries = mutableListOf<Event>()
    val returns = mutableListOf<Event>()
    val exceptions = mutableListOf<Pair<Event, Throwable>>()
    private fun event(context: EventContext) = Event(context.instrumentedNode.rootNode,
      context.instrumentedSourceSection, context.hasTag(StandardTags.RootTag::class.java),
      context.hasTag(StandardTags.RootBodyTag::class.java), context.hasTag(StandardTags.StatementTag::class.java))
    private val binding = instrumenter.attachExecutionEventListener(
      SourceSectionFilter.newBuilder()
        .sourceIs(SourceSectionFilter.SourcePredicate { it.name == sourceName })
        .tagIs(StandardTags.RootTag::class.java, StandardTags.RootBodyTag::class.java,
          StandardTags.StatementTag::class.java).build(),
      object : ExecutionEventListener {
        override fun onEnter(context: EventContext, frame: VirtualFrame) {
          entries += event(context)
        }
        override fun onReturnValue(context: EventContext, frame: VirtualFrame, result: Any?) {
          returns += event(context)
        }
        override fun onReturnExceptional(context: EventContext, frame: VirtualFrame, exception: Throwable) {
          exceptions += event(context) to exception
        }
      })
    override fun close() = binding.dispose()
  }

  fun trace(sourceName: String) = Trace(sourceName)
}

class BytecodeInstrumentationTests {
  private fun builder(backend: String) = Context.newBuilder("cadenza")
    .allowExperimentalOptions(true).option("cadenza.Backend", backend)

  private fun source(text: String, name: String) = Source.newBuilder("cadenza", text, name).build()

  private fun limits(limit: Long, notifications: AtomicInteger) = ResourceLimits.newBuilder()
    .statementLimit(limit, null).onLimit { notifications.incrementAndGet() }.build()

  private inline fun Context.withLimitCleanup(action: (Context) -> Unit) {
    try { action(this) } finally { close(true) }
  }

  private fun assertLimit(notifications: AtomicInteger, action: () -> Any?) {
    val error = assertThrows(PolyglotException::class.java) { action() }
    assertTrue(error.isCancelled, error.message)
    assertTrue(error.isResourceExhausted, error.message)
    assertFalse(error.isInternalError, error.message)
    assertEquals(1, notifications.get(), "the resource-limit callback must cause cancellation")
  }

  @Test fun generatedRootsExposeStatementRangesWithoutIncompleteRootEvents() {
    Engine.create().use { engine ->
      val probe = engine.instruments.getValue("cadenza-bytecode-tags-test").lookup(BytecodeTagProbe::class.java)
      builder("bytecode").engine(engine).build().use { context ->
        val header = "#!/usr/bin/env cadenza\n"
        val lambda = "\\(x : Nat) -> plus x 1"
        val source = source(header + lambda, "tag-ranges.za")
        probe.trace(source.name).use { trace ->
          val function = context.eval(source)
          assertEquals(42, function.execute(41).asInt())
          val roots = trace.entries.filter { it.rootTag }
          val bodies = trace.entries.filter { it.bodyTag }
          val statements = trace.entries.filter { it.statementTag }
          assertTrue(roots.isEmpty(), "bytecode root exits are incomplete across tail transfers")
          assertTrue(bodies.isEmpty(), "avoid advertising incomplete root-body events")
          assertEquals(2, statements.size)
          assertTrue(trace.entries.all { it.root is BytecodeRoot }, "helpers must not masquerade as source roots")
          assertEquals(listOf(header + lambda, lambda), statements.map { it.root.sourceSection!!.characters.toString() })
          assertEquals(listOf(lambda, "plus x 1"), statements.map { it.section!!.characters.toString() })
          assertEquals(listOf(header.length, header.length + lambda.indexOf("plus")),
            statements.map { it.section!!.charIndex })
          assertEquals(trace.entries, trace.returns, "ordinary completed statements have balanced exits")
          assertTrue(trace.exceptions.isEmpty())
        }
      }
    }
  }

  @Test fun attachingTagsAfterExecutionReplaysCapturedRootsWithoutChangingTheirValues() {
    Engine.create().use { engine ->
      val probe = engine.instruments.getValue("cadenza-bytecode-tags-test").lookup(BytecodeTagProbe::class.java)
      builder("bytecode").engine(engine).build().use { context ->
        val source = source("\\(offset : Nat) -> \\(x : Nat) -> plus offset x", "late-tags.za")
        val factory = context.eval(source)
        val retained = factory.execute(40)
        repeat(20) { assertEquals(42, retained.execute(2).asInt()) }
        probe.trace(source.name).use { trace ->
          assertEquals(43, retained.execute(3).asInt())
          assertEquals(0, trace.entries.count { it.rootTag })
          assertEquals(0, trace.entries.count { it.bodyTag })
          assertEquals(1, trace.entries.count { it.statementTag })
          assertEquals("plus offset x", trace.entries.single { it.statementTag }.section!!.characters.toString())
          trace.entries.clear()
          val other = factory.execute(100)
          assertEquals(102, other.execute(2).asInt())
          assertEquals(42, retained.execute(2).asInt())
          assertEquals(3, trace.entries.count { it.statementTag })
        }
      }
    }
  }

  @Test fun everyBytecodeTailIterationEntersItsSourceBodyExactlyOnce() {
    Engine.create().use { engine ->
      val probe = engine.instruments.getValue("cadenza-bytecode-tags-test").lookup(BytecodeTagProbe::class.java)
      builder("bytecode").engine(engine).build().use { context ->
        val source = source("let count : Nat -> Nat = \\(n : Nat) -> " +
          "if eq n 0 then 42 else count (minus n 1) in count", "tag-tail.za")
        probe.trace(source.name).use { trace ->
          val count = context.eval(source)
          assertEquals(42, count.execute(40).asInt())
          assertEquals(0, trace.entries.count { it.rootTag })
          assertEquals(0, trace.entries.count { it.bodyTag })
          assertEquals(42, trace.entries.count { it.statementTag })
        }
      }
    }
  }

  @Test fun aGuestErrorDeliversTheExceptionalStatementExit() {
    Engine.create().use { engine ->
      val probe = engine.instruments.getValue("cadenza-bytecode-tags-test").lookup(BytecodeTagProbe::class.java)
      builder("bytecode").engine(engine).build().use { context ->
        val source = source("div 1 0", "tag-error.za")
        probe.trace(source.name).use { trace ->
          val error = assertThrows(PolyglotException::class.java) { context.eval(source) }
          assertTrue(error.isGuestException)
          assertFalse(error.isInternalError)
          assertEquals(1, trace.entries.size)
          assertTrue(trace.entries.single().statementTag)
          assertFalse(trace.entries.single().rootTag)
          assertFalse(trace.entries.single().bodyTag)
          assertTrue(trace.returns.isEmpty())
          assertEquals(trace.entries, trace.exceptions.map { it.first })
          assertEquals("division by zero", trace.exceptions.single().second.message)
        }
      }
    }
  }

  @Test fun statementBudgetCountsWholeExpressionsAndActualClosureEntries() {
    for (backend in listOf("ast", "bytecode")) {
      val arithmeticNotifications = AtomicInteger()
      builder(backend).resourceLimits(limits(1, arithmeticNotifications)).build().withLimitCleanup { context ->
        assertEquals(42, context.eval("cadenza", "plus (mult 2 3) (minus 40 4)").asInt(), backend)
        assertEquals(0, arithmeticNotifications.get())
        assertLimit(arithmeticNotifications) { context.eval("cadenza", "0") }
      }
      val closureNotifications = AtomicInteger()
      builder(backend).resourceLimits(limits(2, closureNotifications)).build().withLimitCleanup { context ->
        val function = context.eval("cadenza", "\\(x : Nat) (y : Nat) -> plus x y")
        val partial = function.execute(40)
        assertEquals(42, partial.execute(2).asInt(), backend)
        assertEquals(0, closureNotifications.get(), "partial application does not execute a body: $backend")
        assertLimit(closureNotifications) { partial.execute(2) }
      }
    }
  }

  @Test fun statementLimitsCancelFiniteAndInfiniteTailRecursion() {
    val programs = listOf(
      "let count : Nat -> Nat = \\(n : Nat) -> if eq n 0 then 42 else count (minus n 1) in count 100000",
      "let loop : Nat -> Nat = \\(n : Nat) -> loop (plus n 1) in loop 0"
    )
    for (backend in listOf("ast", "bytecode")) {
      for (program in programs) {
        val notifications = AtomicInteger()
        builder(backend).resourceLimits(limits(64, notifications)).build().withLimitCleanup { context ->
          // If tagging regresses, the infinite case must fail instead of hanging the test suite.
          val watchdogTriggered = AtomicBoolean()
          val scheduler = Executors.newSingleThreadScheduledExecutor { action ->
            Thread(action, "cadenza-statement-limit-watchdog").apply { isDaemon = true }
          }
          val watchdog = scheduler.schedule({
            watchdogTriggered.set(true)
            context.close(true)
          }, 10, TimeUnit.SECONDS)
          try {
            assertLimit(notifications) { context.eval("cadenza", program) }
          } finally {
            watchdog.cancel(false)
            scheduler.shutdownNow()
            assertFalse(watchdogTriggered.get(), "statement counter did not terminate $backend execution")
          }
        }
      }
    }
  }

  @Test fun contextsSharingInstrumentedBytecodeKeepIndependentBudgets() {
    Engine.create().use { engine ->
      val firstNotifications = AtomicInteger()
      val secondNotifications = AtomicInteger()
      builder("bytecode").engine(engine).resourceLimits(limits(2, firstNotifications)).build().withLimitCleanup { first ->
        builder("bytecode").engine(engine).resourceLimits(limits(10, secondNotifications)).build().withLimitCleanup { second ->
          val shared = source("\\(x : Nat) -> plus x 1", "shared-budget.za")
          val firstFunction = first.eval(shared)
          val secondFunction = second.eval(shared)
          assertEquals(42, firstFunction.execute(41).asInt())
          assertLimit(firstNotifications) { firstFunction.execute(41) }
          repeat(5) { assertEquals(it + 1, secondFunction.execute(it).asInt()) }
          assertEquals(0, secondNotifications.get())
        }
      }
    }
  }
}
