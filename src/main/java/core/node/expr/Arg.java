package core.node.expr;

import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import core.Types;

// once a variable binding has been inferred to refer to the local arguments of the current frame and mapped to an actual arg index
// this node replaces the original node.
@TypeSystemReference(Types.class)
@NodeInfo(shortName = "Arg")
public class Arg extends CoreExpressionNode {
  private int index;
  public Arg(int index) {
    this.index = index;
  }

  @Override public Object execute(VirtualFrame frame) {
    Object[] arguments = frame.getArguments();
    // assert that arguments.length is a compile time constant?
    assert index < arguments.length : "insufficient arguments";
    return frame.getArguments()[index];
  }
}