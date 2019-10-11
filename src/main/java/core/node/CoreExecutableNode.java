package core.node;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.Node;
import core.Language;
import core.node.expr.CoreExpressionNode;

// returned in response to an InlineParsingRequest. Not otherwise used.
public class CoreExecutableNode extends ExecutableNode {
  @Node.Child public CoreExpressionNode body;

  public CoreExecutableNode(Language language, CoreExpressionNode body) {
    super(language);
    this.body = body;
  }

  public Object execute(VirtualFrame frame) {
    return body.execute(frame);
  }
}
