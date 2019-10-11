package core.node.expr;

import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import core.*;

// Used for expressions: variables, applications, abstractions, etc.

@NodeInfo(language = "core", description = "core nodes")
@TypeSystemReference(Types.class)
public abstract class CoreExpressionNode extends Node {

  public abstract Object execute(VirtualFrame frame) throws TailCallException;

  public long executeLong(VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectLong(execute(frame));
  }
  public boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectBoolean(execute(frame));
  }

  public void setTail() {}
}
