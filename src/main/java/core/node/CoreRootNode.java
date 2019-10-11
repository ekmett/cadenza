package core.node;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RootNode;
import core.Language;
import core.Types;
import core.node.expr.Expression;

@NodeInfo(language = "core", description = "A root of a core tree.")
@TypeSystemReference(Types.class)
public class CoreRootNode extends RootNode {
  public CoreRootNode(
    Language language,
    Expression body,
    FrameDescriptor fd
  ) {
    super(language, fd);
    this.body = body;
  }

  // for top level combinators with no environment
  public CoreRootNode(
    Language language,
    Expression body
  ) {
    this(language, body, new FrameDescriptor());
  }

  @Child @SuppressWarnings("CanBeFinal") private Expression body;

  @Override public boolean isCloningAllowed() { return true; }

  @Override public Object execute(VirtualFrame frame) {
    assert(lookupContextReference(Language.class).get() != null);
    // this thing should pump the body for tail calls, and check arity.
    return body.execute(frame);
  }

  public static CoreRootNode create(Language language, Expression body) {
    return new CoreRootNode(language, body, new FrameDescriptor());
  }
}