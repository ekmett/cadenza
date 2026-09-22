package cadenza.jit

import cadenza.data.*
import cadenza.Language
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.dsl.Bind
import com.oracle.truffle.api.dsl.NonIdempotent
import com.oracle.truffle.api.dsl.GenerateUncached
import com.oracle.truffle.api.dsl.GenerateInline
import com.oracle.truffle.api.dsl.Cached
import com.oracle.truffle.api.dsl.ReportPolymorphism
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.profiles.InlinedConditionProfile
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.*


// expects fully applied & doesn't trampoline
// just an inline cache of DirectCallNodes & IndirectCallNodes
@ReportPolymorphism
@GenerateInline
@GenerateUncached
abstract class DispatchCallTarget : Node() {
  abstract fun executeDispatch(inliningTarget: Node, callTarget: CallTarget, ys: Array<Any?>): Any

  @Specialization(guards = [
    "callTarget == cachedCallTarget"
  ], limit = "3")
  fun callDirect(callTarget: CallTarget, ys: Array<Any?>?,
                 @Cached("callTarget") cachedCallTarget: CallTarget,
                 @Cached("create(cachedCallTarget)") callNode: DirectCallNode): Any? {
    return CallUtils.callDirect(callNode, ys)
  }

  @Specialization(replaces = ["callDirect"])
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
    if (hasEnv) { args[1] = fn.env }
    // TODO: figure out how to avoid TailCallException if inlining
    return callerNode.call(frame, args, tail_call)
  }

  @Specialization(guards = [
    "fn.arity < argsSize",
    "fn.arity == arity",
    "fn.callTarget == cachedCallTarget"
  ], limit = "3")
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
    if (hasEnv) { args[1] = fn.env }
    val y = callerNode.call(frame, args, false)
    val zs = ys.copyOfRange(arity, argsSize)
    return dispatch.executeDispatch(frame, y as Closure, zs)
  }

  @Specialization(guards = ["fn.arity > argsSize"])
  fun callUnderapplied(fn: Closure, ys: Array<Any?>): Any? {
    return fn.pap(ys)
  }

  // Once the small direct cache saturates, a single indirect caller handles every target.
  @Specialization(guards = ["fn.arity == argsSize"], replaces = ["callDirect"])
  fun callIndirect(frame: VirtualFrame, fn: Closure, ys: Array<Any?>?,
                   @Cached("create()") callerNode: IndirectCallerNode): Any? {
    val hasEnv = fn.env != null
    val args = appendLSkip(if (hasEnv) 2 else 1, fn.papArgs, fn.papArgs.size, ys, argsSize)
    if (hasEnv) { args[1] = fn.env }
    return callerNode.call(frame, fn.callTarget, args, tail_call)
  }

  // Once this site becomes megamorphic, avoid a second bounded cache of arities:
  // the same application site can observe arbitrarily many currying shapes.
  @Specialization(guards = ["fn.arity < argsSize"], replaces = ["callDirectOverapplied"])
  fun callIndirectOverapplied(frame: VirtualFrame, fn: Closure, ys: Array<Any?>,
                              @Bind node: Node,
                              @Cached(inline = true) dispatch: GenericDispatch): Any? =
    dispatch.executeDispatch(frame, node, fn, ys, tail_call)

  fun createMinusTail(x: Int, y: Int): Dispatch = DispatchNodeGen.create(x - y, tail_call)
}

/** Handles every arity without allocating an unbounded chain of cached dispatch nodes. */
@GenerateInline
abstract class GenericDispatch : Node() {
  abstract fun executeDispatch(frame: VirtualFrame, inliningTarget: Node, fn: Closure,
                               arguments: Array<Any?>, tail: Boolean): Any?

  companion object {
    @JvmStatic
    @Specialization
    fun apply(frame: VirtualFrame, node: Node, fn: Closure, arguments: Array<Any?>, tail: Boolean,
              @Cached(value = "create()", neverDefault = true) caller: IndirectCallerNode,
              @Cached underapplied: InlinedConditionProfile,
              @Cached exact: InlinedConditionProfile): Any? {
      var function = fn
      var offset = 0
      while (true) {
        val remaining = arguments.size - offset
        if (underapplied.profile(node, function.arity > remaining)) {
          return function.pap(arguments.copyOfRange(offset, arguments.size))
        }
        val count = function.arity
        val hasEnv = function.env != null
        val skip = if (hasEnv) 2 else 1
        val args = arrayOfNulls<Any>(skip + function.papArgs.size + count)
        if (hasEnv) args[1] = function.env
        System.arraycopy(function.papArgs, 0, args, skip, function.papArgs.size)
        System.arraycopy(arguments, offset, args, skip + function.papArgs.size, count)
        if (exact.profile(node, count == remaining)) {
          return caller.call(frame, function.callTarget, args, tail)
        }
        function = caller.call(frame, function.callTarget, args, false) as Closure
        offset += count
      }
    }
  }
}

/** A bounded host entry cache; both paths enter a root before guest dispatch. */
@GenerateInline(false)
@GenerateUncached
abstract class InteropDispatch : Node() {
  abstract fun execute(fn: Closure, arguments: Array<Any?>): Any?

  companion object {
    @JvmStatic
    @Specialization(guards = ["arguments.length == arity", "language == currentLanguage()"], limit = "3")
    fun cached(fn: Closure, arguments: Array<Any?>,
               @Cached("arguments.length") arity: Int,
               @Cached("currentLanguage()") language: Language,
               @Cached("createTarget(language, arity)") target: RootCallTarget,
               @Bind node: Node,
               @Cached.Exclusive @Cached(inline = true) dispatch: DispatchCallTarget): Any? =
      dispatch.executeDispatch(node, target, arrayOf(fn, arguments))

    @JvmStatic
    @Specialization(replaces = ["cached"])
    fun generic(fn: Closure, arguments: Array<Any?>,
                @Bind node: Node,
                @Cached.Exclusive @Cached(inline = true) dispatch: DispatchCallTarget): Any? =
      dispatch.executeDispatch(node, currentLanguage().genericInteropTarget, arrayOf(fn, arguments))

    @JvmStatic
    @NonIdempotent
    fun currentLanguage(): Language = Language.currentLanguage()

    @JvmStatic
    fun createTarget(language: Language, arity: Int): RootCallTarget =
      InteropApplyRootNode(language, arity).callTarget
  }
}
