package cadenza.jit

import cadenza.Language
import com.oracle.truffle.api.*
import com.oracle.truffle.api.frame.*
import com.oracle.truffle.api.nodes.*
import com.oracle.truffle.api.profiles.BranchProfile
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget
import java.lang.Exception


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
  @Child var loopNode = LoopNode(TailCallRepeatingNode())

  fun execute(tailCall: TailCallException): Any {
    return loopNode.execute(TailCallState(tailCall))
  }
}


class LoopNode<S : Any>(@field:Child var repeatingNode: RepeatingNode<S>) : Node() {
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
      val x = repeatingNode.body(stateV)
      if (k > 10) { return x }
      if (x is ContinueLoop<*>) { stateV = x.state as S } else { return x }
      k++
    }
  }

  fun execute(state: S): Any {
    var stateV = state

    while (!CompilerDirectives.inInterpreter()) {
      val x = profilingLoop(stateV)
      if (x is ContinueLoop<*>) { stateV = x.state as S } else { return x }
    }

    if (target == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate()
      val x = profilingLoop(stateV)
      if (x is ContinueLoop<*>) { stateV = x.state as S } else { return x }
      val language = lookupLanguageReference(Language::class.java).get()
      val rootNode = LoopRootNode(repeatingNode, FrameDescriptor(), language)
      // probably fine if this is just
      // Truffle.getRuntime().createCallTarget(rootNode)
//      target = (Truffle.getRuntime() as GraalTruffleRuntime).createOSRCallTarget(rootNode)
      target = Truffle.getRuntime().createCallTarget(rootNode)
      // TODO: when not allowed to do this, could instead make a calltarget
      // that runs the loop for k iterations & call it a bunch to get it to compile
      try {
        (target as OptimizedCallTarget).compile(true)
      } catch (e: IllegalAccessError) {}
      callNode = DirectCallNode.create(target)
    }

    return runCompiled(stateV)

  }
}

@CompilerDirectives.ValueType
class ContinueLoop<S : Any>(val state: S)

// TODO: make state be an array & pass it as arguments to our RootNode?
// or use generics to let body have multiple args?
abstract class RepeatingNode<S : Any> : Node() {
  // return ContinueLoop(newState) to continue, anything else to return
  abstract fun body(state: S): Any
  // copy state if possible (if state is immutable), to help escape analysis
  abstract fun realloc(state: S): S
}


@CompilerDirectives.ValueType
class TailCallState {
  val fn: CallTarget
  @CompilerDirectives.CompilationFinal(dimensions = 1) val args: Array<Any?>

  constructor(e: TailCallState) { fn = e.fn; args = e.args }
  constructor(e: TailCallException) { fn = e.fn; args = e.args }
}

class TailCallRepeatingNode : RepeatingNode<TailCallState>() {
  @Child var callNode: DispatchCallTarget = DispatchCallTargetNodeGen.create()

  override fun body(state: TailCallState): Any {
    return try {
      callNode.executeDispatch(state.fn, state.args)
    } catch (e: TailCallException) {
      val x = TailCallState(e)
//      CompilerDirectives.ensureVirtualized(x)
      ContinueLoop(x)
    }
  }

  override fun realloc(e: TailCallState): TailCallState {
    return TailCallState(e)
  }
}

class LoopRootNode<S : Any>(@field:Child var repeatingNode: RepeatingNode<S>, fd: FrameDescriptor, language: Language) : RootNode(language, fd) {
  override fun execute(frame: VirtualFrame): Any? {
    // TODO: exit if !inInterpreter?
    var stateV = repeatingNode.realloc(frame.arguments[0] as S)

    while (true) {
      val x = repeatingNode.body(stateV)
      if (x is ContinueLoop<*>) { stateV = x.state as S } else { return x }
    }
  }

  override fun isCloningAllowed() = false
  override fun getName() = "tail-call trampoline"
}
