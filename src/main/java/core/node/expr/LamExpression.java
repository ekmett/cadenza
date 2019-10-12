package core.node.expr;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.Node.*;
import core.CoreTypes;
import core.frame.MaterialBuilder;
import core.node.CoreRootNode;
import core.values.Closure;

@SuppressWarnings("ALL")
@TypeSystemReference(CoreTypes.class)
@NodeInfo(shortName = "Lam")
public class LamExpression extends Expression {
  @Children private MaterialBuilder[] steps; // used to construct the closure's environment
  @Child public CoreRootNode body; // we manufacture an entire root node

  public LamExpression(MaterialBuilder[] steps, CoreRootNode body) {
    this.steps = steps;
    this.body = body;
  }

  @ExplodeLoop
  public Closure execute(VirtualFrame frame) {
    MaterializedFrame newFrame = Truffle.getRuntime().createMaterializedFrame(new Object[]{}, body.getFrameDescriptor());
    for (MaterialBuilder step : steps) step.execute(frame, newFrame);
    return Closure.create(newFrame, body);
  }
}
