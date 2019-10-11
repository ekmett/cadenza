package core.frame;

import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import core.Types;

import java.util.function.Consumer;

@TypeSystemReference(Types.class)
@NodeInfo(shortName = "FrameBuilder")
public abstract class FrameBuilder extends Node {
  public abstract void execute(VirtualFrame frame, MaterializedFrame newFrame);
}