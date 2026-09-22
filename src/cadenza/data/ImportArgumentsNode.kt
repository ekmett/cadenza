package cadenza.data

import cadenza.semantics.Type
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.dsl.GenerateInline
import com.oracle.truffle.api.dsl.GenerateUncached
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.UnsupportedMessageException
import com.oracle.truffle.api.interop.UnsupportedTypeException
import com.oracle.truffle.api.library.CachedLibrary
import com.oracle.truffle.api.nodes.Node
import java.math.BigInteger

/** Normalize numeric interop arguments without changing guest-to-guest calls. */
@GenerateInline(false)
@GenerateUncached
abstract class ImportArgumentsNode : Node() {
  @Throws(UnsupportedTypeException::class)
  abstract fun execute(type: Type, arguments: Array<Any?>): Array<Any?>

  @Specialization
  @Throws(UnsupportedTypeException::class)
  fun convert(type: Type, arguments: Array<Any?>,
              @CachedLibrary(limit = "3") numbers: InteropLibrary): Array<Any?> {
    var result = arguments
    var currentType = type
    for (index in arguments.indices) {
      val functionType = currentType as Type.Arr
      val argument = arguments[index]
      val imported = if (functionType.argument === Type.Nat) importNatural(argument, numbers) else {
        functionType.argument.validate(argument)
        argument
      }
      if (imported !== argument) {
        // The interop caller owns its array. Native values need no copy, and a
        // converted argument must never overwrite an element in that input.
        if (result === arguments) result = arguments.copyOf()
        result[index] = imported
      }
      currentType = functionType.result
    }
    return result
  }

  private fun importNatural(value: Any?, numbers: InteropLibrary): Any {
    // Keep the original reference, including boxed Ints outside the JVM cache.
    if (value is Int && value >= 0 || value is BigInt && value.isNatural()) return value
    if (value is Int || value is BigInt) unsupported(value)
    if (value == null) unsupported(value)
    try {
      if (numbers.fitsInInt(value)) {
        val integer = numbers.asInt(value)
        return if (integer >= 0) integer else unsupported(value)
      }
      if (numbers.fitsInBigInteger(value)) return importBigInteger(numbers.asBigInteger(value), value)
    } catch (_: UnsupportedMessageException) {
      // Foreign implementations may reject conversion; report an argument error
      // rather than exposing an interpreter exception at the host boundary.
    }
    unsupported(value)
  }

  @TruffleBoundary
  private fun importBigInteger(integer: BigInteger, original: Any): Any {
    if (integer.signum() < 0) unsupported(original)
    return if (integer.bitLength() < 32) integer.toInt() else BigInt(integer)
  }

  private fun unsupported(value: Any?): Nothing =
    throw UnsupportedTypeException.create(arrayOf(value), "expected nonnegative integer")
}
