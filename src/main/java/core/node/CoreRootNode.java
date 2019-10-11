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

// root nodes are needed by Truffle.getRuntime().createCallTarget(someRoot), which is the way to manufacture callable
// things in truffle.
@NodeInfo(language = "core", description = "A root of a core tree.")
@TypeSystemReference(Types.class)
public class CoreRootNode extends RootNode {
  protected CoreRootNode(
    Language language,
    Expression body,
    FrameDescriptor fd
  ) {
    super(language, fd);
    this.body = body;
  }

  @Child @SuppressWarnings("CanBeFinal") private Expression body;

  // eventually disallow selectively when we have the equivalent of NOINLINE / top level implicitly constructed references?
  @Override public boolean isCloningAllowed() { return true; }

  @Override public Object execute(VirtualFrame frame) {
    assert(lookupContextReference(Language.class).get() != null);
    // to pump for tail calls insert a TrampolineExpression between this and the body
    return body.execute(frame);
  }

  public static CoreRootNode create(Language language, Expression body, FrameDescriptor fd) {
    return new CoreRootNode(language, body, fd);
  }

  public static CoreRootNode create(Language language, Expression body) {
    return new CoreRootNode(language, body, new FrameDescriptor());
  }
}