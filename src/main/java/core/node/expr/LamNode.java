package core.node.expr;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import core.Types;
import core.node.FrameBuilder;
import core.values.Closure;

@SuppressWarnings("ALL")
@TypeSystemReference(Types.class)
@NodeInfo(shortName = "Lam")
public class LamNode extends CoreExpressionNode {
  @Children private FrameBuilder[] steps; // used to construct the closure's environment
  public final RootCallTarget callTarget;

  public LamNode(FrameBuilder[] steps, RootCallTarget callTarget) {
    this.steps = steps;
    this.callTarget = callTarget;
  }


  public Closure execute(VirtualFrame frame) {
    MaterializedFrame newFrame = Truffle.getRuntime().createMaterializedFrame(new Object[]{}, callTarget.getRootNode().getFrameDescriptor());
    for (FrameBuilder step : steps) step.run(frame, newFrame);
    return new Closure(newFrame, callTarget);
  }
}
