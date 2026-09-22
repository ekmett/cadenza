package cadenza.jit

import cadenza.RuntimeError
import com.oracle.truffle.api.*
import com.oracle.truffle.api.bytecode.BytecodeNode
import com.oracle.truffle.api.frame.*
import com.oracle.truffle.api.nodes.*
import com.oracle.truffle.api.nodes.RepeatingNode.CONTINUE_LOOP_STATUS
import com.oracle.truffle.api.profiles.BranchProfile
import java.lang.Exception


open class TailCallException(val fn: RootCallTarget, val args: Array<Any?>) : ControlFlowException()

/** Only unsourced builtin targets need a fallback after their caller has tail-returned. */
class BuiltinTailCallException(fn: RootCallTarget, args: Array<Any?>, private var origin: Node) :
  TailCallException(fn, args) {
  private var bytecodeIndex = -1

  fun atBytecode(node: BytecodeNode, index: Int) {
    if (bytecodeIndex < 0) {
      origin = node
      bytecodeIndex = index
    }
  }

  @CompilerDirectives.TruffleBoundary
  fun locate(error: RuntimeError): RuntimeError {
    // Retained bytecode positions must follow any interpreter replacement during source loading.
    val source = if (bytecodeIndex >= 0) (origin as BytecodeNode).getBytecodeLocation(bytecodeIndex)
      .ensureSourceInformation().sourceLocation else origin.encapsulatingSourceSection
    return error.at(source)
  }
}

class TailCheck : Node() {
  private val tailCallProfile: BranchProfile = BranchProfile.create()
  private val unrollProfile: BranchProfile = BranchProfile.create()

  private fun bounce(fn: RootCallTarget, args: Array<Any?>): TailCallException =
    if (fn.rootNode is BuiltinRootNode) BuiltinTailCallException(fn, args, this)
    else TailCallException(fn, args)

  fun tailCheck(frame: VirtualFrame, fn: RootCallTarget, args: Array<Any?>) {
    val root = rootNode
    if (root !is CadenzaRootNode || !root.hasTailCallFrame) {
      throw bounce(fn, args)
    }
    val mask = frame.getLong(FrameLayout.BLOOM_FILTER)
    if (fn.rootNode !is CadenzaRootNode) {
      throw Exception("calling non-canedza rootNode w/ wrong convention!")
    }
    val targetMask = (fn.rootNode as CadenzaRootNode).mask
    if (mask and targetMask == targetMask) {
      tailCallProfile.enter()
      // hit, throw a tail call
      throw bounce(fn, args)
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
    repeating.setNextCall(frame, tailCall)
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
    call: TailCallException) {
    frame.setObject(functionSlot, call.fn)
    frame.setObject(argsSlot, call)
  }

  fun getResult(frame: VirtualFrame): Any? {
    return frame.getObject(resultSlot)
  }

  private fun getNextFunction(frame: VirtualFrame): CallTarget {
    val result = frame.getObject(functionSlot) as CallTarget
    frame.setObject(functionSlot, null)
    return result
  }

  private fun getNextCall(frame: VirtualFrame): TailCallException {
    val result = frame.getObject(argsSlot) as TailCallException
    frame.setObject(argsSlot, null)
    return result
  }

  override fun executeRepeating(frame: VirtualFrame): Boolean {
    return try {
      val fn = getNextFunction(frame)
      val call = getNextCall(frame)
      val args = call.args
      args[0] = 0L
      val result = try {
        dispatchNode.executeDispatch(this, fn, args)
      } catch (error: RuntimeError) {
        throw if (call is BuiltinTailCallException) call.locate(error) else error
      }
      frame.setObject(resultSlot, result)
      false
    } catch (e: TailCallException) {
      setNextCall(frame, e)
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
      if (closureRoot.isSelfCall(e.fn)) {
        closureRoot.buildFrame(e.args, frame)
        CONTINUE_LOOP_STATUS
      } else { throw e }
    }
  }
}
