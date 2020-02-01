package cadenza.jit

import com.oracle.truffle.api.*
import com.oracle.truffle.api.dsl.Cached
import com.oracle.truffle.api.dsl.ReportPolymorphism
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.frame.*
import com.oracle.truffle.api.nodes.*
import com.oracle.truffle.api.nodes.RepeatingNode.CONTINUE_LOOP_STATUS
import com.oracle.truffle.api.profiles.BranchProfile
import java.lang.Exception
import java.lang.reflect.Method


// list of ways custom loop node better than builtin
// * if reflection not allowed, can make variant that still compiles w/ interpreted parent
//   & doesn't need to use the parent's FrameWithoutBoxing for state
// * less interpreter overhead
// * less messy not using rootNode's fd


class TailCallException(val fn: CallTarget, @CompilerDirectives.CompilationFinal(dimensions = 1) val args: Array<Any?>) : ControlFlowException() {}

class DirectCallerNode(callTarget: CallTarget) : Node() {
  @Child private var callNode: DirectCallNode = DirectCallNode.create(callTarget)
  @Child internal var loop = TailCallLoop()

  private val normalCallProfile = BranchProfile.create()
  private val tailCallProfile = BranchProfile.create()

  fun call(args: Array<Any?>): Any {
    return try {
      val x = CallUtils.callDirect(callNode, args)
      normalCallProfile.enter()
      x
    } catch (tailCall: TailCallException) {
      tailCallProfile.enter()
      loop.execute(tailCall)
    }
  }

  companion object {
    @JvmStatic fun create(callTarget: CallTarget) = DirectCallerNode(callTarget)
  }
}



class IndirectCallerNode() : Node() {
  @Child private var callNode: IndirectCallNode = IndirectCallNode.create()
  @Child internal var loop = TailCallLoop()

  private val normalCallProfile = BranchProfile.create()
  private val tailCallProfile = BranchProfile.create()

  fun call(fn: CallTarget, args: Array<Any?>): Any {
    return try {
      val x = CallUtils.callIndirect(callNode, fn, args)
      normalCallProfile.enter()
      x
    } catch (tailCall: TailCallException) {
      tailCallProfile.enter()
      loop.execute(tailCall)
    }
  }

  companion object {
    @JvmStatic fun create() = IndirectCallerNode()
  }
}


class TailCallLoop() : Node() {
  @Child var loopNode: LoopNode? = null
  @Child var repeatingNode: TailCallRepeatingNode? = null

  fun execute(tailCall: TailCallException): Any {
    if (loopNode == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate()
      repeatingNode = TailCallRepeatingNode(rootNode.frameDescriptor)
      val slots = arrayOf(repeatingNode!!.functionSlot, repeatingNode!!.argsLenSlot)
//      loopNode = createOptimizedLoopNode(repeatingNode!!, slots, slots)
      loopNode = Truffle.getRuntime().createLoopNode(repeatingNode!!)
      adoptChildren()
    }
    val frame = Truffle.getRuntime().createVirtualFrame(null, repeatingNode!!.descriptor)
    repeatingNode!!.setup(frame)
    repeatingNode!!.setNextCall(frame, tailCall.fn, tailCall.args)
    return loopNode!!.execute(frame)
  }
}

// expects fully applied & doesn't trampoline
// just an inline cache of DirectCallNodes & IndirectCallNodes
@ReportPolymorphism
abstract class DispatchCallTarget(val repeatingNode: TailCallRepeatingNode) : Node() {
  abstract fun executeDispatch(frame: VirtualFrame, callTarget: CallTarget, ys: Array<Any?>): Any

  @Specialization(guards = [
    "callTarget == cachedCallTarget"
  ], limit = "3")
  fun callDirect(frame: VirtualFrame, callTarget: CallTarget, ys: Array<Any?>?,
                 @Cached("callTarget") cachedCallTarget: CallTarget,
                 @Cached("create(cachedCallTarget)") callNode: DirectCallNode): Any? {
    try {
      return CallUtils.callDirect(callNode, ys)
    } catch (e: TailCallException) {
      repeatingNode.setNextCall(frame, e.fn, e.args)
      return CONTINUE_LOOP_STATUS
    }
  }

  @Specialization
  fun callIndirect(frame: VirtualFrame, callTarget: CallTarget, ys: Array<Any?>?,
                   @Cached("create()") callNode: IndirectCallNode): Any? {
    try {
      return CallUtils.callIndirect(callNode, callTarget, ys)
    } catch (e: TailCallException) {
      repeatingNode.setNextCall(frame, e.fn, e.args)
      return CONTINUE_LOOP_STATUS
    }
  }
}


// current version copied from
// https://github.com/luna/enso/blob/master/engine/runtime/src/main/java/org/enso/interpreter/node/callable/dispatch/LoopingCallOptimiserNode.java
class TailCallRepeatingNode(val descriptor: FrameDescriptor) : Node(), RepeatingNode {
  val functionSlot = descriptor.findOrAddFrameSlot("<TCO Function>", FrameSlotKind.Object)
  val argsLenSlot = descriptor.findOrAddFrameSlot("<TCO Args Len>", FrameSlotKind.Int)
  @CompilerDirectives.CompilationFinal(dimensions = 1) var argsSlots: Array<FrameSlot> = arrayOf()
  @Child var dispatchNode: DispatchCallTarget = DispatchCallTargetNodeGen.create(this)

  fun setup(frame: VirtualFrame) {
//    frame.setObject(argsSlot, arrayOfNulls<Any?>(maxArgsLen))
  }

  @ExplodeLoop
  fun setNextCall(
    frame: VirtualFrame,
    fn: CallTarget,
    arguments: Array<Any?>) {
    frame.setObject(functionSlot, fn)
    if (arguments.size > argsSlots.size) {
      CompilerDirectives.transferToInterpreterAndInvalidate()
      argsSlots = (0 until arguments.size).map { descriptor.findOrAddFrameSlot("<TCO Arg $it>") }.toTypedArray()
    }
    frame.setInt(argsLenSlot, arguments.size)
    for (ix in 0 until argsSlots.size) {
      frame.setObject(argsSlots[ix], if (ix < arguments.size) arguments[ix] else null)
    }
  }

  private fun getNextFunction(frame: VirtualFrame): CallTarget {
    val result = FrameUtil.getObjectSafe(frame, functionSlot) as CallTarget
    frame.setObject(functionSlot, null)
    return result
  }

  @ExplodeLoop
  private fun getNextArgs(frame: VirtualFrame): Array<Any?> {
    // making this be `frame.getInt(argsLenSlot)` breaks escape analysis??
    val argsSize = argsSlots.size //frame.getInt(argsLenSlot)
    val array = arrayOfNulls<Any?>(argsSize)
    for (ix in 0 until argsSlots.size) {
      if (ix < argsSize) {
        array[ix] = frame.getObject(argsSlots[ix])
      }
      frame.setObject(argsSlots[ix], null)
    }
    return array
  }

  override fun executeRepeating(frame: VirtualFrame?): Boolean {
    throw Exception("executeRepeating called over executeRepeatingWithValue")
  }

  override fun executeRepeatingWithValue(frame: VirtualFrame): Any {
    val fn = getNextFunction(frame)
    val args = getNextArgs(frame)
    return dispatchNode.executeDispatch(frame, fn, args)
  }
}

class DummyRepeatingNode() : Node(), RepeatingNode {
  override fun executeRepeating(frame: VirtualFrame?): Boolean = false
}

val createOSRLoop: Method? by lazy {
  try {
    val klass: Class<*> = if (TruffleOptions.AOT) {
      Class.forName("org.graalvm.compiler.truffle.runtime.OptimizedOSRLoopNode\$OptimizedDefaultOSRLoopNode")
    } else {
      Truffle.getRuntime().createLoopNode(DummyRepeatingNode())::class.java
    }
    klass.getMethod("createOSRLoop", RepeatingNode::class.java,
      Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
      Array<FrameSlot>::class.java, Array<FrameSlot>::class.java)
  } catch (e: Exception) {
    println("Virtualizing OSR loop node creation failed, falling back to normal LoopNode: $e")
    null
  }
}

// Note: to avoid "java.lang.AssertionError: Frames should never shrink.", you must:
// 1. adopt the resulting LoopNode before executing it
// 2. use the LoopNode's (parent's) rootNode.frameDescriptor as the fd for the frame you pass to LoopNode.execute
// (but only the subset readFrameSlots/writtenFrameSlots will be available in the loop)
fun createOptimizedLoopNode(repeatingNode: RepeatingNode, readFrameSlots: Array<FrameSlot>, writtenFrameSlots: Array<FrameSlot>): LoopNode {
  val loopNode = Truffle.getRuntime().createLoopNode(repeatingNode)
//  if (!OzLanguage.ON_GRAAL) {
//    return loopNode
//  }
  val m = createOSRLoop
  return if (m == null) {
    loopNode
  } else {
    m.invoke(null, repeatingNode, 3, 100_000, readFrameSlots, writtenFrameSlots) as LoopNode
  }
}