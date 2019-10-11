package core.nodes;

import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RootNode;
import core.Language;

@NodeInfo(language = "core", description = "A root of a core tree.")
@TypeSystemReference(Types.class)
public class CoreRootNode extends RootNode {
  public CoreRootNode(
    Language language,
    CoreNode body,
    FrameDescriptor fd
  ) {
    super(language, fd);
    this.body = body;
  }

  @Child @SuppressWarnings("CanBeFinal") private CoreNode body;

  @Override public boolean isCloningAllowed() { return true; }

  @Override public Object execute(VirtualFrame frame) {
    assert(lookupContextReference(Language.class).get() != null);
    return body.execute(frame);
  }
}
