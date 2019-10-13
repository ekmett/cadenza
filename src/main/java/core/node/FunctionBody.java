package core.node;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import core.CoreTypes;
import core.node.expr.Expression;
import core.node.expr.ExpressionInterface;
import core.values.Closure;

@TypeSystemReference(CoreTypes.class)
public final class FunctionBody extends RootNode implements ExpressionInterface {
  private static final Object[] noArguments = new Object[]{};
  @Children private final FrameBuilder[] envPreamble;
  @Children private final FrameBuilder[] argPreamble;
  public final int arity;
  @Child public Expression body;

  public final boolean isSuperCombinator() { return envPreamble.length != 0; }

  public FunctionBody(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, int arity, FrameBuilder[] envPreamble, FrameBuilder[] argPreamble, Expression body) {
    super(language, frameDescriptor);
    this.arity = arity;
    this.envPreamble = envPreamble;
    this.argPreamble = argPreamble;
    this.body = body;
  }

  @ExplodeLoop
  private final VirtualFrame preamble(VirtualFrame frame) {
    VirtualFrame local = Truffle.getRuntime().createVirtualFrame(noArguments,getFrameDescriptor());
    for (FrameBuilder builder : argPreamble) builder.build(local,frame);
    if (isSuperCombinator()) { // supercombinator, needs environment
      MaterializedFrame env = (MaterializedFrame) frame.getArguments()[0];
      for (FrameBuilder builder : envPreamble) builder.build(local,env);
    }
    return local;
  }

  // TODO: rewrite on execute throwing a tail call exception?
  // * if it is for the same FunctionBody, we can reuse the frame, just refilling it with their args
  // * if it is for a different FunctionBody, we can use a traditional trampoline
  // * pass the special execute method a tailcall count and have it blow only once it exceeds some threshold?

  public final Object execute(VirtualFrame frame) {
    return body.execute(preamble(frame));
  }

  @Override
  public final boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException {
    return body.executeBoolean(preamble(frame));
  }

  @Override
  public final int executeInteger(VirtualFrame frame) throws UnexpectedResultException {
    return body.executeInteger(preamble(frame));
  }

  @Override
  public final Closure executeClosure(VirtualFrame frame) throws UnexpectedResultException {
    return body.executeClosure(preamble(frame));
  }

  public static FunctionBody create(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, int arity, FrameBuilder[] envPreamble, FrameBuilder[] argPreamble, Expression body) {
    return new FunctionBody(language, frameDescriptor, arity, envPreamble, argPreamble, body);
  }

  public static FunctionBody create(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, int arity, FrameBuilder[] argPreamble, Expression body) {
    return new FunctionBody(language, frameDescriptor, arity, FrameBuilder.noFrameBuilders, argPreamble, body);
  }

  public static FunctionBody create(TruffleLanguage<?> language, int arity, FrameBuilder[] argPreamble, Expression body) {
    return new FunctionBody(language, new FrameDescriptor(), arity, FrameBuilder.noFrameBuilders, argPreamble, body);
  }

}
