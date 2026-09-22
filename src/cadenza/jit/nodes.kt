package cadenza.jit

import cadenza.Language
import cadenza.Loc
import cadenza.data.DataTypes
import cadenza.data.drop
import cadenza.frame.DataFrame
import cadenza.frame.CaptureLayout
import cadenza.data.Closure
import cadenza.data.NeutralValue
import cadenza.data.Neutral
import cadenza.semantics.after
import cadenza.section
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.dsl.TypeSystemReference
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.MaterializedFrame
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.instrumentation.*
import com.oracle.truffle.api.nodes.*
import com.oracle.truffle.api.profiles.BranchProfile
import com.oracle.truffle.api.source.Source
import com.oracle.truffle.api.source.SourceSection

// code and statements, and other things with source locations that aren't root or root-like
abstract class LocatedNode(val loc: Loc? = null) : Node(), InstrumentableNode {
  override fun getSourceSection(): SourceSection? = loc?.let { rootNode?.sourceSection?.source?.section(it) }
  override fun isInstrumentable() = loc !== null
}

abstract class CadenzaRootNode(
  language: Language,
  fd: FrameDescriptor
) : RootNode(language, fd) {
  /** True only for roots that initialize FrameLayout.BLOOM_FILTER on every entry. */
  open val hasTailCallFrame: Boolean = false
  open val mask: Long = hashCode().run {
    1L shl and(0x3f) or
      (1L shl (shr(6) and 0x3f)) or
      (1L shl (shr(12) and 0x3f)) or
      (1L shl (shr(18) and 0x3f)) or
      (1L shl (shr(24) and 0x3f))
  }
}

@NodeInfo(language = "core", description = "A root of a core tree.")
@TypeSystemReference(DataTypes::class)
open class ProgramRootNode constructor(
  language: Language,
  @field:Child private var body: Code,
  fd: FrameDescriptor,
  val source: Source
) : CadenzaRootNode(language, fd) {
  @Child var tailCallLoop = TailCallLoop()

  override fun isCloningAllowed() = true
  override fun execute(frame: VirtualFrame): Any? {
    return try {
      body.executeAny(frame)
    } catch (tailCall: TailCallException) {
      tailCallLoop.execute(tailCall)
    }
  }

  override fun getSourceSection(): SourceSection = source.createSection(0, source.length)
  override fun getName() = "program root"
}

class InlineCode(
  language: Language,
  @field:Child var body: Code
) : ExecutableNode(language) {
  override fun execute(frame: VirtualFrame) = body.executeAny(frame)
}

@GenerateWrapper
open class ClosureBody constructor(
  @field:Child protected var content: Code?
) : Node(), InstrumentableNode {
  constructor(@Suppress("UNUSED_PARAMETER") that: ClosureBody) : this(null as Code?)

  open fun execute(frame: VirtualFrame): Any? = content!!.executeAny(frame)
  override fun isInstrumentable() = true
  override fun createWrapper(probe: ProbeNode): InstrumentableNode.WrapperNode = ClosureBodyWrapper(this, this, probe)
  override fun hasTag(tag: Class<out Tag>?) = tag == StandardTags.RootBodyTag::class.java
  override fun getSourceSection(): SourceSection? = rootNode.sourceSection
}

// todo: should this get removed & always inline?
// might still be good to use this, since we could use this e.g. at gc time to do selector forwarding
open class BuiltinRootNode(
  private val language: Language,
  @field:Child var builtin: Builtin
) : CadenzaRootNode(language, FrameLayout().build()) {
  override fun execute(frame: VirtualFrame): Any? {
    val arguments = drop(1, frame.arguments)
    if (arguments.any { it is NeutralValue }) {
      return NeutralValue(builtin.type.after(builtin.arity), Neutral.NCallBuiltin(builtin, arguments))
    }
    return builtin.run(frame, arguments)
  }

  override fun isCloningAllowed() = true
  override fun getName() = "builtin"
}

// TODO: instrumentable body prelude node w/ RootTag?
@TypeSystemReference(DataTypes::class)
open class ClosureRootNode(
  private val language: Language,
  frameDescriptor: FrameDescriptor = FrameLayout().build(),
  val arity: Int,
  // slot = closure.env[ix]
  @CompilerDirectives.CompilationFinal(dimensions = 1) val envPreamble: Array<Pair<Int, Int>> = arrayOf(),
  @CompilerDirectives.CompilationFinal(dimensions = 1) val argPreamble: Array<Pair<Int, Int>>,
  body: ClosureBody,
  val source: Source,
  val loc: Loc? = null,
  private val captureLayout: CaptureLayout? = null
) : CadenzaRootNode(language, frameDescriptor) {

  override val hasTailCallFrame: Boolean = true
  val bloomFilterSlot: Int = FrameLayout.BLOOM_FILTER
  @field:Child var selfTailCallLoopNode = SelfTailCallLoop(body)
  private val tailCallProfile: BranchProfile = BranchProfile.create()

  @Suppress("NOTHING_TO_INLINE")
  inline fun hasEnvironment() = envPreamble.isNotEmpty()

  @ExplodeLoop
  fun buildFrame(arguments: Array<Any?>, local: VirtualFrame) {
    val offset = if (hasEnvironment()) 2 else 1
    for ((slot, x) in argPreamble) FrameAccess.write(local, slot, arguments[x+offset])
    if (hasEnvironment()) { // Closure receives its captured environment.
      val env = arguments[1] as DataFrame
      for ((slot, ix) in envPreamble) FrameAccess.write(local, slot, captureLayout!!.read(env, ix))
    }
  }

  @ExplodeLoop
  private fun preamble(frame: VirtualFrame): VirtualFrame {
    val local = frame
    local.setLong(bloomFilterSlot, (frame.arguments[0] as Long) or mask)
    buildFrame(frame.arguments, local)
    return local
  }

  override fun execute(oldFrame: VirtualFrame): Any? {
    val local = preamble(oldFrame)
    // force loop peeling: this allows constant folding if recursive calls have const arguments
    return try {
      selfTailCallLoopNode.executeOnce(local)
    } catch (e: TailCallException) {
      tailCallProfile.enter()
      if (e.fn.rootNode !== this) { throw e }
      buildFrame(e.args, local)
      selfTailCallLoopNode.execute(local)
    }
  }

  override fun getSourceSection(): SourceSection? = loc?.let { source.section(it) }
  override fun isInstrumentable() = loc !== null
  override fun getName() = "closure"

  override fun isCloningAllowed() = true
}

class Indirection {
  var set: Boolean = false
  var value: Any? = null
}

/** Host calls enter the same rooted dispatcher and trampoline as guest calls. */
class InteropApplyRootNode(language: Language, argsSize: Int) :
  CadenzaRootNode(language, FrameLayout().build()) {
  @Child private var dispatch: Dispatch = DispatchNodeGen.create(argsSize, false)
  override fun execute(frame: VirtualFrame): Any? = dispatch.executeDispatch(
    frame, frame.arguments[0] as Closure, frame.arguments[1] as Array<Any?>)
  override fun getName() = "interop application"
}

class GenericInteropApplyRootNode(language: Language) :
  CadenzaRootNode(language, FrameLayout().build()) {
  @Child private var dispatch: GenericDispatch = GenericDispatchNodeGen.create()
  override fun execute(frame: VirtualFrame): Any? = dispatch.executeDispatch(
    frame, this, frame.arguments[0] as Closure, frame.arguments[1] as Array<Any?>, false)
  override fun getName() = "generic interop application"
}
