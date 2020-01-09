package cadenza.jit

import cadenza.data.*
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.dsl.Cached
import com.oracle.truffle.api.dsl.ReportPolymorphism
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.frame.MaterializedFrame
import com.oracle.truffle.api.nodes.*


// expects fully applied & doesn't trampoline
// just an inline cache of DirectCallNodes & IndirectCallNodes
@ReportPolymorphism
abstract class DispatchCallTarget : Node() {
  abstract fun executeDispatch(callTarget: CallTarget, ys: Array<Any?>): Any

  @Specialization(guards = [
    "callTarget == cachedCallTarget"
  ], limit = "3")
  fun callDirect(callTarget: CallTarget, ys: Array<Any?>?,
                 @Cached("callTarget") cachedCallTarget: CallTarget,
                 @Cached("create(cachedCallTarget)") callNode: DirectCallNode): Any? {
    return CallUtils.callDirect(callNode, ys)
  }

  @Specialization
  fun callIndirect(callTarget: CallTarget, ys: Array<Any?>?,
                   @Cached("create()") callNode: IndirectCallNode): Any? {
    return CallUtils.callIndirect(callNode, callTarget, ys)
  }
}


// TODO: dispatch on closure equality for static (no env or pap) closures?
// (would need to statically allocate them)
@ReportPolymorphism
abstract class Dispatch(@JvmField val argsSize: Int, val tail_call: Boolean = false) : Node() {

  // pre: ys.size == argsSize
  abstract fun executeDispatch(closure: Closure, ys: Array<Any?>): Any?

  @Specialization(guards = [
    "fn.arity == argsSize",
    "fn.callTarget == cachedCallTarget"
  ], limit = "3")
  fun callDirect(fn: Closure, ys: Array<Any?>?,
                 @Cached("fn.callTarget") cachedCallTarget: RootCallTarget,
                 // determined by fn.callTarget & fn.arity
                 @Cached("fn.papArgs.length") papSize: Int,
                 // determined by fn.callTarget
                 @Cached("fn.env != null") hasEnv: Boolean,
                 @Cached("create(cachedCallTarget)") callerNode: DirectCallerNode
                 ): Any {
    val args =
      if (hasEnv) consAppendL(fn.env as MaterializedFrame, fn.papArgs, papSize, ys, argsSize)
      else appendL(fn.papArgs, papSize, ys, argsSize)
    // TODO: don't need to create callerNode if tail call
    // split DispatchTailCall out?
    // TODO: figure out how to avoid TailCallException if inlining
    if (tail_call) {
      throw TailCallException(cachedCallTarget, args)
    } else {
      return callerNode.call(args)
    }
  }

  @Specialization(guards = [
    // TODO: can/should this be arity < argsSize?
    "fn.arity < argsSize",
    "fn.arity == arity",
    "fn.callTarget == cachedCallTarget"
  ])
  fun callDirectOverapplied(fn: Closure, ys: Array<Any?>,
                            @Cached("fn.arity") arity: Int,
                            @Cached("fn.callTarget") cachedCallTarget: RootCallTarget,
                            // determined by fn.callTarget & fn.arity
                            @Cached("fn.papArgs.length") papSize: Int,
                            // determined by fn.callTarget
                            @Cached("fn.env != null") hasEnv: Boolean,
                            @Cached("create(cachedCallTarget)") callerNode: DirectCallerNode,
                            @Cached("createMinusTail(argsSize, arity)") dispatch: Dispatch): Any? {
    val args =
      if (hasEnv) consAppendL(fn.env as MaterializedFrame, fn.papArgs, papSize, ys, arity)
      else appendL(fn.papArgs, papSize, ys, arity)

    val y = callerNode.call(args)
    val zs = ys.copyOfRange(arity, argsSize)
    return dispatch.executeDispatch(y as Closure, zs)
  }

  @Specialization(guards = ["fn.arity > argsSize"])
  fun callUnderapplied(fn: Closure, ys: Array<Any?>): Any? {
    return fn.pap(ys)
  }

  // TODO: should callIndirect & callIndirectOverapplied be merged?

  // replaces => give up on callDirect once more than 3 variants
  // TODO: is replaces the right choice?
  @Specialization(guards = ["fn.arity == argsSize"], replaces = ["callDirect"])
  fun callIndirect(fn: Closure, ys: Array<Any?>?,
                   @Cached("create()") callerNode: IndirectCallerNode): Any? {
    val args =
      if (fn.env != null) consAppendL(fn.env, fn.papArgs, fn.papArgs.size, ys, argsSize)
      else appendL(fn.papArgs, fn.papArgs.size, ys, argsSize)
    if (tail_call) {
      throw TailCallException(fn.callTarget, args)
    } else {
      return callerNode.call(fn.callTarget, args)
    }
  }

  @Specialization(guards = [
    "fn.arity < argsSize",
    "arity == fn.arity"
  ], replaces = ["callDirectOverapplied"])
  fun callIndirectOverapplied(fn: Closure, ys: Array<Any?>,
                              @Cached("fn.arity") arity: Int,
                              @Cached("create()") callerNode: IndirectCallerNode,
                              @Cached("createMinusTail(argsSize, arity)") dispatch: Dispatch): Any? {
    val xs = ys.copyOf(fn.arity)
    val zs = ys.copyOfRange(fn.arity, ys.size)
    val args =
      if (fn.env != null) consAppendL(fn.env, fn.papArgs, fn.papArgs.size, xs, arity)
      else appendL(fn.papArgs, fn.papArgs.size, xs, arity)
    val y = callerNode.call(fn.callTarget, args)
    return dispatch.executeDispatch(y as Closure, zs)
  }

  fun createMinusTail(x: Int, y: Int): Dispatch = DispatchNodeGen.create(x - y, tail_call)
}