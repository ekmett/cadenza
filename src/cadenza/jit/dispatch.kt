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

  abstract fun executeDispatch(closure: Closure, ys: Array<Any?>): Any?

  // TODO: variant for arity < argsSize that keeps a cached Dispatch (per specialization) (callDirectOverapplied)
  // since specialization => known arity (arity = callTarget - papSize)
  @Specialization(guards = [
    "fn.callTarget == cachedCallTarget",
    "fn.papArgs.length == papSize",
    "(fn.env == null) == noEnv",
    // TODO: callTarget - papSize = arity, so don't need to check this after specialization
    // or alternatively could use arity to reconstruct papSize
    "fn.arity == argsSize"
  ], limit = "3")
  fun callDirect(fn: Closure, ys: Array<Any?>,
                 @Cached("fn.callTarget") cachedCallTarget: RootCallTarget,
                 @Cached("fn.papArgs.length") papSize: Int,
                 @Cached("fn.env == null") noEnv: Boolean,
                 @Cached("create(cachedCallTarget)") callNode: DirectCallNode): Any {
    val args =
      if (!noEnv) consAppendL(fn.env as MaterializedFrame, fn.papArgs, papSize, ys, argsSize)
      else appendL(fn.papArgs, papSize, ys, argsSize)
    return CallUtils.callDirect(callNode, args)
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

  @Specialization(guards = ["fn.arity < argsSize"])
  fun callIndirectOverapplied(fn: Closure, ys: Array<Any?>): Any? {
    // TODO: do a map arity => cache for second call (if cache for first call(us) is full)
    // & use an IndirectCallNode
    val xs = ys.copyOf(fn.arity)
    val zs = ys.copyOfRange(fn.arity, ys.size)
    return (fn.call(xs) as Closure).call(zs)
  }


}