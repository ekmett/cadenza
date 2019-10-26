package cadenza.nodes

import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.instrumentation.*
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.source.SourceSection

@GenerateWrapper
open class ClosureBody : Node, InstrumentableNode {
  @Child
  protected var content: Code

  constructor(content: Code) {
    this.content = content
  }

  constructor(that: ClosureBody) {
    this.content = that.content
  }

  open fun execute(frame: VirtualFrame): Any? {
    return content.executeAny(frame)
  }

  override fun isInstrumentable(): Boolean {
    return true
  }

  override fun createWrapper(probe: ProbeNode): InstrumentableNode.WrapperNode {
    return ClosureBodyWrapper(this, this, probe)
  }

  override fun hasTag(tag: Class<out Tag>?): Boolean {
    return tag == StandardTags.RootBodyTag::class.java
  }

  override fun getSourceSection(): SourceSection {
    return parent.sourceSection
  }
}
