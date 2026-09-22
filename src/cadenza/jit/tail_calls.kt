package cadenza.jit

import com.oracle.truffle.api.*
import com.oracle.truffle.api.frame.*
import com.oracle.truffle.api.nodes.*
import com.oracle.truffle.api.nodes.RepeatingNode.CONTINUE_LOOP_STATUS
import com.oracle.truffle.api.profiles.BranchProfile
import java.lang.Exception


class TailCallException(val fn: RootCallTarget, val args: Array<Any?>) : ControlFlowException() {}

class TailCheck : Node() {
  private val tailCallProfile: BranchProfile = BranchProfile.create()
  private val unrollProfile: BranchProfile = BranchProfile.create()

  fun tailCheck(frame: VirtualFrame, fn: RootCallTarget, args: Array<Any?>) {
    val root = rootNode
    if (root !is ClosureRootNode) {
      throw TailCallException(fn, args)
    }
    val mask = frame.getLong((root as ClosureRootNode).bloomFilterSlot)
    if (fn.rootNode !is CadenzaRootNode) {
      throw Exception("calling non-canedza rootNode w/ wrong convention!")
    }
    val targetMask = (fn.rootNode as CadenzaRootNode).mask
    if (mask and targetMask == targetMask) {
      tailCallProfile.enter()
      // hit, throw a tail call
      throw TailCallException(fn, args)
    } else {
      unrollProfile.enter()
      args[0] = mask
    }
  }
}

class DirectCallerNode(val callTarget: RootCallTarget) : Node() {
  @Child private var callNode: DirectCallNode = DirectCallNode.create(callTarget)
  @Child internal var loop = TailCallLoop()
  @Child var tailCheck = TailCheck()

  private val normalCallProfile = BranchProfile.create()
  private val tailCallProfile = BranchProfile.create()

  fun call(frame: VirtualFrame, args: Array<Any?>, tail_call: Boolean): Any? {
    return if (tail_call) {
      tailCheck.tailCheck(frame, callTarget, args)
      CallUtils.callDirect(callNode, args)
    } else {
      try {
        args[0] = 0L
        val x = CallUtils.callDirect(callNode, args)
        normalCallProfile.enter()
        x
      } catch (tailCall: TailCallException) {
        tailCallProfile.enter()
        loop.execute(tailCall)
      }
    }
  }

  companion object {
    @JvmStatic fun create(callTarget: RootCallTarget) = DirectCallerNode(callTarget)
  }
}



class IndirectCallerNode() : Node() {
  @Child private var callNode: IndirectCallNode = IndirectCallNode.create()
  @Child internal var loop = TailCallLoop()
  @Child var tailCheck = TailCheck()

  private val normalCallProfile = BranchProfile.create()
  private val tailCallProfile = BranchProfile.create()

  fun call(frame: VirtualFrame, callTarget: RootCallTarget, args: Array<Any?>, tail_call: Boolean): Any? {
    return if (tail_call) {
      tailCheck.tailCheck(frame, callTarget, args)
      CallUtils.callIndirect(callNode, callTarget, args)
    } else {
      try {
        args[0] = 0L
        val x = CallUtils.callIndirect(callNode, callTarget, args)
        normalCallProfile.enter()
        x
      } catch (tailCall: TailCallException) {
        tailCallProfile.enter()
        loop.execute(tailCall)
      }
    }
  }

  companion object {
    @JvmStatic fun create() = IndirectCallerNode()
  }
}


class TailCallLoop : Node() {
  @Child private var loopNode: LoopNode = Truffle.getRuntime().createLoopNode(
    TailCallRepeatingNode(FrameLayout().build()))

  fun execute(tailCall: TailCallException): Any? {
    val repeating = loopNode.repeatingNode as TailCallRepeatingNode
    val frame = Truffle.getRuntime().createVirtualFrame(emptyArray(), repeating.descriptor)
    repeating.setNextCall(frame, tailCall.fn, tailCall.args)
    loopNode.execute(frame)
    return repeating.getResult(frame)
  }
}

// current version copied from
// https://github.com/luna/enso/blob/master/engine/runtime/src/main/java/org/enso/interpreter/node/callable/dispatch/LoopingCallOptimiserNode.java
class TailCallRepeatingNode(val descriptor: FrameDescriptor) : Node(), RepeatingNode {
  val resultSlot = FrameLayout.TAIL_RESULT
  val functionSlot = FrameLayout.TAIL_FUNCTION
  val argsSlot = FrameLayout.TAIL_ARGUMENTS
  @Child var dispatchNode: DispatchCallTarget = DispatchCallTargetNodeGen.create()

  fun setNextCall(
    frame: VirtualFrame,
    fn: CallTarget,
    arguments: Array<Any?>) {
    frame.setObject(functionSlot, fn)
    frame.setObject(argsSlot, arguments)
  }

  fun getResult(frame: VirtualFrame): Any? {
    return frame.getObject(resultSlot)
  }

  private fun getNextFunction(frame: VirtualFrame): CallTarget {
    val result = frame.getObject(functionSlot) as CallTarget
    frame.setObject(functionSlot, null)
    return result
  }

  private fun getNextArgs(frame: VirtualFrame): Array<Any?> {
    val result = frame.getObject(argsSlot) as Array<Any?>
    frame.setObject(argsSlot, null)
    return result
  }

  override fun executeRepeating(frame: VirtualFrame): Boolean {
    return try {
      val fn = getNextFunction(frame)
      val args = getNextArgs(frame)
      args[0] = 0L
      frame.setObject(resultSlot, dispatchNode.executeDispatch(this, fn, args))
      false
    } catch (e: TailCallException) {
      setNextCall(frame, e.fn, e.args)
      true
    }
  }
}


class SelfTailCallLoop(body: ClosureBody): Node() {
  @Child private var loopNode: LoopNode = Truffle.getRuntime().createLoopNode(SelfTailCallRepeatingNode(body))

  fun executeOnce(frame: VirtualFrame): Any? =
    (loopNode.repeatingNode as SelfTailCallRepeatingNode).executeOnce(frame)

  fun execute(frame: VirtualFrame): Any? = loopNode.execute(frame)
}

class SelfTailCallRepeatingNode(
  @field:Child private var body: ClosureBody
): RepeatingNode, Node() {
  fun executeOnce(frame: VirtualFrame): Any? = body.execute(frame)

  override fun executeRepeating(frame: VirtualFrame): Boolean =
    throw UnsupportedOperationException("value-returning loop")

  override fun executeRepeatingWithValue(frame: VirtualFrame): Any? {
    return try {
      executeOnce(frame)
    } catch (e: TailCallException) {
      val closureRoot = rootNode as ClosureRootNode
      if (e.fn.rootNode === closureRoot) {
        closureRoot.buildFrame(e.args, frame)
        CONTINUE_LOOP_STATUS
      } else { throw e }
    }
  }
}
