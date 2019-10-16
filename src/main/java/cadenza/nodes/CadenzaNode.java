package cadenza.nodes;

import cadenza.types.Types;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import cadenza.Language;

// CoreNode is just an instrumentable node that is also a Node
// CoreNode.Simple is an instrumentable node that uses lazy source elaboration
@TypeSystemReference(Types.class)
public abstract class CadenzaNode extends Node implements InstrumentableNode {

  // implement source by allowing you to set a source section
  public abstract static class Simple extends CadenzaNode {
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

  // used in response to inlineparsing requests
  // the only real applicable domain for these is to create literal watching
  // and eventually it'd be kinda cool to have them for antiquoters for some kind of template haskell thing
  // as this gives access to the current environment

  // to support this, frameslots should be created such that they carry the Type as the extra field!
  // then we can reconstitute type information for variable watches.
  public static class Watch extends ExecutableNode {
    @SuppressWarnings("CanBeFinal")
    @Child public Expr body;

    protected Watch(Language language, Expr body) {
      super(language);
      this.body = body;
    }

    public Object execute(VirtualFrame frame) {
      return body.executeAny(frame);
    }

    public static Watch create(Language language, Expr body) {
      return new Watch(language, body);
    }
  }

  // root nodes are needed by Truffle.getRuntime().createCallTarget(someRoot), which is the way to manufacture callable
  // things in truffle.
  @NodeInfo(language = "core", description = "A root of a core tree.")
  @TypeSystemReference(Types.class)
  public static class ProgramRoot extends RootNode {
    protected ProgramRoot(
      Language language,
      Expr body,
      FrameDescriptor fd
    ) {
      super(language, fd);
      this.body = body;
      this.language = language;
    }

    public final Language language;

    @Child @SuppressWarnings("CanBeFinal") private Expr body;

    // eventually disallow selectively when we have the equivalent of NOINLINE / top level implicitly constructed references?
    @Override public boolean isCloningAllowed() { return true; }

    // returns neutral terms
    @Override public Object execute(VirtualFrame frame) {
      return body.executeAny(frame);
    }
  }
  public static ProgramRoot root(Language language, Expr body, FrameDescriptor fd) {
    return new ProgramRoot(language, body, fd);
  }
  public static ProgramRoot root(Language language, Expr body) {
    return new ProgramRoot(language, body, new FrameDescriptor());
  }

}
