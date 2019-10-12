package core.node.expr;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node.*;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import core.CoreTypes;
import core.values.Closure;

@TypeSystemReference(CoreTypes.class)
@NodeInfo(shortName = "App")
public class AppExpression extends Expression {
  protected AppExpression(Expression rator, Expression[] rands) {
    this.rator = rator;
    this.rands = rands;
  }

  @Child protected Expression rator;
  @Children protected Expression[] rands;

  @ExplodeLoop
  public Object execute(VirtualFrame frame)  {
    Closure fun = null;
    try {
      fun = rator.executeClosure(frame);
    } catch (UnexpectedResultException e) {
      throw new RuntimeException("rator not fun", e);
    }

    int len = rands.length;
    CompilerAsserts.partialEvaluationConstant(len);
    Object[] arguments = new Object[len];
    for (int i=0;i<len;++i) arguments[i] = rands[i].execute(frame);
    return dispatch(fun, arguments);
  }

  protected Object dispatch(Closure closure, Object[] arguments)  {
    return closure.execute(arguments);
  }
    //CompilerAsserts.partialEvaluationConstant(this.isInTailPosition);
    //if (this.isInTailPosition) throw new TailCallException(closure, arguments);

  //@CompilerDirectives.CompilationFinal protected boolean isInTailPosition = false;
  // app nodes care if they are in tail position
  //@Override public final void setInTailPosition() { isInTailPosition = true; }
  //public boolean requiresTrampoline() { return isInTailPosition; } // if we're in tail position we require a trampoline


}
