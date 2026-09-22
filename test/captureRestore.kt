import cadenza.Language
import cadenza.RuntimeError
import cadenza.data.BigInt
import cadenza.data.Closure
import cadenza.data.Neutral
import cadenza.data.NeutralException
import cadenza.data.NeutralValue
import cadenza.frame.CaptureLayout
import cadenza.jit.Code
import cadenza.jit.FrameAccess
import cadenza.jit.FrameLayout
import cadenza.jit.Indirection
import cadenza.jit.PlusNodeGen
import cadenza.semantics.Type
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.frame.FrameSlotKind
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.nodes.NodeUtil
import com.oracle.truffle.api.source.Source
import org.graalvm.polyglot.Context
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigInteger

class CaptureRestoreTests {
  private fun withLanguage(action: () -> Unit) {
    Context.create("cadenza").use { context ->
      context.initialize("cadenza")
      context.enter()
      try { action() } finally { context.leave() }
    }
  }

  private fun symbolic(type: Type) = NeutralValue(type,
    Neutral.NCallBuiltin(PlusNodeGen.create(), emptyArray()))

  @Test fun restoringMixedCapturesPreservesSnapshotsAndUnrelatedLocals() = withLanguage {
    val captures = CaptureLayout(Language.currentLanguage(),
      arrayOf(Type.Nat, Type.Bool, Type.Obj, Type.Obj, Type.Nat))
    val sourceLayout = FrameLayout()
    val sourceSlots = Array(5) { sourceLayout.bind("source$it") }
    val source = Truffle.getRuntime().createVirtualFrame(emptyArray(), sourceLayout.build())
    val reference = Any()
    val big = BigInt(BigInteger.ONE.shiftLeft(100).negate())
    val expected = arrayOf<Any?>(1701, true, reference, null, big)
    expected.forEachIndexed { index, value -> FrameAccess.write(source, sourceSlots[index], value) }
    val environment = captures.capture(source, sourceSlots)

    val destinationLayout = FrameLayout()
    val before = destinationLayout.bind("before")
    // Capture indexes and local slots intentionally have different orders.
    val destinationSlots = Array(5) { destinationLayout.bind("destination$it") }.reversedArray()
    val after = destinationLayout.bind("after")
    val destination = Truffle.getRuntime().createVirtualFrame(emptyArray(), destinationLayout.build())
    FrameAccess.write(destination, before, 42)
    FrameAccess.write(destination, after, reference)
    for (index in expected.indices) captures.restore(environment, index, destination, destinationSlots[index])
    assertTrue(destination.isInt(destinationSlots[0]))
    assertTrue(destination.isBoolean(destinationSlots[1]))
    assertSame(reference, destination.getObject(destinationSlots[2]))
    assertNull(destination.getObject(destinationSlots[3]))
    assertSame(big, destination.getObject(destinationSlots[4]))

    // Neither later source writes nor destination writes can alter an escaped snapshot.
    sourceSlots.forEach { FrameAccess.write(source, it, 0) }
    destinationSlots.forEach { FrameAccess.write(destination, it, null) }
    val materialized = destination.materialize()
    for (index in expected.indices) {
      captures.restore(environment, index, materialized, destinationSlots[index])
      assertEquals(expected[index], FrameAccess.read(destination, destinationSlots[index]))
      assertEquals(expected[index], captures.read(environment, index))
      assertEquals(expected[index], environment.getValue(index))
    }
    assertEquals(42, FrameAccess.read(destination, before))
    assertSame(reference, FrameAccess.read(destination, after))
  }

  @Test fun primitiveRestorationRespectsWidenedDestinationsAndOlderFrameTags() = withLanguage {
    val captures = CaptureLayout(Language.currentLanguage(), arrayOf(Type.Nat, Type.Bool))
    val sourceLayout = FrameLayout()
    val sourceSlots = arrayOf(sourceLayout.bind("number"), sourceLayout.bind("predicate"))
    val source = Truffle.getRuntime().createVirtualFrame(emptyArray(), sourceLayout.build())
    FrameAccess.write(source, sourceSlots[0], 2049)
    FrameAccess.write(source, sourceSlots[1], false)
    val primitive = captures.capture(source, sourceSlots)
    val big = BigInt(BigInteger.ONE.shiftLeft(100))
    val neutral = symbolic(Type.Bool)
    FrameAccess.write(source, sourceSlots[0], big)
    FrameAccess.write(source, sourceSlots[1], neutral)
    val exotic = captures.capture(source, sourceSlots)

    val destinationLayout = FrameLayout()
    val destinations = arrayOf(destinationLayout.bind("number"), destinationLayout.bind("predicate"))
    val descriptor = destinationLayout.build()
    val older = Truffle.getRuntime().createVirtualFrame(emptyArray(), descriptor)
    val newer = Truffle.getRuntime().createVirtualFrame(emptyArray(), descriptor)
    for (index in destinations.indices) captures.restore(primitive, index, older, destinations[index])
    assertTrue(older.isInt(destinations[0]))
    assertTrue(older.isBoolean(destinations[1]))

    for (index in destinations.indices) captures.restore(exotic, index, newer, destinations[index])
    assertSame(big, FrameAccess.read(newer, destinations[0]))
    assertSame(neutral, FrameAccess.read(newer, destinations[1]))
    for (index in destinations.indices) {
      assertEquals(FrameSlotKind.Object, descriptor.getSlotKind(destinations[index]))
      captures.restore(primitive, index, newer, destinations[index])
      assertTrue(newer.isObject(destinations[index]), "Object-widened slots remain Object slots")
    }
    assertEquals(2049, FrameAccess.read(newer, destinations[0]))
    assertEquals(false, FrameAccess.read(newer, destinations[1]))
    assertEquals(2049, older.getInt(destinations[0]))
    assertEquals(false, older.getBoolean(destinations[1]))
  }

  @Test fun restoringNeutralAndRecursiveCapturesDoesNotForceTheirValues() = withLanguage {
    val captures = CaptureLayout(Language.currentLanguage(), arrayOf(Type.Nat, Type.Nat))
    val sourceLayout = FrameLayout()
    val sourceSlots = arrayOf(sourceLayout.bind("symbolic"), sourceLayout.bind("recursive"))
    val source = Truffle.getRuntime().createVirtualFrame(emptyArray(), sourceLayout.build())
    val neutral = symbolic(Type.Nat)
    val cell = Indirection()
    FrameAccess.write(source, sourceSlots[0], neutral)
    FrameAccess.write(source, sourceSlots[1], cell)
    val environment = captures.capture(source, sourceSlots)

    val destinationLayout = FrameLayout()
    val symbolicSlot = destinationLayout.bind("symbolic")
    val recursiveSlot = destinationLayout.bind("recursive")
    val destination = Truffle.getRuntime().createVirtualFrame(emptyArray(), destinationLayout.build())
    captures.restore(environment, 0, destination, symbolicSlot)
    captures.restore(environment, 1, destination, recursiveSlot)
    assertSame(neutral, Code.Var(symbolicSlot).executeAny(destination))
    val exception = assertThrows(NeutralException::class.java) {
      Code.Var(symbolicSlot).executeInteger(destination)
    }
    assertSame(neutral.term, exception.term)
    assertSame(cell, FrameAccess.read(destination, recursiveSlot))
    assertThrows(RuntimeError::class.java) { Code.Var(recursiveSlot).executeInteger(destination) }
    cell.value = 42
    cell.set = true
    assertEquals(42, Code.Var(recursiveSlot).executeInteger(destination))
    assertSame(cell, captures.read(environment, 1))
  }

  @Test fun clonedRootsRestoreEachInvocationsEnvironmentAfterPrimitiveAndExoticCaptures() = withLanguage {
    val maker = Language.currentLanguage().parse(Source.newBuilder("cadenza", """
      \(number : Nat) (predicate : Bool) -> \(extra : Nat) ->
        if predicate then plus number extra else minus number extra
    """.trimIndent(), "restore-clone.za").build()).call() as Closure
    val interop = InteropLibrary.getUncached()
    val big = BigInteger.ONE.shiftLeft(100)
    val cases = listOf(
      Triple(interop.execute(maker, 2000, true) as Closure, BigInteger.valueOf(2000), true),
      Triple(interop.execute(maker, BigInt(big), false) as Closure, big, false),
      Triple(interop.execute(maker, 3000, false) as Closure, BigInteger.valueOf(3000), false)
    )
    val clone = NodeUtil.cloneNode(cases.first().first.callTarget.rootNode)
    assertTrue(NodeUtil.verify(clone))
    for ((closure, number, predicate) in cases + cases.reversed()) {
      val expected = if (predicate) number.add(BigInteger.valueOf(17)) else number.subtract(BigInteger.valueOf(17))
      for (target in listOf(clone.callTarget, closure.callTarget)) {
        val actual = target.call(0L, closure.env, 17)
        val integer = if (actual is BigInt) actual.value else BigInteger.valueOf((actual as Int).toLong())
        assertEquals(expected, integer)
      }
    }
  }
}
