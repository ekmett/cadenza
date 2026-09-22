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
import com.oracle.truffle.api.RootCallTarget
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
  body: Code,
  fd: FrameDescriptor,
  val source: Source
) : CadenzaRootNode(language, fd) {
  @Child private var body = ProgramBody(body)

  override fun isCloningAllowed() = true
  override fun execute(frame: VirtualFrame): Any? = body.execute(frame)

  override fun getSourceSection(): SourceSection = source.createSection(0, source.length)
  override fun getName() = "program root"
}

/** One statement unit for evaluating a complete source expression. */
@GenerateWrapper
open class ProgramBody private constructor(
  @field:Child private var content: Code?,
  private val wrappedSection: SourceSection?
) : Node(), InstrumentableNode {
  @Child private var tailCallLoop: TailCallLoop? = content?.let { TailCallLoop() }

  constructor(content: Code) : this(content, null)
  constructor(that: ProgramBody) : this(null, that.sourceSection)

  open fun execute(frame: VirtualFrame): Any? {
    return try {
      content!!.executeAny(frame)
    } catch (tailCall: TailCallException) {
      tailCallLoop!!.execute(tailCall)
    }
  }

  override fun isInstrumentable() = sourceSection != null
  override fun createWrapper(probe: ProbeNode): InstrumentableNode.WrapperNode = ProgramBodyWrapper(this, this, probe)
  override fun hasTag(tag: Class<out Tag>?) = tag == StandardTags.RootTag::class.java ||
    tag == StandardTags.RootBodyTag::class.java || tag == StandardTags.StatementTag::class.java
  override fun getSourceSection(): SourceSection? = wrappedSection ?: content?.sourceSection ?: rootNode?.sourceSection
}

class InlineCode(
  language: Language,
  @field:Child var body: Code
) : ExecutableNode(language) {
  override fun execute(frame: VirtualFrame) = body.executeAny(frame)
}

@GenerateWrapper
open class ClosureBody private constructor(
  @field:Child protected var content: Code?,
  private val wrappedSection: SourceSection?
) : Node(), InstrumentableNode {
  constructor(content: Code?) : this(content, null)
  constructor(that: ClosureBody) : this(null, that.sourceSection)

  open fun execute(frame: VirtualFrame): Any? = content!!.executeAny(frame)
  override fun isInstrumentable() = true
  override fun createWrapper(probe: ProbeNode): InstrumentableNode.WrapperNode = ClosureBodyWrapper(this, this, probe)
  // A function body is one statement unit on each evaluation, including self-tail iterations.
  override fun hasTag(tag: Class<out Tag>?) = tag == StandardTags.RootBodyTag::class.java ||
    tag == StandardTags.StatementTag::class.java
  override fun getSourceSection(): SourceSection? = wrappedSection ?: content?.sourceSection ?: rootNode?.sourceSection
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

/** Instrument the whole invocation, including argument setup and all self-tail iterations. */
@GenerateWrapper
open class ClosureInvocation(body: ClosureBody?) : Node(), InstrumentableNode {
  @Child private var selfTailCallLoopNode: SelfTailCallLoop? = body?.let { SelfTailCallLoop(it) }
  private val tailCallProfile: BranchProfile = BranchProfile.create()

  constructor(@Suppress("UNUSED_PARAMETER") that: ClosureInvocation) : this(null as ClosureBody?)

  open fun execute(frame: VirtualFrame): Any? {
    val root = rootNode as ClosureRootNode
    frame.setLong(FrameLayout.BLOOM_FILTER, (frame.arguments[0] as Long) or root.mask)
    root.buildFrame(frame.arguments, frame)
    // Keep the peeled first iteration: constant recursive arguments can still fold.
    return try {
      selfTailCallLoopNode!!.executeOnce(frame)
    } catch (e: TailCallException) {
      tailCallProfile.enter()
      if (!root.isSelfCall(e.fn)) throw e
      root.buildFrame(e.args, frame)
      selfTailCallLoopNode!!.execute(frame)
    }
  }

  override fun isInstrumentable() = sourceSection != null
  override fun createWrapper(probe: ProbeNode): InstrumentableNode.WrapperNode = ClosureInvocationWrapper(this, this, probe)
  override fun hasTag(tag: Class<out Tag>?) = tag == StandardTags.RootTag::class.java
  override fun getSourceSection(): SourceSection? = rootNode?.sourceSection
}

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

  // Truffle copies this identity when splitting a root. Recursive closures still point at
  // the original call target, but its clones have the same body and calling convention.
  private val bodyIdentity = Any()
  override val hasTailCallFrame: Boolean = true
  val bloomFilterSlot: Int = FrameLayout.BLOOM_FILTER
  @Child private var invocation = ClosureInvocation(body)

  @Suppress("NOTHING_TO_INLINE")
  inline fun hasEnvironment() = envPreamble.isNotEmpty()

  fun isSelfCall(target: RootCallTarget): Boolean =
    (target.rootNode as? ClosureRootNode)?.bodyIdentity === bodyIdentity

  @ExplodeLoop
  fun buildFrame(arguments: Array<Any?>, local: VirtualFrame) {
    val offset = if (hasEnvironment()) 2 else 1
    for ((slot, x) in argPreamble) FrameAccess.write(local, slot, resolveFixedArgument(arguments[x+offset]))
    if (hasEnvironment()) { // Closure receives its captured environment.
      val env = arguments[1] as DataFrame
      for ((slot, ix) in envPreamble) FrameAccess.write(local, slot, captureLayout!!.read(env, ix))
    }
  }

  override fun execute(frame: VirtualFrame): Any? = invocation.execute(frame)

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
