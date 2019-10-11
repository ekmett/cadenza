package core.nodes;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;

@TypeSystemReference(Types.class)
public class CoreDispatchNode extends Node {
  public CoreDispatchNode(CallTarget target) {
    this.callNode = Truffle.getRuntime().createDirectCallNode(target);
  }
  @Child @SuppressWarnings("CanBeFinal") private DirectCallNode callNode;

  protected Object executeDispatch(@SuppressWarnings("unused") VirtualFrame frame, Object[] arguments) {
    return this.callNode.call(arguments);
  }

}
