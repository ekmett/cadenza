package core.nodes;

import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

@SuppressWarnings("unused")
@NodeInfo(language = "core", description = "core nodes")
@TypeSystemReference(Types.class)
public abstract class CoreNode extends Node {
  public abstract Object execute(VirtualFrame frame);

  public long executeLong(VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectLong(execute(frame));
  }
}
