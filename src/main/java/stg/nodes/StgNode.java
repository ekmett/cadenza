package stg.nodes;

import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

@GenerateWrapper
@ReportPolymorphism
@NodeInfo(language = "stg", description = "Instrumentable STG nodes")
public abstract class StgNode extends Node implements InstrumentableNode {
  public static final int NO_SOURCE = -1;
  public static final int UNAVAILABLE_SOURCE = -2;
  private int sourceCharIndex = NO_SOURCE;
  private int sourceLength = 0;

  public boolean hasSource() {
    return sourceCharIndex != NO_SOURCE;
  }

  // invoked by the parser to set the source
  public final void setSourceSection(int charIndex, int length) {
    assert sourceCharIndex == NO_SOURCE : "source must only be set once";
    if (charIndex < 0) throw new IllegalArgumentException("charIndex < 0");
    if (length < 0) throw new IllegalArgumentException("length < 0");
    sourceCharIndex = charIndex;
    sourceLength = length;
  }

  public final void setUnavailableSourceSection() {
    assert sourceCharIndex == NO_SOURCE : "source must only be set once";
    sourceCharIndex = UNAVAILABLE_SOURCE;
  }

  public final SourceSection getSourceSection() {
    if (sourceCharIndex == NO_SOURCE) return null;
    RootNode rootNode = getRootNode();
    if (rootNode == null) return null;
    SourceSection rootSourceSection = rootNode.getSourceSection();
    if (rootSourceSection == null) return null;
    Source source = rootSourceSection.getSource();
    return (sourceCharIndex == UNAVAILABLE_SOURCE)
      ? source.createUnavailableSection()
      : source.createSection(sourceCharIndex, sourceLength);
  }

  @Override
  public boolean isInstrumentable() { return hasSource(); }

  public WrapperNode createWrapper(ProbeNode probe) {
    return new StgNodeWrapper(this, probe);
  }

  public abstract Object execute(VirtualFrame frame);
}
