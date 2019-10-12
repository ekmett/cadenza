package core.node.expr;

import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import core.CoreTypes;

// once a variable binding has been inferred to refer to the local arguments of the current frame and mapped to an actual arg index
// this node replaces the original node.
@TypeSystemReference(CoreTypes.class)
@NodeInfo(shortName = "Arg")
public class ArgExpression extends Expression {
  private final int index;
  public ArgExpression(int index) {
    assert 0 <= index;
    this.index = index;
  }

  @Override public Object execute(VirtualFrame frame) {
    Object[] arguments = frame.getArguments();
    assert index < arguments.length : "insufficient arguments";
    return frame.getArguments()[index];
  }

  @Override public boolean isAdoptable() { return false; }
}