package core.node.expr;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameSlot;
import core.node.FrameBuilder;

// combinator library for constructing expressions, sweeping under the rug what is supplied by a builder
public class Expressions {
  private static final FrameBuilder[] noSteps = new FrameBuilder[]{}; // shared empty array

  public static ArgNode arg(int i) { return new ArgNode(i); }
  public static ReadNode read(FrameSlot slot) { return ReadNodeGen.create(slot); }

  public static LamNode lam(RootCallTarget callTarget) { return new LamNode(noSteps, callTarget); }
  public static LamNode lam(FrameBuilder[] steps, RootCallTarget callTarget) { return new LamNode(steps, callTarget); }

  public AppNode create(CallTarget target, CoreExpressionNode... argumentNodes) {
    return new AppNode(target, Truffle.getRuntime().createDirectCallNode(target), argumentNodes);
  }

  public static Trampoline trampoline(CoreExpressionNode body) {
    return new Trampoline(Truffle.getRuntime().createIndirectCallNode(), body);
  }
}
