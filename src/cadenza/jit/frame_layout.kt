package cadenza.jit

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
    builder.addSlot(FrameSlotKind.Object, name, null)
  }

  fun build(): FrameDescriptor = builder.build()

  companion object {
    const val BLOOM_FILTER = 0
    const val TAIL_RESULT = 1
    const val TAIL_FUNCTION = 2
    const val TAIL_ARGUMENTS = 3
  }
}
