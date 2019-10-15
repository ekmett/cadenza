package cadenza.builtin;

import cadenza.NeutralException;
import cadenza.TypesGen;
import cadenza.nodes.Expr;
import cadenza.types.Type;
import cadenza.values.Closure;
import cadenza.values.Neutral;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
@NodeInfo(shortName = "print$")

public abstract class Builtin {
  public final Type resultType;
  public Builtin(final Type resultType) {
    this.resultType = resultType;
  }

  public abstract Object execute(VirtualFrame frame, Expr arg) throws NeutralException;

  public void executeVoid(VirtualFrame frame, Expr arg) throws NeutralException {
    execute(frame, arg);
  }

  public Closure executeClosure(VirtualFrame frame, Expr arg) throws UnexpectedResultException, NeutralException {
    return TypesGen.expectClosure(execute(frame, arg));
  }
  public boolean executeBoolean(VirtualFrame frame, Expr arg) throws UnexpectedResultException, NeutralException {
    return TypesGen.expectBoolean(execute(frame, arg));
  }
  public int executeInteger(VirtualFrame frame, Expr arg) throws UnexpectedResultException, NeutralException {
    return TypesGen.expectInteger(execute(frame, arg));
  }

  public static class Print extends Builtin {
    Print() { super(Type.action); }

    @Override
    public Object execute(VirtualFrame frame, Expr arg) {
      executeVoid(frame,arg);
      return null;
    }

    @Override
    public void executeVoid(VirtualFrame frame, Expr arg) {
      System.out.println(arg.execute(frame));
    }
  }
}
