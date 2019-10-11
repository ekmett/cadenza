package core.nodes;

import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;

@TypeSystemReference(Types.class)
public class Arg extends CoreNode {
  private int index;
  public Arg(int index) {
    this.index = index;
  }

  @Override public Object execute(VirtualFrame frame) {
    Object[] arguments = frame.getArguments();
    assert index < arguments.length : "insufficient arguments";
    return frame.getArguments()[index];
  }
}