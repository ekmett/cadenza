package cadenza.nodes;

import cadenza.Language;
import cadenza.types.Types;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RootNode;

// root nodes are needed by Truffle.getRuntime().createCallTarget(someRoot), which is the way to manufacture callable
// things in truffle.
@NodeInfo(language = "core", description = "A root of a core tree.")
@TypeSystemReference(Types.class)
public class ProgramRootNode extends RootNode {
  protected ProgramRootNode(
    Language language,
    Code body,
    FrameDescriptor fd
  ) {
    super(language, fd);
    this.body = body;
    this.language = language;
  }

  public final Language language;

  @Child @SuppressWarnings("CanBeFinal") private Code body;

  // eventually disallow selectively when we have the equivalent of NOINLINE / top level implicitly constructed references?
  @Override public boolean isCloningAllowed() { return true; }

  // returns neutral terms
  @Override public Object execute(VirtualFrame frame) {
    return body.executeAny(frame);
  }
}
