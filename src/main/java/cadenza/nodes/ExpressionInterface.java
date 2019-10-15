package cadenza.nodes;

import cadenza.NeutralException;
import cadenza.values.VNeutral;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInterface;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import cadenza.values.Closure;
import cadenza.TypesGen;

public interface ExpressionInterface extends NodeInterface, Cloneable {
  Object execute(VirtualFrame frame) throws NeutralException;
  Object executeAny(VirtualFrame frame); // convenience for writing stuck term handlers
  Closure executeClosure(VirtualFrame frame) throws UnexpectedResultException, NeutralException;
  void executeVoid(VirtualFrame frame) throws NeutralException;
  int executeInteger(VirtualFrame frame) throws UnexpectedResultException, NeutralException;
  boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException, NeutralException;

  interface WithDefaults extends ExpressionInterface {
    default Object executeAny(VirtualFrame frame) {
      try {
        return execute(frame);
      } catch (NeutralException e) {
        return e.getVNeutral();
      }
    }

    Closure executeClosure(VirtualFrame frame) throws UnexpectedResultException, NeutralException {
      return TypesGen.expectClosure(execute(frame));
    }

    default int executeInteger(VirtualFrame frame) throws UnexpectedResultException, NeutralException {
      return TypesGen.expectInteger(execute(frame));
    }

    default boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException, NeutralException {
      return TypesGen.expectBoolean(execute(frame));
    }

    default void executeVoid(VirtualFrame frame) throws NeutralException {
      execute(frame);
    }
  }
}
