package core.node.expr;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import core.TailCallException;
import core.Types;
import core.values.Closure;

@TypeSystemReference(Types.class)
@NodeInfo(shortName = "App")
public class AppExpression extends Expression {
  protected AppExpression(Expression rator, Expression[] rands) {
    this.rator = rator;
    this.rands = rands;
  }

  @Child protected Expression rator;
  @Children protected Expression[] rands;

  @ExplodeLoop
  public Object execute(VirtualFrame frame) throws TailCallException {
    Closure fun = null;
    try {
      fun = rator.executeClosure(frame);
    } catch (UnexpectedResultException e) {
      throw new RuntimeException("not fun",e);
    }

    int len = rands.length;
    CompilerAsserts.partialEvaluationConstant(len);
    Object[] arguments = new Object[len];
    for (int i=0;i<len;++i) arguments[i] = rands[i].execute(frame);
    return dispatch(fun, arguments);
  }

  protected Object dispatch(Closure closure, Object[] arguments) throws TailCallException {
    CompilerAsserts.partialEvaluationConstant(this.isTail);
    if (this.isTail) throw new TailCallException(closure, arguments);
    return closure.execute(arguments);
  }

  @CompilerDirectives.CompilationFinal protected boolean isTail = false;
  // app nodes care if they are in tail position
  @Override public final void setTail() { isTail = true; }


}
