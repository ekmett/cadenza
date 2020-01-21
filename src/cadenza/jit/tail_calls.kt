package cadenza.jit

import com.oracle.truffle.api.*
import com.oracle.truffle.api.frame.*
import com.oracle.truffle.api.nodes.*
import com.oracle.truffle.api.profiles.BranchProfile
import java.lang.Exception


// list of ways custom loop node better than builtin
// * if reflection not allowed, can make variant that still compiles w/ interpreted parent
//   & doesn't need to use the parent's FrameWithoutBoxing for state
// * might be able to get tail call loop to manually unroll good
//   such that if f tail calls g, there's only one dispatch check per unrolled loop body
//   (reduces trampoline overhead by unrolling)
//   probably a decent amount of effort to get it to work
//   * TODO: try ExceptionLoopNode (that uses an exception to signal continue) again?
//     or make a specialized loopnode for tail calls?
//   but can probably unroll with normal RepeatingNode too?
// * less interpreter overhead


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
      repeatingNode = TailCallRepeatingNode()
      val slots = arrayOf(repeatingNode!!.argsSlot, repeatingNode!!.functionSlot, repeatingNode!!.resultSlot)
//      loopNode = createOptimizedLoopNode(repeatingNode!!, slots, slots)
//      // TODO: use createOSR here
      loopNode = Truffle.getRuntime().createLoopNode(repeatingNode)
    }
    val frame = Truffle.getRuntime().createVirtualFrame(null, repeatingNode!!.descriptor)
    repeatingNode!!.setNextCall(frame, tailCall.fn, tailCall.args)
    loopNode!!.execute(frame)
    return repeatingNode!!.getResult(frame)
  }
}

//
//class CallTargetCacheEntry(
//  @JvmField @field:Child var callNode: DirectCallNode,
//  @JvmField @CompilerDirectives.CompilationFinal val callTarget: CallTarget,
//  @JvmField @field:Child var next: CallTargetCacheEntry?) : Node() {}
//
//class CallTargetCache : Node() {
//  @Child var cache: CallTargetCacheEntry? = null
//  @Child var indirectCallNode = IndirectCallNode.create()
//
//  fun call(target: CallTarget, args: Array<Any?>): Any {
//    var entry = cache
//    while (entry != null) {
//      if (entry.callTarget == target) {
//        return CallUtils.callDirect(entry.callNode, args)
//      }
//      entry = entry.next
//    }
//    CompilerDirectives.transferToInterpreterAndInvalidate()
//    val callNode = DirectCallNode.create(target)
//    cache = CallTargetCacheEntry(callNode, target, cache)
//    adoptChildren()
//    return CallUtils.callIndirect(indirectCallNode, target, args)
//  }
//}

// current version copied from
// https://github.com/luna/enso/blob/master/engine/runtime/src/main/java/org/enso/interpreter/node/callable/dispatch/LoopingCallOptimiserNode.java
class TailCallRepeatingNode : Node(), RepeatingNode {
  val descriptor = FrameDescriptor()
  val resultSlot = descriptor.findOrAddFrameSlot("<TCO Function>", FrameSlotKind.Object)
  val functionSlot = descriptor.findOrAddFrameSlot("<TCO Result>", FrameSlotKind.Object)
  val argsSlot = descriptor.findOrAddFrameSlot("<TCO Arguments>", FrameSlotKind.Object)
  @Child var dispatchNode: DispatchCallTarget = DispatchCallTargetNodeGen.create()
//  @Child var cache = CallTargetCache()

  fun setNextCall(
    frame: VirtualFrame,
    fn: CallTarget,
    arguments: Array<Any?>) {
    frame.setObject(functionSlot, fn)
    frame.setObject(argsSlot, arguments)
  }

  fun getResult(frame: VirtualFrame): Any {
    return FrameUtil.getObjectSafe(frame, resultSlot)
  }

  private fun getNextFunction(frame: VirtualFrame): CallTarget {
    val result = FrameUtil.getObjectSafe(frame, functionSlot) as CallTarget
    frame.setObject(functionSlot, null)
    return result
  }

  private fun getNextArgs(frame: VirtualFrame): Array<Any?> {
    val result = FrameUtil.getObjectSafe(frame, argsSlot) as Array<Any?>
    frame.setObject(argsSlot, null)
    return result
  }

//  // explodeLoop doesn't explode the executeDispatch, so useless
//  // let's try our own dispatch (not using specialization) + kotlin inlining
//  @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE)
//  fun executeInner(fn1: CallTarget, args1: Array<Any?>): Any {
//    var fn = fn1
//    var args = args1
//    var k = 0
//    while (true) {
//      try {
//        return dispatchNode.executeDispatch(fn, args)
//      } catch (e: Exception) {
//        if (e is TailCallException && k < 2) {
//          fn = e.fn; args = e.args; k++
//        } else { throw e }
//      }
//    }
//  }

  override fun executeRepeating(frame: VirtualFrame): Boolean {
    return try {
      val fn = getNextFunction(frame)
      val args = getNextArgs(frame)
      frame.setObject(resultSlot, dispatchNode.executeDispatch(fn, args))
  //      frame.setObject(resultSlot, executeInner(fn, args))
      false
    } catch (e: TailCallException) {
      setNextCall(frame, e.fn, e.args)
      true
    }
  }
}


fun createOptimizedLoopNode(repeatingNode: RepeatingNode, readFrameSlots: Array<FrameSlot>, writtenFrameSlots: Array<FrameSlot>): LoopNode {
  val loopNode = Truffle.getRuntime().createLoopNode(repeatingNode)
//  if (!OzLanguage.ON_GRAAL) {
//    return loopNode
//  }
  return try {
    val klass: Class<*> = if (TruffleOptions.AOT) {
      Class.forName("org.graalvm.compiler.truffle.runtime.OptimizedOSRLoopNode\$OptimizedDefaultOSRLoopNode")
    } else {
      loopNode::class.java
    }
    val createOSRLoop = klass.getMethod("createOSRLoop",
      RepeatingNode::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Array<FrameSlot>::class.java, Array<FrameSlot>::class.java)
    createOSRLoop.invoke(null, repeatingNode, 3, 100_000, readFrameSlots, writtenFrameSlots) as LoopNode
  } catch (e: Exception) {
    println("Virtualizing OSR loop node creation failed, falling back to normal LoopNode: $e")
    loopNode
  }
}