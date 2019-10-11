package core.node.expr;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.NodeInfo;
import core.TailCallException;
import core.Types;

@TypeSystemReference(Types.class)
@NodeInfo(shortName = "App")
public abstract class App extends CoreExpressionNode {
  public App(CallTarget target, CoreExpressionNode[] argumentNodes) {
    this.target = target;
    this.callNode = Truffle.getRuntime().createDirectCallNode(target);
    this.argumentNodes = argumentNodes;
  }

  protected final CallTarget target;
  @Child @SuppressWarnings("CanBeFinal") private DirectCallNode callNode;
  @Children protected final CoreExpressionNode[] argumentNodes;

  public Object execute(VirtualFrame frame) {
    Object[] arguments = new Object[argumentNodes.length];
    for (int i=0;i<argumentNodes.length;++i) arguments[i] = argumentNodes[i].execute(frame);
    return dispatch(arguments);
  }

  protected Object dispatch(Object[] arguments) {
    CompilerAsserts.partialEvaluationConstant(this.isTail);
    if (this.isTail) throw new TailCallException(target, arguments);
    return callNode.call(arguments);
  }

  @CompilerDirectives.CompilationFinal protected boolean isTail = false;
  // app nodes care if they are in tail position
  @Override public final void setTail() { isTail = true; }
}
