package cadenza.jit

import cadenza.Language
import com.oracle.truffle.api.*
import com.oracle.truffle.api.frame.*
import com.oracle.truffle.api.nodes.*
import com.oracle.truffle.api.profiles.BranchProfile
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget


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
  @CompilerDirectives.CompilationFinal var target: CallTarget? = null
  @Child var callNode: DirectCallNode? = null

  fun execute(tailCall: TailCallException): Any {
    // use a new callTarget a la OptimizedVirtualizingOSRLoopNode (createOSRLoop) to avoid allocating a FrameWithoutBoxing
    // the deal is that LoopNode's OSR must use an allocated frame if it's parent isn't compiled
    // which is bad if hot loop in cold function
    // TODO: don't make a CallTarget when in interpreter
    if (target == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate()
      val language = lookupLanguageReference(Language::class.java).get()
      val rootNode = TailCallRootNode(FrameDescriptor(), language)
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
    // don't use callTarget: it leaves calls to pushEncapsulatingNode etc in the resulting code
    // either use callOSR or a DirectCallNode
//    return CallUtils.callTarget(target as CallTarget, arrayOf(tailCall.fn, tailCall.args))
    // TODO: use callOSR when we can?
//    return (target as OptimizedCallTarget).callOSR(tailCall.fn, tailCall.args)
    return CallUtils.callDirect(callNode, arrayOf(tailCall.fn, tailCall.args))
  }
}

class TailCallRootNode(fd: FrameDescriptor, language: Language) : RootNode(language, fd) {
  @Child var dispatchCallTarget: DispatchCallTarget = DispatchCallTargetNodeGen.create()

  override fun execute(frame: VirtualFrame): Any? {
    var fn = frame.arguments[0] as CallTarget
    var args = frame.arguments[1] as Array<Any?>

    while (true) {
      try {
        return dispatchCallTarget.executeDispatch(fn, args)
      } catch (e: TailCallException) {
        fn = e.fn
        args = e.args
      }
    }
  }

  override fun isCloningAllowed() = false
  override fun getName() = "tail-call trampoline"
}
