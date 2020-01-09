package cadenza.jit

import cadenza.Language
import com.oracle.truffle.api.*
import com.oracle.truffle.api.dsl.Cached
import com.oracle.truffle.api.dsl.Fallback
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.frame.*
import com.oracle.truffle.api.nodes.*
import com.oracle.truffle.api.profiles.BranchProfile
import com.oracle.truffle.api.profiles.IntValueProfile
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget
import java.lang.Exception
import kotlin.math.min
import java.lang.reflect.Array as JavaArray


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
  @Child var loopNode = ExceptionLoopNode(TailCallERepeatingNode())

  fun execute(tailCall: TailCallException): Any {
    return loopNode.execute(TailCallState(tailCall))
  }
}


class ExceptionLoopNode<S : Any>(@field:Child var repeatingNode: ExceptionRepeatingNode<S>) : Node() {
  @CompilerDirectives.CompilationFinal var target: CallTarget? = null
  @Child var callNode: DirectCallNode? = null

  fun runCompiled(state: S): Any {
    // don't use callTarget: it leaves calls to pushEncapsulatingNode etc in the resulting code
    // either use callOSR or a DirectCallNode
//    return CallUtils.callTarget(target as CallTarget, arrayOf(tailCall.fn, tailCall.args))
    // TODO: use callOSR when we can?
//    return (target as OptimizedCallTarget).callOSR(tailCall.fn, tailCall.args)
    return CallUtils.callDirect(callNode, arrayOf(state as Any))
  }


  fun profilingLoop(state: S): Any {
    var stateV = state
    var k = 0

    while (true) {
      try {
        return repeatingNode.body(state)
      } catch (e: Exception) {
        if (k > 10) { throw e }
        repeatingNode.shouldContinue(e)?.let {
          stateV = it } ?: run { throw e }
      }
    }
  }

  fun execute(state: S): Any {
    var stateV = state
    while (!CompilerDirectives.inInterpreter()) {
      try {
        return repeatingNode.body(stateV)
      } catch (e: Exception) {
        repeatingNode.shouldContinue(e)?.let {
          stateV = it } ?: run { throw e }
      }
    }

    if (target == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate()
      try {
        return profilingLoop(state)
      } catch (e: Exception) {
        repeatingNode.shouldContinue(e)?.let {
          stateV = it } ?: run { throw e }
      }
      val language = lookupLanguageReference(Language::class.java).get()
      val rootNode = LoopRootNode(repeatingNode, FrameDescriptor(), language)
      // probably fine if this is just
      // Truffle.getRuntime().createCallTarget(rootNode)
//      target = (Truffle.getRuntime() as GraalTruffleRuntime).createOSRCallTarget(rootNode)
      target = Truffle.getRuntime().createCallTarget(rootNode)
      // TODO: when not allowed to do this, could instead make a calltarget
      // that runs the loop for k iterations & call it a bunch to get it to compile
      // could take k as an argument to the loop?
      // this requires an
      try {
        (target as OptimizedCallTarget).compile(true)
      } catch (e: IllegalAccessError) {}
      callNode = DirectCallNode.create(target)
    }

    return runCompiled(stateV)

  }
}

// TODO: make state be an array & pass it as arguments to our RootNode?
// or use generics to let run take multiple args?
// TODO: a return value instead of exceptions for continue?
abstract class ExceptionRepeatingNode<S : Any> : Node() {
  abstract fun body(state: S): Any
  abstract fun shouldContinue(exception: Exception): S?
  // copy constructor for state, to help escape analysis
  abstract fun realloc(state: S): S
}



@CompilerDirectives.ValueType
class TailCallState {
  val fn: CallTarget
  @CompilerDirectives.CompilationFinal(dimensions = 1) val args: Array<Any?>

  constructor(e: TailCallState) { fn = e.fn; args = e.args }
  constructor(e: TailCallException) { fn = e.fn; args = e.args }
}

class TailCallERepeatingNode : ExceptionRepeatingNode<TailCallState>() {
  @Child var callNode: DispatchCallTarget = DispatchCallTargetNodeGen.create()

  override fun body(state: TailCallState): Any {
    return callNode.executeDispatch(state.fn, state.args)
  }

  override fun shouldContinue(e: Exception): TailCallState? {
    if (e is TailCallException) {
      val x = TailCallState(e)
      CompilerDirectives.ensureVirtualized(x)
      return x
    }
    return null
  }

  override fun realloc(e: TailCallState): TailCallState {
    return TailCallState(e)
  }

}

class LoopRootNode<S : Any>(@field:Child var repeatingNode: ExceptionRepeatingNode<S>, fd: FrameDescriptor, language: Language) : RootNode(language, fd) {
  override fun execute(frame: VirtualFrame): Any? {
    // TODO: exit if !inInterpreter?
    var stateV = repeatingNode.realloc(frame.arguments[0] as S)

    while (true) {
      try {
        return repeatingNode.body(stateV)
      } catch (e: Exception) {
        repeatingNode.shouldContinue(e)?.let {
          stateV = it } ?: run { throw e }
      }
    }
  }

  override fun isCloningAllowed() = false
  override fun getName() = "tail-call trampoline"
}
