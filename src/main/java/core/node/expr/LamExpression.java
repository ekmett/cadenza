package core.node.expr;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import core.Types;
import core.frame.FrameBuilder;
import core.frame.FrameSlotBuilder;
import core.values.Closure;

@SuppressWarnings("ALL")
@TypeSystemReference(Types.class)
@NodeInfo(shortName = "Lam")
public class LamExpression extends Expression {
  @Children private FrameSlotBuilder[] steps; // used to construct the closure's environment
  public final RootCallTarget callTarget;

  public LamExpression(FrameSlotBuilder[] steps, RootCallTarget callTarget) {
    this.steps = steps;
    this.callTarget = callTarget;
  }

  @ExplodeLoop
  public Closure execute(VirtualFrame frame) {
    MaterializedFrame newFrame = Truffle.getRuntime().createMaterializedFrame(new Object[]{}, callTarget.getRootNode().getFrameDescriptor());
    for (FrameSlotBuilder step : steps) step.execute(frame, newFrame);
    return Closure.create(newFrame, callTarget);
  }
}
