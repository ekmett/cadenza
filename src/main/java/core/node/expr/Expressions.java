package core.node.expr;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import core.frame.FrameBuilder;
import core.node.CoreRootNode;
import core.node.FunctionBody;

// expression factory, sweeping under the rug what is supplied by an annotation processor, by convention, by constructor.
public interface Expressions {
  static FrameBuilder[] noSteps = new FrameBuilder[]{}; // shared empty array

  static ArgExpression arg(int i) { return new ArgExpression(i); }
  static ReadExpression read(FrameSlot slot) { return ReadExpressionNodeGen.create(slot); }

  // by convention the body here is a combinator, and c
  static Lambda lam(FunctionBody node) {
    assert !node.hasEnv() : "body uses environment, none supplied";
    return new Lambda(null, noSteps, node); // TODO: make two types of lambdas, closures, functionbodies? one with, one without env?
  }
  static Lambda lam(FrameDescriptor closureDescriptor, FrameBuilder[] steps, FunctionBody node) { return new Lambda(closureDescriptor, steps, node); }

  static AppExpression create(Expression target, Expression... argumentNodes) {
    return new AppExpression(target, argumentNodes);
  }
}