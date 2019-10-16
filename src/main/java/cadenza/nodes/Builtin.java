package cadenza.nodes;

import cadenza.nbe.NeutralException;
import cadenza.types.Type;
import cadenza.types.Types;
import cadenza.types.TypesGen;
import cadenza.values.Closure;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

import java.io.Serializable;

@TypeSystemReference(Types.class)
public abstract class Builtin extends Node implements Serializable {
  public final Type resultType;
  public Builtin(final Type resultType) {
    this.resultType = resultType;
  }

  public abstract Object execute(VirtualFrame frame, Code arg) throws NeutralException;

  public void executeVoid(VirtualFrame frame, Code arg) throws NeutralException {
    execute(frame, arg);
  }

  public Closure executeClosure(VirtualFrame frame, Code arg) throws UnexpectedResultException, NeutralException {
    return TypesGen.expectClosure(execute(frame, arg));
  }
  public boolean executeBoolean(VirtualFrame frame, Code arg) throws UnexpectedResultException, NeutralException {
    return TypesGen.expectBoolean(execute(frame, arg));
  }
  public int executeInteger(VirtualFrame frame, Code arg) throws UnexpectedResultException, NeutralException {
    return TypesGen.expectInteger(execute(frame, arg));
  }

  public static Builtin print$ = new Print();
  @NodeInfo(shortName="print$")
  static class Print extends Builtin {
    Print() { super(Type.action); }
    public Object execute(VirtualFrame frame, Code arg) throws NeutralException {
      executeVoid(frame,arg);
      return null;
    }
    @Override
    public void executeVoid(VirtualFrame frame, Code arg) throws NeutralException {
      System.out.println(arg.execute(frame));
    }
  }
}
