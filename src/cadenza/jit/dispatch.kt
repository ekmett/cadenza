package cadenza.jit

import cadenza.data.*
import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.dsl.Cached
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.frame.MaterializedFrame
import com.oracle.truffle.api.nodes.DirectCallNode
import com.oracle.truffle.api.nodes.IndirectCallNode
import com.oracle.truffle.api.nodes.Node

abstract class Dispatch(@JvmField val argsSize: Int) : Node() {

  // pre: ys.size == argsSize
  abstract fun executeDispatch(closure: Closure, ys: Array<Any?>): Any?

  // TODO: variant for arity < argsSize that keeps a cached Dispatch (per specialization) (callDirectOverapplied)
  @Specialization(guards = [
    "fn.arity == argsSize",
    "fn.callTarget == cachedCallTarget"
  ], limit = "3")
  fun callDirect(fn: Closure, ys: Array<Any?>,
                 @Cached("fn.callTarget") cachedCallTarget: RootCallTarget,
                 // determined by fn.callTarget & fn.arity
                 @Cached("fn.papArgs.length") papSize: Int,
                 // determined by fn.callTarget
                 @Cached("fn.env != null") hasEnv: Boolean,
                 @Cached("create(cachedCallTarget)") callNode: DirectCallNode): Any {
    val args =
      if (hasEnv) consAppendL(fn.env as MaterializedFrame, fn.papArgs, papSize, ys, argsSize)
      else appendL(fn.papArgs, papSize, ys, argsSize)
    return CallUtils.callDirect(callNode, args)
  }

  @Specialization(guards = [
    // TODO: can this be arity < argsSize?
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
                            @Cached("create(cachedCallTarget)") callNode: DirectCallNode,
                            @Cached("createMinus(argsSize, arity)") dispatch: Dispatch): Any? {
    val args =
      if (hasEnv) consAppendL(fn.env as MaterializedFrame, fn.papArgs, papSize, ys, arity)
      else appendL(fn.papArgs, papSize, ys, arity)

    val y = CallUtils.callDirect(callNode, args)
    val zs = ys.copyOfRange(arity, argsSize)
    return dispatch.executeDispatch(y as Closure, zs)
  }

  @Specialization(guards = ["fn.arity > argsSize"])
  fun callUnderapplied(fn: Closure, ys: Array<Any?>): Any? {
    return fn.pap(ys)
  }

  // replaces => give up on callDirect once more than 3 variants
  // TODO: is replaces the right choice?
  @Specialization(guards = ["fn.arity == argsSize"], replaces = ["callDirect"])
  fun callIndirect(fn: Closure, ys: Array<Any?>,
                   @Cached("create()") callNode: IndirectCallNode): Any? {
    val args =
      if (fn.env != null) consAppendL(fn.env, fn.papArgs, fn.papArgs.size, ys, argsSize)
      else appendL(fn.papArgs, fn.papArgs.size, ys, argsSize)
    return CallUtils.callIndirect(callNode,fn.callTarget,args)
  }

  @Specialization(guards = [
    "fn.arity < argsSize",
    "arity == fn.arity"
  ])
  fun callIndirectOverapplied(fn: Closure, ys: Array<Any?>,
                              @Cached("fn.arity") arity: Int,
                              @Cached("create()") callNode: IndirectCallNode,
                              @Cached("createMinus(argsSize, arity)") dispatch: Dispatch): Any? {
    val xs = ys.copyOf(fn.arity)
    val zs = ys.copyOfRange(fn.arity, ys.size)
    val args =
      if (fn.env != null) consAppendL(fn.env, fn.papArgs, fn.papArgs.size, xs, arity)
      else appendL(fn.papArgs, fn.papArgs.size, xs, arity)
    val y = CallUtils.callIndirect(callNode,fn.callTarget,args)
    return dispatch.executeDispatch(y as Closure, zs)
  }

  fun createMinus(x: Int, y: Int): Dispatch = DispatchNodeGen.create(x - y)
}