package cadenza.nodes

import cadenza.types.Types
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.dsl.TypeSystemReference
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.MaterializedFrame
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.instrumentation.*
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.RootNode

@GenerateWrapper
@TypeSystemReference(Types::class)
open class ClosureRootNode : RootNode, InstrumentableNode {
  @Children
  private val envPreamble: Array<FrameBuilder>
  @Children
  private val argPreamble: Array<FrameBuilder>
  val arity: Int
  @Child
  var body: ClosureBody
  protected val language: TruffleLanguage<*>

  val isSuperCombinator: Boolean
    get() = envPreamble.size != 0

  constructor(language: TruffleLanguage<*>, frameDescriptor: FrameDescriptor, arity: Int, envPreamble: Array<FrameBuilder>, argPreamble: Array<FrameBuilder>, body: ClosureBody) : super(language, frameDescriptor) {
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
    if (isSuperCombinator) { // supercombinator, needs environment
      val env = frame.arguments[0] as MaterializedFrame
      for (builder in envPreamble) builder.build(local, env)
    }
    return local
  }

  // TODO: rewrite on execute throwing a tail call exception?
  // * if it is for the same FunctionBody, we can reuse the frame, just refilling it with their args
  // * if it is for a different FunctionBody, we can use a traditional trampoline
  // * pass the special execute method a tailcall count and have it blow only once it exceeds some threshold?

  override fun execute(frame: VirtualFrame): Any? {
    return body.execute(preamble(frame))
  }

  override fun hasTag(tag: Class<out Tag>?): Boolean {
    return tag == StandardTags.RootTag::class.java
  }

  override fun createWrapper(probeNode: ProbeNode): InstrumentableNode.WrapperNode {
    return ClosureRootNodeWrapper(this, this, probeNode)
  }

  override fun isInstrumentable(): Boolean {
    return super.isInstrumentable()
  }

  companion object {
    private val noArguments = arrayOf<Any>()

    fun create(language: TruffleLanguage<*>, frameDescriptor: FrameDescriptor, arity: Int, envPreamble: Array<FrameBuilder>, argPreamble: Array<FrameBuilder>, body: Code): ClosureRootNode {
      return ClosureRootNode(language, frameDescriptor, arity, envPreamble, argPreamble, ClosureBody(body))
    }

    fun create(language: TruffleLanguage<*>, frameDescriptor: FrameDescriptor, arity: Int, argPreamble: Array<FrameBuilder>, body: Code): ClosureRootNode {
      return ClosureRootNode(language, frameDescriptor, arity, FrameBuilder.noFrameBuilders, argPreamble, ClosureBody(body))
    }

    fun create(language: TruffleLanguage<*>, arity: Int, argPreamble: Array<FrameBuilder>, body: Code): ClosureRootNode {
      return ClosureRootNode(language, FrameDescriptor(), arity, FrameBuilder.noFrameBuilders, argPreamble, ClosureBody(body))
    }
  }


  // checking two closures for alpha equivalence equality involves using nbe to probe to see if they are the same.
  // then converting to debruijn form.

  // we'd also need to convert the hashcode to work similarly.
}
