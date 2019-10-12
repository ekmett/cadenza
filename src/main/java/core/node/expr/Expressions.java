package core.node.expr;

import com.oracle.truffle.api.frame.FrameSlot;
import core.frame.FrameBuilder;
import core.node.CoreRootNode;

// expression factory, sweeping under the rug what is supplied by an annotation processor, by convention, by constructor.
public interface Expressions {
  static FrameBuilder[] noSteps = new FrameBuilder[]{}; // shared empty array

  static ArgExpression arg(int i) { return new ArgExpression(i); }
  static ReadExpression read(FrameSlot slot) { return ReadExpressionNodeGen.create(slot); }

  static Lambda lam(CoreRootNode node) { return new Lambda(noSteps, node); }
  static Lambda lam(FrameBuilder[] steps, CoreRootNode node) { return new Lambda(steps, node); }

  static AppExpression create(Expression target, Expression... argumentNodes) {
    return new AppExpression(target, argumentNodes);
  }
}
