package cadenza.jit

import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.frame.Frame
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.FrameSlotKind

/** Allocate indexed slots during compilation, then freeze the descriptor for execution. */
class FrameLayout {
  private val builder = FrameDescriptor.newBuilder()
  private val locals = mutableMapOf<String, Int>()

  init {
    builder.addSlot(FrameSlotKind.Long, "<TCO Bloom Filter>", null)
    builder.addSlot(FrameSlotKind.Object, "<TCO Result>", null)
    builder.addSlot(FrameSlotKind.Object, "<TCO Function>", null)
    builder.addSlot(FrameSlotKind.Object, "<TCO Arguments>", null)
  }

  fun slot(name: String): Int = locals.getOrPut(name) {
    builder.addSlot(FrameSlotKind.Illegal, name, null)
  }

  fun build(): FrameDescriptor = builder.build()

  companion object {
    const val BLOOM_FILTER = 0
    const val TAIL_RESULT = 1
    const val TAIL_FUNCTION = 2
    const val TAIL_ARGUMENTS = 3
  }
}

/** Primitive locals widen once when an exceptional value needs object storage. */
object FrameAccess {
  fun read(frame: Frame, slot: Int): Any? = frame.getValue(slot)

  fun write(frame: Frame, slot: Int, value: Any?) {
    val descriptor = frame.frameDescriptor
    val kind = descriptor.getSlotKind(slot)
    when {
      value is Int && (kind == FrameSlotKind.Int || kind == FrameSlotKind.Illegal) -> {
        if (kind == FrameSlotKind.Illegal) {
          CompilerDirectives.transferToInterpreterAndInvalidate()
          descriptor.setSlotKind(slot, FrameSlotKind.Int)
        }
        frame.setInt(slot, value)
      }
      value is Boolean && (kind == FrameSlotKind.Boolean || kind == FrameSlotKind.Illegal) -> {
        if (kind == FrameSlotKind.Illegal) {
          CompilerDirectives.transferToInterpreterAndInvalidate()
          descriptor.setSlotKind(slot, FrameSlotKind.Boolean)
        }
        frame.setBoolean(slot, value)
      }
      else -> {
        if (kind != FrameSlotKind.Object) {
          CompilerDirectives.transferToInterpreterAndInvalidate()
          descriptor.setSlotKind(slot, FrameSlotKind.Object)
        }
        frame.setObject(slot, value)
      }
    }
  }
}
