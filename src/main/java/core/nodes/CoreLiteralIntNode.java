package core.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

@NodeInfo(shortName = "const")
public class CoreLiteralIntNode extends CoreExpressionNode {
  private final int value;
  public CoreLiteralIntNode(int value) { this.value = value; }

  @Override
  public Object execute(@SuppressWarnings("unused") VirtualFrame frame) { return value; }

  @Override
  public int executeInteger(@SuppressWarnings("unused") VirtualFrame frame) { return value; }
}
