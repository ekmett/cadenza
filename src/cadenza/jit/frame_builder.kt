package cadenza.jit

import cadenza.Types
import cadenza.data.NeutralException
import com.oracle.truffle.api.dsl.Fallback
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.dsl.TypeSystemReference
import com.oracle.truffle.api.frame.*
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.NodeInfo
import com.oracle.truffle.api.nodes.UnexpectedResultException

val noFrameBuilders = arrayOf<FrameBuilder>() // can't make const because kotlin is silly

// this copies information from the VirtualFrame frame into a materialized frame
@TypeSystemReference(Types::class)
@NodeInfo(shortName = "FrameBuilder")
abstract class FrameBuilder(
  private val slot: FrameSlot,
  @field:Child protected var rhs: Code
) : Node() {

  @Suppress("NOTHING_TO_INLINE")
  inline fun build(frame: VirtualFrame, oldFrame: VirtualFrame) {
    execute(frame, 0, oldFrame)
  }

  abstract fun execute(frame: VirtualFrame, hack: Int, oldFrame: VirtualFrame): Any

  private fun allowsSlotKind(frame: VirtualFrame, kind: FrameSlotKind): Boolean {
    val currentKind = frame.frameDescriptor.getFrameSlotKind(slot)
    if (currentKind == FrameSlotKind.Illegal) {
      frame.frameDescriptor.setFrameSlotKind(slot, kind)
      return true
    }
    return currentKind == kind
  }

  protected fun allowsBooleanSlot(frame: VirtualFrame) = allowsSlotKind(frame, FrameSlotKind.Boolean)

  protected fun allowsIntegerSlot(frame: VirtualFrame) = allowsSlotKind(frame, FrameSlotKind.Int)

  // UnexpectedResultException lets us "accept" an answer on the slow path, but it forces me to give back an Object. small price to pay
  @Specialization(guards = ["allowsBooleanSlot(frame)"], rewriteOn = [UnexpectedResultException::class])
  @Throws(UnexpectedResultException::class)
  @Suppress("unused")
  internal fun buildBoolean(frame: VirtualFrame, @Suppress("UNUSED_PARAMETER") _hack: Int, oldFrame: VirtualFrame): Boolean {
    val result: Boolean
    try {
      result = rhs.executeBoolean(oldFrame)
    } catch (e: UnexpectedResultException) {
      frame.setObject(slot, e)
      throw e
    } catch (e: NeutralException) {
      frame.setObject(slot, e.get())
      return false // nonsense, the results are never used, result is to use @Specialization only
    }

    frame.setBoolean(slot, result)
    return result
  }

  @Specialization(guards = ["allowsIntegerSlot(frame)"], rewriteOn = [UnexpectedResultException::class])
  @Throws(UnexpectedResultException::class)
  @Suppress("unused")
  internal fun buildInteger(frame: VirtualFrame, @Suppress("UNUSED_PARAMETER") _hack: Int, oldFrame: VirtualFrame): Int {
    val result: Int
    try {
      result = rhs.executeInteger(oldFrame)
    } catch (e: UnexpectedResultException) {
      frame.setObject(slot, e)
      throw e
    } catch (e: NeutralException) {
      frame.setObject(slot, e.get())
      return 0
    }

    frame.setInt(slot, result)
    return result
  }

  @Fallback
  @Suppress("unused")
  internal fun buildObject(frame: VirtualFrame, @Suppress("UNUSED_PARAMETER") _hack: Int, oldFrame: VirtualFrame): Any? {
    val result = rhs.executeAny(oldFrame)
    frame.setObject(slot, result)
    return result
  }

  override fun isAdoptable() = false
}

@Suppress("NOTHING_TO_INLINE","unused")
inline fun put(slot: FrameSlot, value: Code): FrameBuilder = FrameBuilderNodeGen.create(slot, value)