package cadenza.tests

import cadenza.Language
import cadenza.data.BigInt
import cadenza.data.Closure
import cadenza.data.ImportArgumentsNodeGen
import cadenza.data.Neutral
import cadenza.data.NeutralValue
import cadenza.jit.PlusNodeGen
import cadenza.semantics.Type
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnsupportedTypeException
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.source.Source
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigInteger

/** Distinct foreign receiver class, with the complete numeric interop contract. */
@ExportLibrary(value = InteropLibrary::class, delegateTo = "number")
class ForeignArgumentInteger(value: BigInteger) : TruffleObject {
  @JvmField val number = BigInt(value)
}

class NumericArgumentTests {
  private val natFunction = Type.Arr(Type.Nat, Type.Nat)

  @Test fun hostNumbersAreImportedLosslesslyOnBothBackends() {
    val large = BigInteger.ONE.shiftLeft(100)
    val cases = listOf(
      0.toByte() to BigInteger.ZERO,
      42.toByte() to BigInteger.valueOf(42),
      300.toShort() to BigInteger.valueOf(300),
      1000 to BigInteger.valueOf(1000),
      42L to BigInteger.valueOf(42),
      Int.MAX_VALUE.toLong() + 1 to BigInteger.valueOf(Int.MAX_VALUE.toLong() + 1),
      Long.MAX_VALUE to BigInteger.valueOf(Long.MAX_VALUE),
      BigInteger.ZERO to BigInteger.ZERO,
      BigInteger.valueOf(42) to BigInteger.valueOf(42),
      large to large,
      BigInt(large) to large,
      0.0 to BigInteger.ZERO,
      42.0f to BigInteger.valueOf(42),
      42.0 to BigInteger.valueOf(42),
      Math.scalb(1.0, 100) to large,
      Float.MAX_VALUE to BigDecimal(Float.MAX_VALUE.toDouble()).toBigIntegerExact(),
      Double.MAX_VALUE to BigDecimal(Double.MAX_VALUE).toBigIntegerExact(),
      ForeignArgumentInteger(BigInteger.valueOf(42)) to BigInteger.valueOf(42),
      ForeignArgumentInteger(large) to large
    )
    for (backend in listOf("ast", "bytecode")) {
      Context.newBuilder("cadenza").allowExperimentalOptions(true)
        .option("cadenza.Backend", backend).build().use { context ->
          val identity = context.eval("cadenza", "\\(x : Nat) -> x")
          // Repeat after saturating the bounded interop receiver cache.
          repeat(2) {
            for ((argument, expected) in cases) {
              assertEquals(expected, identity.execute(argument).asBigInteger(), "$backend: $argument")
            }
          }
          val plus = context.eval("cadenza", "plus")
          assertEquals(42, plus.execute(40L).execute(2.toByte()).asInt())
          val curried = context.eval("cadenza", "\\(x : Nat) -> \\(y : Nat) -> plus x y")
          assertEquals(42, curried.execute(40L, BigInteger.TWO).asInt())
          assertEquals(large + BigInteger.ONE, plus.execute(large, 1L).asBigInteger())
        }
    }
  }

  @Test fun lossyNegativeAndNonnumericArgumentsRemainErrors() {
    val invalid = listOf(
      (-1).toByte(), (-1).toShort(), -1, -1L, Long.MIN_VALUE,
      BigInteger.valueOf(-1), BigInt(-1), ForeignArgumentInteger(BigInteger.valueOf(-1)),
      -0.0f, -0.0, -1.0f, -1.0, 1.5f, 1.5,
      Float.NaN, Double.NaN, Float.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
      Float.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
      true, '4', "42", BigDecimal("42"), null
    )
    for (backend in listOf("ast", "bytecode")) {
      Context.newBuilder("cadenza").allowExperimentalOptions(true)
        .option("cadenza.Backend", backend).build().use { context ->
          val identity = context.eval("cadenza", "\\(x : Nat) -> x")
          for (argument in invalid) {
            assertThrows(IllegalArgumentException::class.java, { identity.execute(argument) }, "$backend: $argument")
          }
          val boolean = context.eval("cadenza", "\\(x : Bool) -> if x then 42 else 0")
          assertEquals(42, boolean.execute(true).asInt())
          assertThrows(IllegalArgumentException::class.java) { boolean.execute(1L) }
        }
    }
  }

  @Test fun disabledHostBigIntegerNumberAccessIsRespected() {
    val hostAccess = HostAccess.newBuilder(HostAccess.EXPLICIT).allowBigIntegerNumberAccess(false).build()
    Context.newBuilder("cadenza").allowHostAccess(hostAccess).build().use { context ->
      val identity = context.eval("cadenza", "\\(x : Nat) -> x")
      val large = BigInteger.ONE.shiftLeft(100)
      assertFalse(context.asValue(large).isNumber)
      assertThrows(IllegalArgumentException::class.java) { identity.execute(large) }
      assertEquals(42, identity.execute(42L).asInt())
      assertEquals(large, identity.execute(BigInt(large)).asBigInteger())
      assertEquals(large, identity.execute(ForeignArgumentInteger(large)).asBigInteger())
    }
  }

  @Test fun importerKeepsNativeArraysAndDoesNotOverwriteForeignArguments() {
    val importer = ImportArgumentsNodeGen.getUncached()
    val native = arrayOf<Any?>(1000)
    assertSame(native, importer.execute(natFunction, native))
    val big = BigInt(BigInteger.ONE.shiftLeft(100))
    val nativeBig = arrayOf<Any?>(big)
    assertSame(nativeBig, importer.execute(natFunction, nativeBig))
    val foreign = arrayOf<Any?>(1000L)
    val normalized = importer.execute(natFunction, foreign)
    assertNotSame(foreign, normalized)
    assertEquals(1000L, foreign[0])
    assertEquals(1000, normalized[0])
    assertTrue(normalized[0] is Int)

    val twoArguments = Type.Arr(Type.Nat, natFunction)
    val rejected = arrayOf<Any?>(40L, -1L)
    assertThrows(UnsupportedTypeException::class.java) { importer.execute(twoArguments, rejected) }
    assertArrayEquals(arrayOf<Any?>(40L, -1L), rejected)
  }

  @Test fun closureInteropDoesNotRetainOrMutateTheCallersArgumentArray() {
    Context.create("cadenza").use { context ->
      context.initialize("cadenza")
      context.enter()
      try {
        val plus = Language.currentLanguage().parse(Source.newBuilder("cadenza", "plus", "numeric-arguments.za").build()).call() as Closure
        val arguments = arrayOf<Any?>(40L)
        val partial = executeWithoutArrayCopy(plus, arguments) as Closure
        assertEquals(40L, arguments[0])
        arguments[0] = 1000L
        assertEquals(42, InteropLibrary.getUncached().execute(partial, 2.toShort()))
      } finally {
        context.leave()
      }
    }
  }

  @Test fun neutralInteropNormalizesNumbersAndOwnsItsRetainedSnapshot() {
    val symbolic = NeutralValue(Type.Arr(Type.Nat, natFunction),
      Neutral.NCallBuiltin(PlusNodeGen.create(), emptyArray()))
    val native = arrayOf<Any?>(40)
    val first = executeWithoutArrayCopy(symbolic, native) as NeutralValue
    native[0] = 1000
    assertArrayEquals(arrayOf<Any?>(40), (first.term as Neutral.NApp).rands)

    val foreign = arrayOf<Any?>(2L)
    val second = executeWithoutArrayCopy(first, foreign) as NeutralValue
    assertEquals(2L, foreign[0])
    foreign[0] = 1000L
    assertArrayEquals(arrayOf<Any?>(40, 2), (second.term as Neutral.NApp).rands)
    assertEquals(Type.Nat, second.type)

    val interop = InteropLibrary.getUncached()
    val large = BigInteger.ONE.shiftLeft(100)
    val bigResult = interop.execute(symbolic, ForeignArgumentInteger(large)) as NeutralValue
    assertEquals(large, ((bigResult.term as Neutral.NApp).rands[0] as BigInt).value)
    assertThrows(UnsupportedTypeException::class.java) { interop.execute(symbolic, -0.0) }
    assertThrows(UnsupportedTypeException::class.java) { interop.execute(symbolic, 1.5) }
    val neutralArgument = NeutralValue(Type.Nat, symbolic.term)
    assertThrows(UnsupportedTypeException::class.java) { interop.execute(symbolic, neutralArgument) }
    assertSame(neutralArgument, (symbolic.apply(arrayOf(neutralArgument)).term as Neutral.NApp).rands[0])
  }

  private fun executeWithoutArrayCopy(receiver: TruffleObject, arguments: Array<Any?>): Any? {
    // Kotlin's spread operator copies arrays before a Java varargs call. Reflection
    // passes this exact inner array so ownership regressions remain observable.
    return InteropLibrary::class.java.getMethod("execute", Any::class.java, Array<Any?>::class.java)
      .invoke(InteropLibrary.getUncached(), receiver, arguments)
  }
}
