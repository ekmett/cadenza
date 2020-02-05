package cadenza.jit

import cadenza.Language
import cadenza.Loc
import cadenza.data.DataTypes
import cadenza.data.drop
import cadenza.section
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.dsl.TypeSystemReference
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.FrameSlot
import com.oracle.truffle.api.frame.MaterializedFrame
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.instrumentation.*
import com.oracle.truffle.api.nodes.*
import com.oracle.truffle.api.profiles.BranchProfile
import com.oracle.truffle.api.source.Source
import com.oracle.truffle.api.source.SourceSection

internal val noArguments = arrayOf<Any>()

// code and statements, and other things with source locations that aren't root or root-like
abstract class LocatedNode(val loc: Loc? = null) : Node(), InstrumentableNode {
  override fun getSourceSection(): SourceSection? = loc?.let { rootNode?.sourceSection?.source?.section(it) }
  override fun isInstrumentable() = loc !== null
}

abstract class CadenzaRootNode(
  language: Language,
  fd: FrameDescriptor
) : RootNode(language, fd) {
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
  val target = Truffle.getRuntime().createCallTarget(this)

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
  @field:Child protected var content: Code
) : Node(), InstrumentableNode {
  constructor(that: ClosureBody) : this(that.content)

  open fun execute(frame: VirtualFrame): Any? = content.executeAny(frame)
  override fun isInstrumentable() = true
  override fun createWrapper(probe: ProbeNode): InstrumentableNode.WrapperNode = ClosureBodyWrapper(this, this, probe)
  override fun hasTag(tag: Class<out Tag>?) = tag == StandardTags.RootBodyTag::class.java
  override fun getSourceSection(): SourceSection? = parent.sourceSection
}

// todo: should this get removed & always inline?
// might still be good to use this, since we could use this e.g. at gc time to do selector forwarding
// todo: this doesn't work if one of the args is a neutral
open class BuiltinRootNode(
  private val language: Language,
  @field:Child var builtin: Builtin
) : CadenzaRootNode(language, FrameDescriptor()) {
  override fun execute(frame: VirtualFrame): Any? {
//    assert(frame.arguments.size == builtin.arity) { "bad builtin application $builtin" }
    return builtin.run(frame, drop(1, frame.arguments))
  }

  override fun isCloningAllowed() = true
  override fun getName() = "builtin"
}

@GenerateWrapper
@TypeSystemReference(DataTypes::class)
open class ClosureRootNode(
  private val language: Language,
  frameDescriptor: FrameDescriptor = FrameDescriptor(),
  val arity: Int,
  @field:Children val envPreamble: Array<FrameBuilder> = noFrameBuilders,
  @CompilerDirectives.CompilationFinal(dimensions = 1) val argPreamble: Array<Pair<FrameSlot, Int>>,
  @field:Child var body: ClosureBody,
  val source: Source,
  val loc: Loc? = null
) : CadenzaRootNode(language, frameDescriptor), InstrumentableNode {

  constructor(
    other: ClosureRootNode
  ) : this(
    other.language,
    other.frameDescriptor,
    other.arity,
    other.envPreamble,
    other.argPreamble,
    other.body,
    other.source,
    other.loc
  )

  val bloomFilterSlot: FrameSlot = frameDescriptor.addFrameSlot("<TCO Bloom Filter>")
  @field:Child var selfTailCallLoopNode = SelfTailCallLoop(body, this)
  private val tailCallProfile: BranchProfile = BranchProfile.create()

  @Suppress("NOTHING_TO_INLINE")
  inline fun isSuperCombinator() = envPreamble.isNotEmpty()

  @ExplodeLoop
  fun buildFrame(arguments: Array<Any?>, local: VirtualFrame) {
    for ((slot, x) in argPreamble) local.setObject(slot, arguments[x+1])
    if (isSuperCombinator()) { // supercombinator, given environment
      val env = arguments[1] as MaterializedFrame
      for (builder in envPreamble) builder.build(local, env)
    }
  }

  @ExplodeLoop
  private fun preamble(frame: VirtualFrame): VirtualFrame {
    val local = Truffle.getRuntime().createVirtualFrame(noArguments, frameDescriptor)
    local.setLong(bloomFilterSlot, (frame.arguments[0] as Long) or mask)
    buildFrame(frame.arguments, local)
    return local
  }

  override fun execute(oldFrame: VirtualFrame): Any? {
    val local = preamble(oldFrame)
    // force loop peeling: this allows constant folding if recursive calls have const arguments
    return try {
      body.execute(local)
    } catch (e: TailCallException) {
      tailCallProfile.enter()
      if (e.fn.rootNode !== this) { throw e }
      buildFrame(e.args, local)
      selfTailCallLoopNode.execute(local)
    }
  }
  override fun hasTag(tag: Class<out Tag>?) = tag == StandardTags.RootTag::class.java
  override fun createWrapper(probeNode: ProbeNode): InstrumentableNode.WrapperNode = ClosureRootNodeWrapper(this, this, probeNode)
  override fun getSourceSection(): SourceSection? = loc?.let { source.section(it) }
  override fun isInstrumentable() = loc !== null
  override fun getName() = "closure"

  override fun isCloningAllowed() = true
}

@CompilerDirectives.ValueType
class Indirection {
  var set: Boolean = false
  var value: Any? = null
}

// used for let rec
open class ReadIndirectionRootNode(
  val language: Language
): CadenzaRootNode(language, FrameDescriptor()) {
//  override val mask: Long = 0L

  override fun execute(frame: VirtualFrame): Any? {
    val indir = frame.arguments[1] as Indirection
    if (CompilerDirectives.isPartialEvaluationConstant(indir) && CompilerDirectives.inCompiledCode()) {

    }

    if (!indir.set) {
      CompilerDirectives.transferToInterpreter()
      throw Exception("let rec loop (demanded variable while evaluating it)")
    }
    return indir.value
  }
}


