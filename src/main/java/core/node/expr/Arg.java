package core.node.expr;

import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import core.CoreTypes;

// once a variable binding has been inferred to refer to the local arguments of the current frame and mapped to an actual arg index
// this node replaces the original node.
// used during frame materialization to access numbered arguments. otherwise not available
@TypeSystemReference(CoreTypes.class)
@NodeInfo(shortName = "Arg")
public class Arg extends Expression {
  private final int index;
  public Arg(int index) {
    assert 0 <= index : "negative index";
    this.index = index;
  }

  @Override public Object execute(VirtualFrame frame) {
    Object[] arguments = frame.getArguments();
    assert index < arguments.length : "insufficient arguments";
    return arguments[index];
  }

  @Override public boolean isAdoptable() { return false; }
}