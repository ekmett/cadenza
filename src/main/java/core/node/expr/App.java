package core.node.expr;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import core.CoreTypes;
import core.values.Closure;

@TypeSystemReference(CoreTypes.class)
@NodeInfo(shortName = "App")
public final class App extends Expression {

  // construct a call node here we can use to invoke the closure appropriately?
  protected App(Expression rator, Expression[] rands) {
    this.indirectCallNode = Truffle.getRuntime().createIndirectCallNode(); // TODO: custom PIC?
    this.rator = rator;
    this.rands = rands;
  }

  // TODO: specialize when the rator always reduces to a closure with the same body
  @SuppressWarnings("CanBeFinal") @Child protected IndirectCallNode indirectCallNode;
  @SuppressWarnings("CanBeFinal") @Child protected Expression rator;
  @Children protected final Expression[] rands;

  @ExplodeLoop
  private Object[] executeRands(VirtualFrame frame) {
    int len = rands.length;
    CompilerAsserts.partialEvaluationConstant(len);
    Object[] values = new Object[len];
    for (int i=0;i<len;++i) values[i] = rands[i].execute(frame);
    return values;
  }

  public final Object execute(VirtualFrame frame)  {
    Closure fun = null;
    try {
      fun = rator.executeClosure(frame);
    } catch (UnexpectedResultException e) {
      throw new RuntimeException("closure expected", e); // hard fail. when we add neutrals maybe add a slow path here?
    }
    Object[] values = executeRands(frame);
    return indirectCallNode.call(fun.callTarget, values);
  }


    //CompilerAsserts.partialEvaluationConstant(this.isInTailPosition);
    //if (this.isInTailPosition) throw new TailCallException(closure, arguments);

  //@CompilerDirectives.CompilationFinal protected boolean isInTailPosition = false;
  // app nodes care if they are in tail position
  //@Override public final void setInTailPosition() { isInTailPosition = true; }
  //public boolean requiresTrampoline() { return isInTailPosition; } // if we're in tail position we require a trampoline


}
