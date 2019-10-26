package cadenza.nodes

import cadenza.Language
import cadenza.types.Types
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.dsl.TypeSystemReference
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.MaterializedFrame
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.instrumentation.*
import com.oracle.truffle.api.nodes.*
import com.oracle.truffle.api.source.SourceSection

internal val noArguments = arrayOf<Any>()

@TypeSystemReference(Types::class)
abstract class CadenzaNode : Node(), InstrumentableNode;// root nodes are needed by Truffle.getRuntime().createCallTarget(someRoot), which is the way to manufacture callable

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
open class ClosureBody : Node, InstrumentableNode {
  @Child protected var content: Code
  constructor(content: Code) { this.content = content }
  constructor(that: ClosureBody) { this.content = that.content }
  open fun execute(frame: VirtualFrame): Any? = content.executeAny(frame)
  override fun isInstrumentable() = true
  override fun createWrapper(probe: ProbeNode): InstrumentableNode.WrapperNode = ClosureBodyWrapper(this, this, probe)
  override fun hasTag(tag: Class<out Tag>?) = tag == StandardTags.RootBodyTag::class.java
  override fun getSourceSection() = parent.sourceSection
}

@GenerateWrapper
@TypeSystemReference(Types::class)
open class ClosureRootNode : RootNode, InstrumentableNode {
  @Children val envPreamble: Array<FrameBuilder>
  @Children val argPreamble: Array<FrameBuilder>
  val arity: Int
  @Child var body: ClosureBody
  protected val language: TruffleLanguage<*>

  inline fun isSuperCombinator() = envPreamble.size != 0

  constructor(
    language: TruffleLanguage<*>,
    frameDescriptor: FrameDescriptor = FrameDescriptor(),
    arity: Int, envPreamble: Array<FrameBuilder> = noFrameBuilders,
    argPreamble: Array<FrameBuilder>,
    body: ClosureBody
  ) : super(language, frameDescriptor) {
    this.language = language
    this.arity = arity
    this.envPreamble = envPreamble
    this.argPreamble = argPreamble
    this.body = body
  }

  // need a copy constructor for instrumentation
  constructor(other: ClosureRootNode) : super(other.language, other.frameDescriptor) {
    this.language = other.language
    this.arity = other.arity
    this.envPreamble = other.envPreamble
    this.argPreamble = other.argPreamble
    this.body = other.body
  }

  @ExplodeLoop
  private fun preamble(frame: VirtualFrame): VirtualFrame {
    val local = Truffle.getRuntime().createVirtualFrame(noArguments, frameDescriptor)
    for (builder in argPreamble) builder.build(local, frame)
    if (isSuperCombinator()) { // supercombinator, needs environment
      val env = frame.arguments[0] as MaterializedFrame
      for (builder in envPreamble) builder.build(local, env)
    }
    return local
  }

  override fun execute(frame: VirtualFrame) = body.execute(preamble(frame))
  override fun hasTag(tag: Class<out Tag>?) = tag == StandardTags.RootTag::class.java
  override fun isInstrumentable() = super.isInstrumentable()
  override fun createWrapper(probeNode: ProbeNode): InstrumentableNode.WrapperNode
    = ClosureRootNodeWrapper(this, this, probeNode)
}