package core.node.expr;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import core.CoreTypes;
import core.frame.FrameBuilder;
import core.node.CoreRootNode;
import core.node.FunctionBody;
import core.values.Closure;

@SuppressWarnings("ALL")
@TypeSystemReference(CoreTypes.class)
@NodeInfo(shortName = "Lam")
public class Lambda extends Expression {
  FrameDescriptor closureFrameDescriptor; // used to manufacture the temporary copy that we freeze in the closure
  @Children private FrameBuilder[] captureSteps; // steps used to capture the closure's environment
  @Child FunctionBody body; // this could well be set up as a RootNode for our language

  public Lambda(FrameDescriptor closureFrameDescriptor, FrameBuilder[] steps, FunctionBody body) {
    this.captureSteps = steps;
    this.closureFrameDescriptor = closureFrameDescriptor;
    this.body = body;
  }

//  public static Closure create(MaterializedFrame env, FrameDescriptor fd, int arity, FrameBuilder[] builders, Expression body) {

  @ExplodeLoop
  public Closure execute(VirtualFrame frame) {
    MaterializedFrame newFrame = Truffle.getRuntime().createMaterializedFrame(new Object[]{}, closureFrameDescriptor);
    for (FrameBuilder step : captureSteps) step.execute(frame, newFrame);
    return new Closure(newFrame, body);
  }
}
