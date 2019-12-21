package cadenza.jit

import cadenza.data.Closure
import cadenza.data.consAppend
import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.dsl.Cached
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.frame.MaterializedFrame
import com.oracle.truffle.api.nodes.DirectCallNode
import com.oracle.truffle.api.nodes.IndirectCallNode
import com.oracle.truffle.api.nodes.Node

abstract class Dispatch(@JvmField val argsSize: Int) : Node() {

  abstract fun executeDispatch(closure: Closure, ys: Array<Any?>): Any?

  @Specialization(guards = [
    "fn.callTarget == cachedCallTarget",
    "fn.papArgs.length == papSize",
    "(fn.env == null) == noEnv",
    // TODO: variant for arity < argsSize that keeps a cached Dispatch (per specialization)
    // since specialization => known arity (arity = callTarget - papSize)
    "fn.arity == argsSize"
  ], limit = "3")
  fun callDirect(fn: Closure, ys: Array<Any?>,
                 @Cached("fn.callTarget") cachedCallTarget: RootCallTarget,
                 @Cached("fn.papArgs.length") papSize: Int,
                 @Cached("fn.env == null") noEnv: Boolean,
                 @Cached("create(cachedCallTarget)") callNode: DirectCallNode): Any {
    // TODO: use argsSize & papSize for the arraycopys? should allow more const folding
    val args = if (!noEnv) consAppend(fn.env as MaterializedFrame, fn.papArgs, ys) else cadenza.data.append(fn.papArgs, ys)
    return CallUtils.callDirect(callNode, args)
  }

  @Specialization(replaces = ["callDirect"])
  fun callIndirect(fn: Closure, ys: Array<Any?>,
                   @Cached("create()") callNode: IndirectCallNode): Any? {
    return if (fn.arity >= argsSize) {
      if (fn.arity == argsSize) {
        val args = if (fn.env != null) consAppend(fn.env, fn.papArgs, ys) else cadenza.data.append(fn.papArgs, ys)
        CallUtils.callIndirect(callNode,fn.callTarget,args)
      } else {
        fn.pap(ys) // not enough arguments, pap node
      }
    } else {
      // TODO: do a map arity => cache for second call (if cache for first call(us) is full)
      // & use the IndirectCallNode
      val xs = ys.copyOf(fn.arity)
      val zs = ys.copyOfRange(fn.arity, ys.size)
      (fn.call(xs) as Closure).call(zs)
    }
    //      return callNode.call(closure.callTarget, OzArguments.pack(proc.declarationFrame, arguments))
  }


}