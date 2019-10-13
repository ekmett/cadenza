package core.nodes;

import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import core.Language;
import core.values.Closure;

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

  // returned in response to an InlineParsingRequest. Not otherwise used.
  public static class Executable extends ExecutableNode {
    @SuppressWarnings("CanBeFinal")
    @Child public Expr body;

    protected Executable(Language language, Expr body) {
      super(language);
      this.body = body;
    }

    public Object execute(VirtualFrame frame) {
      return body.execute(frame);
    }

    public static Executable create(Language language, Expr body) {
      return new Executable(language, body);
    }
  }

  // root nodes are needed by Truffle.getRuntime().createCallTarget(someRoot), which is the way to manufacture callable
  // things in truffle.
  @NodeInfo(language = "core", description = "A root of a core tree.")
  @TypeSystemReference(Language.Types.class)
  public static class Root extends RootNode implements ExpressionInterface {
    protected Root(
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

    @Override public Object execute(VirtualFrame frame) {
      return body.execute(frame);
    }

    public static Root create(Language language, Expr body, FrameDescriptor fd) {
      return new Root(language, body, fd);
    }

    public static Root create(Language language, Expr body) {
      return new Root(language, body, new FrameDescriptor());
    }

    // default ExpressionInterface non
    public Closure executeClosure(VirtualFrame frame) throws UnexpectedResultException {
      return body.executeClosure(frame);
    }

    public int executeInteger(VirtualFrame frame) throws UnexpectedResultException {
      return body.executeInteger(frame);
    }

    public boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException {
      return body.executeBoolean(frame);
    }

  }
}
