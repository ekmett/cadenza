package core.nodes;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;

public class CoreDispatchNode extends Node {
  @Child private IndirectCallNode callNode = Truffle.getRuntime().createIndirectCallNode();

  protected Object executeDispatch(VirtualFrame frame, CallTarget target, Object argument) {
    return this.callNode.call(target, argument);
  }
}
