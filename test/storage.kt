import cadenza.Language
import cadenza.data.Closure
import cadenza.data.BigInt
import cadenza.data.Neutral
import cadenza.data.NeutralValue
import cadenza.frame.CaptureLayout
import cadenza.jit.FrameAccess
import cadenza.jit.FrameLayout
import cadenza.jit.PlusNodeGen
import cadenza.semantics.Type
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.instrumentation.Instrumenter
import com.oracle.truffle.api.instrumentation.SourceSectionFilter
import com.oracle.truffle.api.instrumentation.StandardTags
import com.oracle.truffle.api.instrumentation.ExecutionEventNode
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory
import com.oracle.truffle.api.instrumentation.InstrumentableNode
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.nodes.NodeUtil
import com.oracle.truffle.api.source.Source
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.frame.FrameSlotKind
import com.oracle.truffle.api.frame.FrameSlotTypeException
import org.graalvm.polyglot.Context
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigInteger

class StorageTests {
  @Test fun localSlotsSpecializeAndRetainOlderFrameValuesAfterWidening() {
    val layout = FrameLayout()
    val slot = layout.slot("x")
    val descriptor = layout.build()
    val older = Truffle.getRuntime().createVirtualFrame(emptyArray(), descriptor)
    val newer = Truffle.getRuntime().createVirtualFrame(emptyArray(), descriptor)
    FrameAccess.write(older, slot, 42)
    assertEquals(FrameSlotKind.Int, descriptor.getSlotKind(slot))
    assertTrue(older.isInt(slot))
    val big = BigInt(BigInteger.ONE.shiftLeft(100))
    FrameAccess.write(newer, slot, big)
    assertEquals(FrameSlotKind.Object, descriptor.getSlotKind(slot))
    assertSame(big, FrameAccess.read(newer, slot))
    assertEquals(42, FrameAccess.read(older, slot))
    FrameAccess.write(newer, slot, 7)
    assertEquals(FrameSlotKind.Object, descriptor.getSlotKind(slot))
    assertEquals(7, FrameAccess.read(newer, slot))
  }

  @Test fun captureLayoutPreservesPrimitivesBigIntsNeutralsAndImmutableSnapshots() {
    Context.create("cadenza").use { context ->
      context.initialize("cadenza")
      context.enter()
      try {
        val captures = CaptureLayout(Language.currentLanguage(null), arrayOf(Type.Nat, Type.Bool, Type.Obj))
        val layout = FrameLayout()
        val slots = arrayOf(layout.slot("number"), layout.slot("predicate"), layout.slot("reference"))
        val frame = Truffle.getRuntime().createVirtualFrame(emptyArray(), layout.build())
        val reference = Any()
        FrameAccess.write(frame, slots[0], 42)
        FrameAccess.write(frame, slots[1], true)
        FrameAccess.write(frame, slots[2], reference)
        val original = captures.capture(frame, slots)
        assertTrue(original.isInteger(0))
        assertEquals(42, original.getInteger(0))
        assertEquals(true, original.getValue(1))
        assertSame(reference, original.getObject(2))
        val primitiveMismatch = assertThrows(FrameSlotTypeException::class.java) { original.getObject(0) }
        assertEquals(0, primitiveMismatch.slot)
        assertEquals(FrameSlotKind.Object, primitiveMismatch.expectedKind)
        assertEquals(FrameSlotKind.Int, primitiveMismatch.actualKind)

        val big = BigInt(BigInteger.ONE.shiftLeft(100))
        val neutral = NeutralValue(Type.Bool, Neutral.NCallBuiltin(PlusNodeGen.create(), emptyArray()))
        FrameAccess.write(frame, slots[0], big)
        FrameAccess.write(frame, slots[1], neutral)
        FrameAccess.write(frame, slots[2], null)
        val exotic = captures.capture(frame, slots)
        assertFalse(exotic.isInteger(0))
        assertSame(big, exotic.getObject(0))
        assertSame(neutral, exotic.getValue(1))
        assertNull(exotic.getObject(2))
        val objectMismatch = assertThrows(FrameSlotTypeException::class.java) { exotic.getInteger(0) }
        assertEquals(0, objectMismatch.slot)
        assertEquals(FrameSlotKind.Int, objectMismatch.expectedKind)
        assertEquals(FrameSlotKind.Object, objectMismatch.actualKind)
        assertEquals(42, original.getInteger(0))
        assertEquals(true, original.getValue(1))
        assertSame(reference, original.getObject(2))
      } finally {
        context.leave()
      }
    }
  }

  @Test fun capturedNaturalCanOverflowInt() {
    Context.create("cadenza").use { context ->
      val value = context.eval("cadenza", "(\\(x : Nat) -> (\\(y : Nat) -> plus x y) 1) (plus 2147483647 1)")
      assertEquals(BigInteger.valueOf(2147483649L), value.asBigInteger())
    }
  }

  @Test fun captureLayoutsRemainIndependentAcrossRepeatedContexts() {
    repeat(3) {
      Context.create("cadenza").use { context ->
        // Both inner lambdas capture a Nat, but their layouts and values are distinct.
        val first = context.eval("cadenza", "(\\(x : Nat) -> \\(y : Nat) -> plus x y) 1000")
        val second = context.eval("cadenza", "(\\(x : Nat) -> \\(y : Nat) -> plus x y) 2000")
        assertEquals(1042, first.execute(42).asInt())
        assertEquals(2042, second.execute(42).asInt())
        assertEquals(1043, first.execute(43).asInt())
      }
    }
  }

  @Test fun captureLayoutKeepsMixedPrimitiveAndObjectFieldsSeparate() {
    Context.create("cadenza").use { context ->
      context.initialize("cadenza")
      context.enter()
      try {
        val types = Array(24) { when (it % 3) { 0 -> Type.Nat; 1 -> Type.Bool; else -> Type.Obj } }
        val captures = CaptureLayout(Language.currentLanguage(), types)
        val layout = FrameLayout()
        val slots = Array(types.size) { layout.slot("capture$it") }
        val frame = Truffle.getRuntime().createVirtualFrame(emptyArray(), layout.build())
        val values = Array<Any?>(types.size) { when (it % 3) { 0 -> 1000 + it; 1 -> it % 2 == 0; else -> Any() } }
        for (index in slots.indices) FrameAccess.write(frame, slots[index], values[index])
        val environment = captures.capture(frame, slots)
        for (index in slots.indices) {
          assertEquals(values[index], environment.getValue(index))
          assertEquals(values[index], captures.read(environment, index))
          FrameAccess.write(frame, slots[index], null)
        }
        for (index in slots.indices) assertEquals(values[index], environment.getValue(index))
      } finally {
        context.leave()
      }
    }
  }

  @Test fun resolvedRecursiveBindingCanBeCapturedAsANatural() {
    Context.create("cadenza").use { context ->
      assertEquals(42, context.eval("cadenza",
        "let x : Nat = 40 in (\\(y : Nat) -> plus x y) 2").asInt())
    }
  }

  @Test fun instrumentedClosureBodyHasSingleOwnership() {
    Context.create("cadenza").use { context ->
      context.initialize("cadenza")
      context.enter()
      try {
        val closure = Language.currentLanguage().parse(Source.newBuilder("cadenza",
          "\\(x : Nat) -> plus x 1", "instrumented.za").build()).call() as Closure
        var entered = false
        val env = TruffleLanguage.ContextReference.create(Language::class.java).get(null).env
        val instrumenter = env.lookup(Instrumenter::class.java)
        val binding = instrumenter.attachExecutionEventFactory(
          SourceSectionFilter.newBuilder().tagIs(StandardTags.RootBodyTag::class.java).build(),
          ExecutionEventNodeFactory {
            object : ExecutionEventNode() {
              override fun onEnter(frame: com.oracle.truffle.api.frame.VirtualFrame) { entered = true }
            }
          }
        )
        try {
          assertEquals(42, closure.callTarget.call(0L, 41))
          assertTrue(entered)
          var wrapperCount = 0
          closure.callTarget.rootNode.accept { node ->
            if (node is InstrumentableNode.WrapperNode) wrapperCount++
            true
          }
          assertTrue(wrapperCount > 0)
          assertTrue(NodeUtil.verify(closure.callTarget.rootNode))
        } finally {
          binding.dispose()
        }
      } finally {
        context.leave()
      }
    }
  }


  @Test fun uncachedInteropEntryCanCallAnUnadoptedDispatch() {
    Context.create("cadenza").use { context ->
      context.initialize("cadenza")
      context.enter()
      try {
        val closure = Language.currentLanguage().parse(Source.newBuilder("cadenza",
          "let count : Nat -> Nat = \\(x : Nat) -> if le 10000 x then x else count (plus x 1) in count",
          "uncached.za").build()).call() as Closure
        assertEquals(10000, InteropLibrary.getUncached().execute(closure, 0))
        assertTrue(NodeUtil.verify(closure.callTarget.rootNode))
      } finally {
        context.leave()
      }
    }
  }


  @Test fun neutralArgumentsRetainTheExceptionPathThroughCapturesAndPartialCalls() {
    Context.create("cadenza").use { context ->
      context.initialize("cadenza")
      context.enter()
      try {
        val neutral = NeutralValue(Type.Nat, Neutral.NCallBuiltin(PlusNodeGen.create(), arrayOf(1, 2)))
        val programs = listOf(
          "\\(x : Nat) -> plus x 1",
          "\\(x : Nat) -> (plus x) 1",
          "\\(x : Nat) -> plus ((\\(y : Nat) -> y) x) 1",
          "\\(x : Nat) -> (\\(y : Nat) -> plus x y) 1"
        )
        for (program in programs) {
          val closure = Language.currentLanguage().parse(Source.newBuilder("cadenza", program, "neutral.za").build()).call() as Closure
          val result = closure.callTarget.call(0L, neutral) as NeutralValue
          assertEquals(Type.Nat, result.type)
          val call = result.term as Neutral.NCallBuiltin
          assertEquals(neutral.term, (call.args[0] as NeutralValue).term)
          assertEquals(1, call.args[1])
        }
      } finally {
        context.leave()
      }
    }
  }

}
