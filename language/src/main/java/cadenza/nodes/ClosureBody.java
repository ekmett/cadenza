package cadenza.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.*;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

@GenerateWrapper
public class ClosureBody extends Node implements InstrumentableNode {
  @SuppressWarnings("CanBeFinal")
  @Child protected Code content;
  public ClosureBody(Code content) {
    this.content = content;
  }
  public ClosureBody() {}

  public Object execute(VirtualFrame frame) { return content.executeAny(frame); }

  @Override
  public boolean isInstrumentable() {
    return true;
  }

  @Override
  public WrapperNode createWrapper(ProbeNode probe) {
    return new ClosureBodyWrapper(this, probe);
  }

  @Override
  public boolean hasTag(Class<? extends Tag> tag) {
    return tag == StandardTags.RootBodyTag.class;
  }

  @Override
  public SourceSection getSourceSection() {
    return getParent().getSourceSection();
  }
}
