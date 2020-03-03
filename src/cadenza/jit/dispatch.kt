package cadenza.jit

import cadenza.data.*
import cadenza.frame.DataFrame
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.dsl.Cached
import com.oracle.truffle.api.dsl.Fallback
import com.oracle.truffle.api.dsl.ReportPolymorphism
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.frame.MaterializedFrame
import com.oracle.truffle.api.frame.VirtualFrame
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


abstract class Whnf() : Node() {
  abstract fun execute(x: Any?): Any?

  abstract fun executeClosure(x: Any?): Closure

  @Specialization
  fun whnfIndirection(x: Indirection, @Cached("create()") whnf: Whnf): Any? {
    // this lets Indirection's fields be CompilationFinal
    if (!x.set) {
      CompilerDirectives.transferToInterpreterAndInvalidate()
      if (!x.set) throw Exception("whnf unset indirection")
    }
    val r = whnf.execute(x.value)
    // TODO: is this worth it? maybe only do it in interpreter?
    if (r !== x.value) x.value = r
    return r
  }

  @Fallback
  fun whnfId(x: Any?) = x
}

// TODO: dispatch on closure equality for static (no env or pap) closures?
// (would need to statically allocate them)
@ReportPolymorphism
abstract class Dispatch(@JvmField val argsSize: Int, val tail_call: Boolean = false) : Node() {
  // pre: ys.size == argsSize
  abstract fun executeDispatch(frame: VirtualFrame, fn: Closure, ys: Array<Any?>): Any?

  @Specialization(guards = [
    "fn.arity == argsSize",
    "fn.callTarget == cachedCallTarget"
  ], limit = "3")
  fun callDirect(frame: VirtualFrame, fn: Closure, ys: Array<Any?>?,
                 @Cached("fn.callTarget") cachedCallTarget: RootCallTarget,
                 // determined by fn.callTarget & fn.arity
                 @Cached("fn.papArgs.length") papSize: Int,
                 // determined by fn.callTarget
                 @Cached("fn.env != null") hasEnv: Boolean,
                 @Cached("create(cachedCallTarget)") callerNode: DirectCallerNode
                 ): Any? {
    val args = appendLSkip(if (hasEnv) 2 else 1, fn.papArgs, papSize, ys, argsSize)
    if (hasEnv) { args[1] = fn.env as DataFrame }
    // TODO: figure out how to avoid TailCallException if inlining
    return callerNode.call(frame, args, tail_call)
  }

  @Specialization(guards = [
    // TODO: can/should this be arity < argsSize?
    "fn.arity < argsSize",
    "fn.arity == arity",
    "fn.callTarget == cachedCallTarget"
  ])
  fun callDirectOverapplied(frame: VirtualFrame, fn: Closure, ys: Array<Any?>,
                            @Cached("fn.arity") arity: Int,
                            @Cached("fn.callTarget") cachedCallTarget: RootCallTarget,
                            // determined by fn.callTarget & fn.arity
                            @Cached("fn.papArgs.length") papSize: Int,
                            // determined by fn.callTarget
                            @Cached("fn.env != null") hasEnv: Boolean,
                            @Cached("create(cachedCallTarget)") callerNode: DirectCallerNode,
                            @Cached("createMinusTail(argsSize, arity)") dispatch: Dispatch): Any? {
    val args = appendLSkip(if (hasEnv) 2 else 1, fn.papArgs, papSize, ys, arity)
    if (hasEnv) { args[1] = fn.env as DataFrame }
    val y = callerNode.call(frame, args, false)
    val zs = ys.copyOfRange(arity, argsSize)
    return dispatch.executeDispatch(frame, y as Closure, zs)
  }

  @Specialization(guards = ["fn.arity > argsSize"])
  fun callUnderapplied(fn: Closure, ys: Array<Any?>): Any? {
    return fn.pap(ys)
  }

  // TODO: should callIndirect & callIndirectOverapplied be merged?

  // replaces => give up on callDirect once more than 3 variants
  // TODO: is replaces the right choice?
  @Specialization(guards = ["fn.arity == argsSize"], replaces = ["callDirect"])
  fun callIndirect(frame: VirtualFrame, fn: Closure, ys: Array<Any?>?,
                   @Cached("create()") callerNode: IndirectCallerNode): Any? {
    val hasEnv = fn.env != null
    val args = appendLSkip(if (hasEnv) 2 else 1, fn.papArgs, fn.papArgs.size, ys, argsSize)
    if (hasEnv) { args[1] = fn.env as DataFrame }
    return callerNode.call(frame, fn.callTarget, args, tail_call)
  }

  @Specialization(guards = [
    "fn.arity < argsSize",
    "arity == fn.arity"
  ], replaces = ["callDirectOverapplied"])
  fun callIndirectOverapplied(frame: VirtualFrame, fn: Closure, ys: Array<Any?>,
                              @Cached("fn.arity") arity: Int,
                              @Cached("create()") callerNode: IndirectCallerNode,
                              @Cached("createMinusTail(argsSize, arity)") dispatch: Dispatch): Any? {
    val xs = ys.copyOf(fn.arity)
    val zs = ys.copyOfRange(fn.arity, ys.size)
    val hasEnv = fn.env != null
    val args = appendLSkip(if (hasEnv) 2 else 1, fn.papArgs, fn.papArgs.size, xs, arity)
    if (hasEnv) { args[1] = fn.env as DataFrame }
    val y = callerNode.call(frame, fn.callTarget, args, false)
    return dispatch.executeDispatch(frame, y as Closure, zs)
  }

  fun createMinusTail(x: Int, y: Int): Dispatch = DispatchNodeGen.create(x - y, tail_call)
}