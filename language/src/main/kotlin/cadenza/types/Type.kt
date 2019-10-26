package cadenza.types

import cadenza.values.Closure
import cadenza.values.BigInt
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.frame.FrameSlotKind
import com.oracle.truffle.api.interop.UnsupportedTypeException
import com.oracle.truffle.api.nodes.ExplodeLoop

import java.util.Objects

// eventually move to a more hindley-milner style model with quantifiers, but then we need subsumption, unification, etc.
// also this doesn't presuppose if we're heading towards dependently typed languages or towards haskell right now
abstract class Type internal constructor(val rep: FrameSlotKind // used to set the starting frameslotkind
) {
  open val arity: Int
    get() = 0

  @Throws(UnsupportedTypeException::class)
  abstract fun validate(t: Any?)  // checks the contract for a given type holds, for runtime argument passing, etc.

  @Throws(TypeError::class)
  fun match(expected: Type) {
    if (!equals(expected)) throw TypeError("type mismatch", this, expected)
  }

  internal fun unsupported(msg: String, vararg objects: Any?): Nothing {
    throw UnsupportedTypeException.create(objects, msg)
  }

  @CompilerDirectives.ValueType
  class Arr(val argument: Type, val result: Type) : Type(FrameSlotKind.Object) {
    override val arity: Int

    init {
      this.arity = result.arity + 1
    }

    @Throws(UnsupportedTypeException::class)
    override fun validate(t: Any?) {
      if (t !is Closure) unsupported("expected closure", t)
      if (this.argument != t.type)
        unsupported(
          "expected closure of type: " + this.toString() + ", but received one of type " + t.type.toString(),
          t
        )
    }

    override fun hashCode(): Int {
      return Objects.hash(argument, result)
    }

    override fun equals(other: Any?): Boolean {
      return other is Arr && argument == other.argument && result == other.result
    }
  }

  // IO actions represented ML-style as nullary functions
  @CompilerDirectives.ValueType
  class IO(val result: Type)// closure
    : Type(FrameSlotKind.Object) {

    @Throws(UnsupportedTypeException::class)
    override fun validate(t: Any?) {
      unsupported("expected io", t)
    }

    override fun hashCode(): Int {
      return result.hashCode()
    }

    override fun equals(other: Any?): Boolean {
      return other is Arr && result == other.result
    }
  }

  companion object {

    val Bool: Type = object : Type(FrameSlotKind.Boolean) {
      @Throws(UnsupportedTypeException::class)
      override fun validate(t: Any?) {
        if (t !is Boolean) unsupported("expected boolean", t)
      }
    }
    val Obj: Type = object : Type(FrameSlotKind.Object) {
      override fun validate(@Suppress("UNUSED_PARAMETER") t: Any?) {}
    }
    val Unit: Type = object : Type(FrameSlotKind.Object) {
      @Throws(UnsupportedTypeException::class)
      override fun validate(t: Any?) {
        if (kotlin.Unit != t) unsupported("expected unit", t)
      }
    } // always null. use byte or something else instead?
    val Nat: Type = object : Type(FrameSlotKind.Int) {
      @Throws(UnsupportedTypeException::class)
      override fun validate(t: Any?) {
        if (!(t is Int && t >= 0 || t is BigInt && t.isNatural()))
          unsupported("expected nat", t)
      }
    }
    val Action = IO(Unit)

    // assumes validity
    @ExplodeLoop
    fun after(t: Type, n: Int): Type {
      var current = t
      for (i in 0 until n)
        current = (current as Type.Arr).result
      return current
    }
  }

}
