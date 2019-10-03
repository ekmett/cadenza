
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

//import org.graalvm.compiler.nodeinfo.NodeInfo;

// generatewrapper means this may need to be done in java
@GenerateWrapper
@ReportPolymorphism
@NodeInfo(language = "stg", description = "The abstract base node for all STG statements")
public abstract class StgStatementNode extends Node implements InstrumentableNode {
  public static final int NO_SOURCE = -1;
  public static final int UNAVAILABLE_SOURCE = -2;
  private int sourceCharIndex = NO_SOURCE;
  private int sourceLength = 0;

  public abstract Object execute(VirtualFrame frame);

  public boolean hasStatementTag() {
    return false;
  }

  public boolean hasRootTag() {
    return false;
  }
  
  public boolean hasTag(Class<? extends Tag> tag) {
    if (tag == StandardTags.StatementTag.class) return hasStatementTag(); 
    if (tag == StandardTags.RootTag.class || tag == StandardTags.RootBodyTag.class) return hasRootTag();
    return false;
  }

  public boolean hasSource() {
    return sourceCharIndex != NO_SOURCE;
  }

  // invoked by the parser to set the source
  public final void setSourceSection(int charIndex, int length) {
    assert sourceCharIndex == NO_SOURCE : "source must only be set once";
    if (charIndex < 0)
      throw new IllegalArgumentException("charIndex < 0");
    if (length < 0)
      throw new IllegalArgumentException("length < 0");

    sourceCharIndex = charIndex;
    sourceLength = length;
  }

  public final void setUnavailableSourceSection() {
    assert sourceCharIndex == NO_SOURCE : "source must only be set once";
    sourceCharIndex = UNAVAILABLE_SOURCE;
  }

  public final SourceSection getSourceSection() {
    if (sourceCharIndex == NO_SOURCE)
      return null;
    RootNode rootNode = getRootNode();
    if (rootNode == null)
      return null;
    SourceSection rootSourceSection = rootNode.getSourceSection();
    if (rootSourceSection == null)
      return null;
    Source source = rootSourceSection.getSource();
    if (sourceCharIndex == UNAVAILABLE_SOURCE) {
      return source.createUnavailableSection();
    } else {
      return source.createSection(sourceCharIndex, sourceLength);
    }
  }

  public WrapperNode createWrapper(ProbeNode probe) {
    return new StgStatementNodeWrapper(this, probe);
  }

  @Override
  public boolean isInstrumentable() { return hasSource(); }
}
