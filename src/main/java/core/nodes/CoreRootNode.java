package core.nodes;

import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import core.CoreLanguage;
import core.values.Closure;

// root nodes are needed by Truffle.getRuntime().createCallTarget(someRoot), which is the way to manufacture callable
// things in truffle.
@NodeInfo(language = "core", description = "A root of a core tree.")
@TypeSystemReference(CoreLanguage.Types.class)
public class CoreRootNode extends RootNode implements ExpressionInterface {
  protected CoreRootNode(
    CoreLanguage language,
    Expr body,
    FrameDescriptor fd
  ) {
    super(language, fd);
    this.body = body;
    this.language = language;
  }

  final CoreLanguage language;

  @Child @SuppressWarnings("CanBeFinal") private Expr body;

  // eventually disallow selectively when we have the equivalent of NOINLINE / top level implicitly constructed references?
  @Override public boolean isCloningAllowed() { return true; }

  @Override public Object execute(VirtualFrame frame) {
    return body.execute(frame);
  }

  public static CoreRootNode create(CoreLanguage language, Expr body, FrameDescriptor fd) {
    return new CoreRootNode(language, body, fd);
  }

  public static CoreRootNode create(CoreLanguage language, Expr body) {
    return new CoreRootNode(language, body, new FrameDescriptor());
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