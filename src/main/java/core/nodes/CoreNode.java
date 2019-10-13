package core.nodes;

import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

// CoreNode is just an instrumentable node that is also a Node
// CoreNode.Simple is an instrumentable node that uses lazy source elaboration
public abstract class CoreNode extends Node implements InstrumentableNode {
  public abstract static class Simple extends CoreNode {
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
  }
}
