package cadenza.nodes

import cadenza.Language
import cadenza.types.Types
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.dsl.TypeSystemReference
import com.oracle.truffle.api.frame.*
import com.oracle.truffle.api.instrumentation.*
import com.oracle.truffle.api.nodes.*
import com.oracle.truffle.api.source.SourceSection

internal val noArguments = arrayOf<Any>()

@TypeSystemReference(Types::class)
abstract class CadenzaNode : Node(), InstrumentableNode

@NodeInfo(language = "core", description = "A root of a core tree.")
@TypeSystemReference(Types::class)
class ProgramRootNode constructor(
  language: Language,
  @field:Child private var body: Code,
  fd: FrameDescriptor
) : RootNode(language, fd) {
  override fun isCloningAllowed() = true
  override fun execute(frame: VirtualFrame) = body.executeAny(frame)
}

class InlineCode(
  language: Language,
  @field:Child var body: Code
) : ExecutableNode(language) {
  override fun execute(frame: VirtualFrame) = body.executeAny(frame)
}

@GenerateWrapper
open class ClosureBody constructor(@field:Child protected var content: Code) : Node(), InstrumentableNode {
  constructor(that: ClosureBody) : this(that.content)

  open fun execute(frame: VirtualFrame): Any? = content.executeAny(frame)
  override fun isInstrumentable() = true
  override fun createWrapper(probe: ProbeNode): InstrumentableNode.WrapperNode = ClosureBodyWrapper(this, this, probe)
  override fun hasTag(tag: Class<out Tag>?) = tag == StandardTags.RootBodyTag::class.java
  override fun getSourceSection(): SourceSection? = parent.sourceSection
}

@GenerateWrapper
@TypeSystemReference(Types::class)
open class ClosureRootNode(
  private val language: TruffleLanguage<*>,
  frameDescriptor: FrameDescriptor = FrameDescriptor(),
  val arity: Int,
  @Children val envPreamble: Array<FrameBuilder> = noFrameBuilders,
  @Children val argPreamble: Array<FrameBuilder>,
  @Child var body: ClosureBody
) : RootNode(language, frameDescriptor), InstrumentableNode {
  constructor(other: ClosureRootNode) : this(other.language,FrameDescriptor(),other.arity,other.envPreamble,other.argPreamble,other.body)

  @Suppress("NOTHING_TO_INLINE")
  inline fun isSuperCombinator() = envPreamble.isNotEmpty()

  @ExplodeLoop
  private fun preamble(frame: VirtualFrame): VirtualFrame {
    val local = Truffle.getRuntime().createVirtualFrame(noArguments, frameDescriptor)
    for (builder in argPreamble) builder.build(local, frame)
    if (isSuperCombinator()) { // supercombinator, given environment
      val env = frame.arguments[0] as MaterializedFrame
      for (builder in envPreamble) builder.build(local, env)
    }
    return local
  }

  override fun execute(frame: VirtualFrame) = body.execute(preamble(frame))
  override fun hasTag(tag: Class<out Tag>?) = tag == StandardTags.RootTag::class.java
  override fun isInstrumentable() = super.isInstrumentable()
  override fun createWrapper(probeNode: ProbeNode): InstrumentableNode.WrapperNode = ClosureRootNodeWrapper(this, this, probeNode)
}