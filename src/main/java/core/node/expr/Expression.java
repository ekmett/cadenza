package core.node.expr;

import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import core.*;
import core.values.Closure;

// Used for expressions: variables, applications, abstractions, etc.

@NodeInfo(language = "core", description = "core nodes")
@TypeSystemReference(CoreTypes.class)
public abstract class Expression extends Node {
  public abstract Object execute(VirtualFrame frame);

  public Closure executeClosure(VirtualFrame frame) throws  UnexpectedResultException {
    return CoreTypesGen.expectClosure(execute(frame));
  }

  public long executeLong(VirtualFrame frame) throws UnexpectedResultException {
    return CoreTypesGen.expectLong(execute(frame));
  }

  public boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException {
    return CoreTypesGen.expectBoolean(execute(frame));
  }

  //public void setInTailPosition() {} // do nothing
  //@Override public boolean requiresTrampoline() { return false; }

}
