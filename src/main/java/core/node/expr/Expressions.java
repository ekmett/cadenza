package core.node.expr;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import core.node.FrameBuilder;
import core.node.FrameBuilderNodeGen;

// expression factory, sweeping under the rug what is supplied by an annotation processor, by convention, by constructor.
public interface Expressions {
  static FrameBuilder[] noSteps = new FrameBuilder[]{}; // shared empty array

  static Arg arg(int i) { return new Arg(i); }
  static Var var(FrameSlot slot) { return VarNodeGen.create(slot); }

  // by convention the body here is a combinator, and c
  static Lambda lam(RootCallTarget callTarget) { return Lambda.create(callTarget); }
  static Lambda lam(int arity, RootCallTarget callTarget) { return Lambda.create(arity, callTarget); }
  static Lambda lam(FrameDescriptor closureFrameDescriptor, FrameBuilder[] captureSteps, RootCallTarget callTarget) { return Lambda.create(closureFrameDescriptor, captureSteps, callTarget); }
  static Lambda lam(FrameDescriptor closureFrameDescriptor, FrameBuilder[] captureSteps, int arity, RootCallTarget callTarget) { return Lambda.create(closureFrameDescriptor, captureSteps, arity, callTarget); }

  static App app(Expression rator, Expression... rands) {
    return new App(rator, rands);
  }
  static FrameBuilder put(FrameSlot slot, Expression value) { return FrameBuilderNodeGen.create(slot,value); }
}