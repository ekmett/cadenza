package core.node.expr;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import core.CoreTypes;
import core.frame.FrameBuilder;
import core.node.FunctionBody;
import core.values.Closure;

// lambdas can be constructed from foreign calltargets, you just need to supply an arity
@TypeSystemReference(CoreTypes.class)
@NodeInfo(shortName = "Lam")
public class Lambda extends Expression {

  final FrameDescriptor closureFrameDescriptor; // used to manufacture the temporary copy that we freeze in the closure
  @Children final FrameBuilder[] captureSteps; // steps used to capture the closure's environment
  @Child RootCallTarget callTarget;
  final int arity;

  private Lambda(final FrameDescriptor closureFrameDescriptor, final FrameBuilder[] captureSteps, final int arity, final RootCallTarget callTarget) {
    this.closureFrameDescriptor = closureFrameDescriptor;
    this.captureSteps = captureSteps;
    this.arity = arity;
    this.callTarget = callTarget;
  }

  // do we need to capture an environment?
  public final boolean isSuperCombinator() { return closureFrameDescriptor != null; }

  public final Closure execute(VirtualFrame frame) {
    return executeClosure(frame);
  }

  @Override
  public final Closure executeClosure(VirtualFrame frame) {
    return new Closure(captureEnv(frame),callTarget);
  }

  @ExplodeLoop
  private MaterializedFrame captureEnv(VirtualFrame frame) {
    if (!isSuperCombinator()) return null;
    VirtualFrame env = Truffle.getRuntime().createMaterializedFrame(new Object[]{}, closureFrameDescriptor);
    int len = captureSteps.length;
    CompilerAsserts.partialEvaluationConstant(len);
    for (int i=0;i<len;++i) captureSteps[i].build(env, frame);
    return env.materialize();
  }

  // smart constructors

  // invariant callTarget points to a native function body with known arity
  public static Lambda create(final RootCallTarget callTarget) {
    RootNode root = callTarget.getRootNode();
    assert root instanceof FunctionBody;
    return create(((FunctionBody)root).arity, callTarget);
  }

  // package a foreign root call target with known arity
  public static Lambda create(final int arity, final RootCallTarget callTarget) {
    return create(null, noSteps, arity, callTarget);
  }

  public static Lambda create(final FrameDescriptor closureFrameDescriptor, final FrameBuilder[] captureSteps, final RootCallTarget callTarget) {
    RootNode root = callTarget.getRootNode();
    assert root instanceof FunctionBody;
    return create(closureFrameDescriptor, captureSteps,((FunctionBody)root).arity, callTarget);
  }

  // ensures that all the invariants for the constructor are satisfied
  public static Lambda create(final FrameDescriptor closureFrameDescriptor, final FrameBuilder[] captureSteps, final int arity, final RootCallTarget callTarget) {
    assert arity > 0;
    boolean hasCaptureSteps = captureSteps.length != 0;
    assert hasCaptureSteps == isSuperCombinator(callTarget) : "mismatched calling convention";
    return new Lambda(
        hasCaptureSteps ? null
      : closureFrameDescriptor != null ? new FrameDescriptor()
      : closureFrameDescriptor,
      captureSteps,
      arity,
      callTarget
    );
  }

  // static helpers
  private static FrameBuilder[] noSteps = new FrameBuilder[]{};

  // utility
  public static boolean isSuperCombinator(final RootCallTarget callTarget) {
    RootNode root = callTarget.getRootNode();
    return root instanceof FunctionBody && ((FunctionBody)root).isSuperCombinator();
  }
}
