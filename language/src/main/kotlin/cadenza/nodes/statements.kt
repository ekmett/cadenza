package cadenza.nodes

import cadenza.NeutralException
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.frame.*
import com.oracle.truffle.api.instrumentation.*
import com.oracle.truffle.api.nodes.NodeInfo
import com.oracle.truffle.api.nodes.UnexpectedResultException

// two kinds of statements, one is top level, the other is an expression?

// block, print can be done at the top level or be treated as a result of IO whatever
// def is a top level statement

// these will typically be 'IO' actions
@GenerateWrapper
abstract class Stmt : CadenzaNode() {

  @GenerateWrapper.OutgoingConverter
  internal fun convertOutgoing(@Suppress("UNUSED_PARAMETER") obj: Any): Any? {
    return null
  }

  override fun isInstrumentable(): Boolean {
    return true
  }

  internal abstract fun execute(frame: VirtualFrame)

  override fun isAdoptable(): Boolean {
    return true
  }

  override fun createWrapper(probe: ProbeNode): InstrumentableNode.WrapperNode {
    return StmtWrapper(this, probe)
  }

  override fun hasTag(tag: Class<out Tag>?): Boolean {
    return tag == StandardTags.StatementTag::class.java || super.hasTag(tag)
  }
}

@NodeInfo(shortName = "Do")
class Do internal constructor(@field:Children internal var body: Array<Stmt>) : Stmt() {
  override fun execute(frame: VirtualFrame) {
    for (stmt in body) stmt.execute(frame)
  }
}

//TODO: use a better internal state management system like the generated code would
@NodeInfo(shortName = "Def")
abstract class Def(protected val slot: FrameSlot, @field:Child var arg: Code) : Stmt() {

  public override fun execute(frame: VirtualFrame) {
    executeDef(frame)
  }

  protected abstract fun executeDef(frame: VirtualFrame): Any?

  @Specialization(guards = ["allowsIntegerSlot(frame)"], rewriteOn = [UnexpectedResultException::class])
  @Throws(UnexpectedResultException::class)
  protected fun defInteger(frame: VirtualFrame): Int {
    try {
      val result = arg.executeInteger(frame)
      frame.setInt(slot, result)
      return result
    } catch (e: UnexpectedResultException) {
      frame.setObject(slot, e)
      throw e
    } catch (e: NeutralException) {
      frame.setObject(slot, e.get())
      return 0 // this result is never used
    }

  }

  @Specialization(guards = ["allowsBooleanSlot(frame)"], rewriteOn = [UnexpectedResultException::class])
  @Throws(UnexpectedResultException::class)
  protected fun defBoolean(frame: VirtualFrame): Boolean {
    try {
      val result = arg.executeBoolean(frame)
      frame.setBoolean(slot, result)
      return result
    } catch (e: UnexpectedResultException) {
      frame.setObject(slot, e)
      throw e
    } catch (e: NeutralException) {
      frame.setObject(slot, e.get())
      return false // never used
    }
  }

  @Specialization(replaces = ["defInteger", "defBoolean"])
  protected fun defObject(frame: VirtualFrame) {
    frame.setObject(slot, arg.executeAny(frame))
  }

  private fun allowsSlotKind(frame: VirtualFrame, kind: FrameSlotKind): Boolean {
    val currentKind = frame.frameDescriptor.getFrameSlotKind(slot)
    if (currentKind == FrameSlotKind.Illegal) {
      frame.frameDescriptor.setFrameSlotKind(slot, kind)
      return true
    }
    return currentKind == kind
  }

  protected fun allowsBooleanSlot(frame: VirtualFrame): Boolean {
    return allowsSlotKind(frame, FrameSlotKind.Boolean)
  }

  protected fun allowsIntegerSlot(frame: VirtualFrame): Boolean {
    return allowsSlotKind(frame, FrameSlotKind.Int)
  }
}

fun def(slot: FrameSlot, body: Code): Def {
  return DefNodeGen.create(slot, body)
}
