package core.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

@NodeInfo(shortName = "const")
public class CoreLiteralBoolNode extends CoreExpressionNode {
  private final boolean value;
  public CoreLiteralBoolNode(boolean value) { this.value = value; }

  @Override
  public Object execute(@SuppressWarnings("unused") VirtualFrame frame) { return value; }

  @Override
  public boolean executeBoolean(@SuppressWarnings("unused") VirtualFrame frame) { return value; }
}
