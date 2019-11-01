package cadenza

import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.frame.FrameSlotKind
import com.oracle.truffle.api.interop.UnsupportedTypeException
import com.oracle.truffle.api.nodes.ExplodeLoop

@Suppress("unused")
class TypeError(
  message: String,
  val actual: Type? = null,
  val expected: Type? = null
) : Exception(message) {
  constructor(message: String, cause: Exception?, actual: Type? = null, expected: Type? = null) : this(message, actual, expected) {
    initCause(cause)
  }
  companion object {
    const val serialVersionUID: Long = 212674730538525189L
  }
}

// eventually move to a more hindley-milner style model with quantifiers, but then we need subsumption, unification, etc.
// also this doesn't presuppose if we're heading towards dependently typed languages or towards haskell right now
abstract class Type protected constructor(@Suppress("unused") val rep: FrameSlotKind) {
  open val arity: Int
    get() = 0

  @Throws(UnsupportedTypeException::class)
  abstract fun validate(t: Any?)  // checks the contract for a given type holds, for runtime argument passing, etc.

  @Throws(TypeError::class)
  @Suppress("unused")
  fun match(expected: Type) {
    if (!equals(expected)) throw TypeError("type mismatch", this, expected)
  }

  @Suppress("NOTHING_TO_INLINE")
  internal inline fun unsupported(msg: String, vararg objects: Any?): Nothing =
    throw UnsupportedTypeException.create(objects, msg)

  @CompilerDirectives.ValueType
  data class Arr(val argument: Type, val result: Type) : Type(FrameSlotKind.Object) {
    override val arity: Int = result.arity + 1
    @Throws(UnsupportedTypeException::class)
    override fun validate(t: Any?) {
      if (t !is Closure) unsupported("expected closure", t)
      if (this.argument != t.type) unsupported("wrong closure type")
    }
  }

  // IO actions represented ML-style as nullary functions
  @CompilerDirectives.ValueType
  data class IO(val result: Type) : Type(FrameSlotKind.Object) {
    @Throws(UnsupportedTypeException::class)
    override fun validate(t: Any?) = todo("io")
  }

  object Bool : Type(FrameSlotKind.Boolean) {
    @Throws(UnsupportedTypeException::class)
    override fun validate(t: Any?) { if (t !is Boolean) unsupported("expected boolean", t)
    }
  }

  @Suppress("unused")
  object Obj : Type(FrameSlotKind.Object) {
    override fun validate(@Suppress("UNUSED_PARAMETER") t: Any?) {}
  }

  object UnitTy : Type(FrameSlotKind.Object) {
    @Throws(UnsupportedTypeException::class)
    override fun validate(t: Any?) { if (Unit != t) unsupported("expected unit", t)
    }
  }

  object Nat : Type(FrameSlotKind.Int) {
    @Throws(UnsupportedTypeException::class)
    override fun validate(t: Any?) {
      if (!(t is Int && t >= 0 || t is BigInt && t.isNatural())) unsupported("expected nat", t)
    }
  }

  companion object {
    val Action = IO(UnitTy)
  }
}

// assumes validity
@ExplodeLoop
@Suppress("unused")
fun Type.after(n: Int): Type {
  var current = this
  for (i in 0 until n)
    current = (current as Type.Arr).result
  return current
}


