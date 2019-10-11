package core.node.expr;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameSlot;
import core.node.FrameBuilder;

// expression factory, sweeping under the rug what is supplied by an annotation processor, by convention, by constructor.
public interface Expressions {
  static final FrameBuilder[] noSteps = new FrameBuilder[]{}; // shared empty array

  static ArgExpression arg(int i) { return new ArgExpression(i); }
  static ReadExpression read(FrameSlot slot) { return ReadExpressionNodeGen.create(slot); }

  static LamExpression lam(RootCallTarget callTarget) { return new LamExpression(noSteps, callTarget); }
  static LamExpression lam(FrameBuilder[] steps, RootCallTarget callTarget) { return new LamExpression(steps, callTarget); }

  static AppExpression create(CallTarget target, Expression... argumentNodes) {
    return new AppExpression(target, Truffle.getRuntime().createDirectCallNode(target), argumentNodes);
  }

  static TrampolineExpression trampoline(Expression body) {
    return new TrampolineExpression(Truffle.getRuntime().createIndirectCallNode(), body);
  }
}
