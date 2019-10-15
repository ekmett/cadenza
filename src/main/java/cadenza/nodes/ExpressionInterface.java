package cadenza.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInterface;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import cadenza.values.Closure;
import cadenza.TypesGen;

public interface ExpressionInterface extends NodeInterface, Cloneable {
  Object execute(VirtualFrame frame);

  // these _should_ just have defaults, but see oracle/graal#1745
  void executeVoid(VirtualFrame frame);
  Closure executeClosure(VirtualFrame frame) throws UnexpectedResultException;
  int executeInteger(VirtualFrame frame) throws UnexpectedResultException;
  boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException;

  interface WithDefaults extends ExpressionInterface {
    default Closure executeClosure(VirtualFrame frame) throws UnexpectedResultException {
      return TypesGen.expectClosure(execute(frame));
    }

    default int executeInteger(VirtualFrame frame) throws UnexpectedResultException {
      return TypesGen.expectInteger(execute(frame));
    }

    default boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException {
      return TypesGen.expectBoolean(execute(frame));
    }

    default void executeVoid(VirtualFrame frame) {
      execute(frame);
    }
  }
}
