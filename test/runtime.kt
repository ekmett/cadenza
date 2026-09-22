import cadenza.data.BigInt
import cadenza.frame.DataFrame
import cadenza.frame.frame
import cadenza.frame.loadClass
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigInteger

class RuntimeTests {
  @Test fun capturedEnvironment() {
    Context.create("cadenza").use { context ->
      assertEquals(42, context.eval("cadenza", "(\\(x : Nat) -> (\\(y : Nat) -> plus x y) 2) 40").asInt())
    }
  }

  @Test fun longTailRecursion() {
    Context.create("cadenza").use { context ->
      assertEquals(100000, context.eval("cadenza",
        "let count : Nat -> Nat = \\(x : Nat) -> if le 100000 x then x else count (plus x 1) in count 0"
      ).asInt())
    }
  }

  @Test fun syntaxErrorsAreGuestErrorsWithLocations() {
    Context.create("cadenza").use { context ->
      val error = assertThrows(PolyglotException::class.java) { context.eval("cadenza", "(") }
      assertTrue(error.isSyntaxError)
      assertFalse(error.isInternalError)
      assertNotNull(error.sourceLocation)
    }
  }

  @Test fun generatedFramesStoreBoxedAndPrimitiveValues() {
    val values = arrayOf<Any>(7, 8L, 1.5f, 2.5, "captured")
    val frameClass = frame("ILFDO").loadClass("cadenza.frame.dynamic.ILFDO")
    val fromArray = frameClass.getConstructor(Array<Any>::class.java)
      .newInstance(values as Any) as DataFrame
    val fromPrimitives = frameClass.getConstructor(
      Int::class.javaPrimitiveType, Long::class.javaPrimitiveType,
      Float::class.javaPrimitiveType, Double::class.javaPrimitiveType, Any::class.java
    ).newInstance(*values) as DataFrame
    for (frame in listOf(fromArray, fromPrimitives)) {
      values.forEachIndexed { index, value -> assertEquals(value, frame.getValue(index)) }
      assertEquals(7, frame.getInteger(0))
      assertEquals(8L, frame.getLong(1))
      assertEquals(1.5f, frame.getFloat(2))
      assertEquals(2.5, frame.getDouble(3))
      assertTrue(frame.isObject(4))
      assertEquals("captured", frame.getObject(4))
    }
  }

  @Test fun bigIntegerInterop() {
    Context.create("cadenza").use { context ->
      val expected = BigInteger.ONE.shiftLeft(100)
      val value = context.asValue(BigInt(expected))
      assertTrue(value.isNumber)
      assertTrue(value.fitsInBigInteger())
      assertFalse(value.fitsInLong())
      assertEquals(expected, value.asBigInteger())
    }
  }
}
