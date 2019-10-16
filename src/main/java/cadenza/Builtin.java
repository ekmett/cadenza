package cadenza;

import cadenza.nbe.NeutralException;
import cadenza.nodes.Expr;
import cadenza.types.Type;
import cadenza.types.TypesGen;
import cadenza.values.Closure;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

import java.io.Serializable;

@NodeInfo(shortName = "print$")
public abstract class Builtin implements Serializable {
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

  public static Builtin print$ = new Builtin(Type.action) {
    @Override
    public Object execute(VirtualFrame frame, Expr arg) throws NeutralException {
      executeVoid(frame,arg);
      return null;
    }
    @Override
    public void executeVoid(VirtualFrame frame, Expr arg) throws NeutralException {
      System.out.println(arg.execute(frame));
    }
  };
}
