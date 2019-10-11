package core.node.expr;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.NodeInfo;
import core.TailCallException;
import core.values.Closure;

// handle TailCallExceptions
// this should be placed inside any root where we had to set its contents to tail position
@NodeInfo(shortName = "Trampoline")
public class TrampolineExpression extends Expression {
  IndirectCallNode callNode = Truffle.getRuntime().createIndirectCallNode();
  public @Child
  Expression body;
  protected TrampolineExpression(IndirectCallNode callNode, Expression body) {
    this.callNode = callNode;
    this.body = body;
  }

  @Override
  public Object execute(VirtualFrame frame) {
    try {
      return body.execute(frame);
    } catch (TailCallException e) {
      return pump(frame, e.closure, e.arguments);
    }
  }

  private Object pump(VirtualFrame frame, Closure closure, Object[] arguments) {
    for(;;) {
      try {
        return closure.execute(arguments);
      } catch (TailCallException e) {
        closure = e.closure;
        arguments = e.arguments;
      }
    }
  }
}
