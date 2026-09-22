import cadenza.Language
import cadenza.data.BigInt
import cadenza.data.Neutral
import cadenza.data.NeutralException
import cadenza.data.NeutralValue
import cadenza.frame.CaptureLayout
import cadenza.jit.*
import cadenza.semantics.Type
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.frame.FrameSlotKind
import com.oracle.truffle.api.frame.FrameSlotTypeException
import com.oracle.truffle.api.frame.MaterializedFrame
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.source.Source
import org.graalvm.polyglot.Context
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class FrameTagTests {
  private fun neutral(type: Type) = NeutralValue(type,
    Neutral.NCallBuiltin(PlusNodeGen.create(), emptyArray()))

  private fun withLanguage(action: () -> Unit) {
    Context.create("cadenza").use { context ->
      context.initialize("cadenza")
      context.enter()
      try { action() } finally { context.leave() }
    }
  }

  @Test fun materializedFramesObservePrimitiveObjectAndNullTransitions() {
    val layout = FrameLayout()
    val slot = layout.bind("x")
    val frame = Truffle.getRuntime().createVirtualFrame(emptyArray(), layout.build())
    val materialized = frame.materialize()
    val big = BigInt(BigInteger.ONE.shiftLeft(100))
    val symbolic = neutral(Type.Bool)
    for ((index, value) in arrayOf(42, big, 43, true, symbolic, false, null, 44).withIndex()) {
      // Writes through either view remain visible through the other after materialization.
      FrameAccess.write(if (index % 2 == 0) frame else materialized, slot, value)
      assertEquals(value, frame.getValue(slot))
      assertEquals(value, FrameAccess.read(materialized, slot))
    }
  }

  @Test fun overwritingFrameValuesReleasesThePreviousObjectStorage() {
    val layout = FrameLayout()
    val slot = layout.bind("x")
    val frame = Truffle.getRuntime().createVirtualFrame(emptyArray(), layout.build())
    // Inspect the installed runtime's backing storage rather than depending on GC timing.
    // Replacing a value must release the old reference even if its representation changes.
    val objectStorage = frame.javaClass.getDeclaredField("indexedLocals").apply {
      isAccessible = true
    }.get(frame) as Array<*>
    for (primitive in arrayOf<Any>(42, true, false, 1000)) {
      val retained = Any()
      FrameAccess.write(frame, slot, retained)
      assertSame(retained, objectStorage[slot])
      FrameAccess.write(frame, slot, primitive)
      assertNotSame(retained, objectStorage[slot], "Replacing a value must release the previous object")
      assertEquals(primitive, FrameAccess.read(frame, slot))
    }
  }

  @Test fun neutralExceptionsAndIllegalSlotErrorsKeepTheirOriginalMeaning() {
    val layout = FrameLayout()
    val slot = layout.bind("x")
    val frame = Truffle.getRuntime().createVirtualFrame(emptyArray(), layout.build())
    val variable = Code.Var(slot)
    val symbolic = neutral(Type.Nat)
    FrameAccess.write(frame, slot, symbolic)
    assertSame(symbolic, variable.executeAny(frame))
    val exception = assertThrows(NeutralException::class.java) { variable.executeInteger(frame) }
    assertSame(symbolic.term, exception.term)
    FrameAccess.write(frame, slot, 42)
    assertEquals(42, variable.executeInteger(frame))
    frame.clear(slot)
    val illegal = assertThrows(FrameSlotTypeException::class.java) { FrameAccess.read(frame, slot) }
    assertEquals(slot, illegal.slot)
    assertEquals(FrameSlotKind.Illegal, illegal.actualKind)
    FrameAccess.write(frame, slot, null)
    assertNull(variable.executeAny(frame))
  }

  @Test fun captureSnapshotsSurviveLocalOverwritesAndKeepRecursiveCellsLive() = withLanguage {
    val layout = FrameLayout()
    val slot = layout.bind("x")
    val frame = Truffle.getRuntime().createVirtualFrame(emptyArray(), layout.build())
    val captures = CaptureLayout(Language.currentLanguage(), arrayOf(Type.Nat))
    FrameAccess.write(frame, slot, 41)
    val primitive = captures.capture(frame, arrayOf(slot))
    val big = BigInt(BigInteger.ONE.shiftLeft(100))
    FrameAccess.write(frame, slot, big)
    val exotic = captures.capture(frame, arrayOf(slot))
    val cell = Indirection()
    FrameAccess.write(frame, slot, cell)
    val recursive = captures.capture(frame, arrayOf(slot))
    cell.value = 42
    cell.set = true
    FrameAccess.write(frame, slot, 42)
    assertEquals(42, FrameAccess.read(frame, slot))
    assertEquals(41, captures.read(primitive, 0))
    assertSame(big, captures.read(exotic, 0))
    assertSame(cell, captures.read(recursive, 0))
    val childLayout = FrameLayout()
    val childSlot = childLayout.bind("x")
    val child = Truffle.getRuntime().createVirtualFrame(emptyArray(), childLayout.build())
    FrameAccess.write(child, childSlot, captures.read(recursive, 0))
    assertEquals(42, Code.Var(childSlot).executeInteger(child))
  }

  @Test fun sharedDescriptorsKeepDifferentFramesIndependentAcrossThreads() {
    val layout = FrameLayout()
    val slot = layout.bind("x")
    val descriptor = layout.build()
    val older = Truffle.getRuntime().createVirtualFrame(emptyArray(), descriptor)
    FrameAccess.write(older, slot, 42)
    val start = CountDownLatch(1)
    val executor = Executors.newFixedThreadPool(4)
    try {
      val futures = (0..3).map { worker -> executor.submit {
        val frame = Truffle.getRuntime().createVirtualFrame(emptyArray(), descriptor)
        val objectValue = Any()
        start.await()
        repeat(1000) { offset ->
          FrameAccess.write(frame, slot, objectValue)
          assertSame(objectValue, FrameAccess.read(frame, slot))
          FrameAccess.write(frame, slot, worker * 1000 + offset)
          assertEquals(worker * 1000 + offset, FrameAccess.read(frame, slot))
        }
      } }
      start.countDown()
      futures.forEach { it.get(20, TimeUnit.SECONDS) }
      assertEquals(42, FrameAccess.read(older, slot))
      assertTrue(older.isInt(slot), "Other frames must not alter an existing frame's runtime tag")
    } finally {
      executor.shutdownNow()
    }
  }

  @Test fun reservedTailCallSlotsKeepTheirRuntimeKindsAndPositions() {
    val descriptor = FrameLayout().build()
    assertEquals(4, descriptor.numberOfSlots)
    val frame = Truffle.getRuntime().createVirtualFrame(emptyArray(), descriptor)
    val arguments = arrayOf<Any?>(0L, 42)
    val function = Any()
    frame.setLong(FrameLayout.BLOOM_FILTER, 0x123456789L)
    frame.setObject(FrameLayout.TAIL_RESULT, 42)
    frame.setObject(FrameLayout.TAIL_FUNCTION, function)
    frame.setObject(FrameLayout.TAIL_ARGUMENTS, arguments)
    assertEquals(0, FrameLayout.BLOOM_FILTER)
    assertEquals(0x123456789L, FrameAccess.read(frame, 0))
    assertEquals(42, frame.getObject(1))
    assertSame(function, frame.getObject(2))
    assertSame(arguments, frame.getObject(3))
  }

  @Test fun selfTailCallsReuseAFrameAfterAnExoticArgument() = withLanguage {
    val layout = FrameLayout()
    val remaining = layout.bind("remaining")
    val accumulator = layout.bind("accumulator")
    lateinit var root: ClosureRootNode
    var observed: MaterializedFrame? = null
    val body = object : Code(null) {
      override fun execute(frame: VirtualFrame): Any? {
        if (observed == null) observed = frame.materialize()
        assertSame(observed, frame.materialize())
        val count = FrameAccess.read(frame, remaining) as Int
        val value = FrameAccess.read(frame, accumulator)
        if (count == 0) return value
        throw TailCallException(root.callTarget,
          arrayOf(0L, count - 1, ((value as Int) + count) % 65521))
      }
    }
    root = ClosureRootNode(Language.currentLanguage(), layout.build(), 2,
      argPreamble = arrayOf(remaining to 0, accumulator to 1), body = ClosureBody(body),
      source = Source.newBuilder("cadenza", "0", "frame-recovery.za").build())
    root.callTarget // Adopt children before directly exercising the root with a retained frame.
    val big = BigInt(BigInteger.ONE.shiftLeft(100))
    val arguments = arrayOf<Any?>(0L, 0, big)
    val frame = Truffle.getRuntime().createVirtualFrame(arguments, root.frameDescriptor)
    assertSame(big, root.execute(frame))
    arguments[1] = 10000
    arguments[2] = 1000
    var expected = 1000
    for (count in 10000 downTo 1) expected = (expected + count) % 65521
    assertEquals(expected, root.execute(frame))
    assertEquals(expected, FrameAccess.read(frame, accumulator))
    assertEquals(0, frame.getInt(remaining))
    assertEquals(expected, FrameAccess.read(observed!!, accumulator))
  }
}
