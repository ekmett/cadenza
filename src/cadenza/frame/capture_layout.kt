package cadenza.frame

import cadenza.Language
import cadenza.jit.FrameAccess
import cadenza.semantics.Type
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.frame.FrameSlotTypeException
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.staticobject.DefaultStaticProperty
import com.oracle.truffle.api.staticobject.StaticShape

/** A lambda's capture representation, fixed while its body is compiled. */
class CaptureLayout(language: Language, types: Array<Type>) {
  @CompilationFinal(dimensions = 1)
  private val fields = Array(types.size) { CaptureField(it, types[it]) }
  private val shape = StaticShape.newBuilder(language).also { builder ->
    fields.forEach { it.register(builder) }
  }.build()

  @ExplodeLoop
  fun capture(frame: VirtualFrame, sourceSlots: Array<Int>): DataFrame {
    check(sourceSlots.size == fields.size)
    val storage = shape.factory.create()
    for (index in fields.indices) fields[index].initialize(storage, FrameAccess.read(frame, sourceSlots[index]))
    return CapturedFrame(this, storage)
  }

  /** Read with the lambda's constant layout so property offsets fold during compilation. */
  fun read(environment: DataFrame, slot: Int): Any? {
    val captured = environment as CapturedFrame
    assert(captured.layout === this)
    return fields[slot].read(captured.storage)
  }

  private class CaptureField(index: Int, val type: Type) {
    private val objectValue = DefaultStaticProperty("capture_${index}_object")
    private val primitiveValue = DefaultStaticProperty("capture_${index}_primitive")
    private val hasPrimitive = DefaultStaticProperty("capture_${index}_tag")
    private val isPrimitive = type == Type.Nat || type == Type.Bool

    fun register(builder: StaticShape.Builder) {
      builder.property(objectValue, Any::class.java, true)
      if (isPrimitive) {
        builder.property(primitiveValue, if (type == Type.Nat) Int::class.javaPrimitiveType else Boolean::class.javaPrimitiveType, true)
        builder.property(hasPrimitive, Boolean::class.javaPrimitiveType, true)
      }
    }

    // Final properties are written exactly once before the environment escapes. Nat can
    // also contain a BigInt, neutral, or recursive indirection, so retain an object arm.
    fun initialize(storage: Any, value: Any?) {
      if (type == Type.Nat && value is Int) {
        primitiveValue.setInt(storage, value)
        hasPrimitive.setBoolean(storage, true)
      } else if (type == Type.Bool && value is Boolean) {
        primitiveValue.setBoolean(storage, value)
        hasPrimitive.setBoolean(storage, true)
      } else {
        objectValue.setObject(storage, value)
        if (isPrimitive) hasPrimitive.setBoolean(storage, false)
      }
    }

    fun isInt(storage: Any) = type == Type.Nat && hasPrimitive.getBoolean(storage)
    fun isObject(storage: Any) = !isPrimitive || !hasPrimitive.getBoolean(storage)
    fun read(storage: Any): Any? = when {
      !isPrimitive || !hasPrimitive.getBoolean(storage) -> objectValue.getObject(storage)
      type == Type.Nat -> primitiveValue.getInt(storage)
      else -> primitiveValue.getBoolean(storage)
    }
    fun readInt(storage: Any): Int {
      if (!isInt(storage)) throw FrameSlotTypeException()
      return primitiveValue.getInt(storage)
    }
    fun readObject(storage: Any): Any? {
      if (!isObject(storage)) throw FrameSlotTypeException()
      return objectValue.getObject(storage)
    }
  }

  private class CapturedFrame(val layout: CaptureLayout, val storage: Any) : DataFrame {
    override fun getValue(slot: Slot): Any? = layout.fields[slot].read(storage)
    override fun getInteger(slot: Slot): Int = layout.fields[slot].readInt(storage)
    override fun isInteger(slot: Slot) = layout.fields[slot].isInt(storage)
    override fun getObject(slot: Slot): Any? = layout.fields[slot].readObject(storage)
    override fun isObject(slot: Slot) = layout.fields[slot].isObject(storage)
    override fun getDouble(slot: Slot): Double = throw FrameSlotTypeException()
    override fun isDouble(slot: Slot) = false
    override fun getFloat(slot: Slot): Float = throw FrameSlotTypeException()
    override fun isFloat(slot: Slot) = false
    override fun getLong(slot: Slot): Long = throw FrameSlotTypeException()
    override fun isLong(slot: Slot) = false
  }
}
