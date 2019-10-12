package core.node.expr;

import com.oracle.truffle.api.frame.FrameSlot;
import core.frame.MaterialBuilder;
import core.node.CoreRootNode;

// expression factory, sweeping under the rug what is supplied by an annotation processor, by convention, by constructor.
public interface Expressions {
  static MaterialBuilder[] noSteps = new MaterialBuilder[]{}; // shared empty array

  static ArgExpression arg(int i) { return new ArgExpression(i); }
  static ReadExpression read(FrameSlot slot) { return ReadExpressionNodeGen.create(slot); }

  static LamExpression lam(CoreRootNode node) { return new LamExpression(noSteps, node); }
  static LamExpression lam(MaterialBuilder[] steps, CoreRootNode node) { return new LamExpression(steps, node); }

  static AppExpression create(Expression target, Expression... argumentNodes) {
    return new AppExpression(target, argumentNodes);
  }
}
