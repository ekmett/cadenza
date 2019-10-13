package core.nodes;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.*;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import core.Types;
import core.values.Closure;

// this node implements RootTag, because it contains a preamble in which the current frame is only partially set up
@GenerateWrapper
@TypeSystemReference(Types.class)
public class FunctionRoot extends RootNode implements ExpressionInterface, InstrumentableNode {
  private static final Object[] noArguments = new Object[]{};
  @Children private final FrameBuilder[] envPreamble;
  @Children private final FrameBuilder[] argPreamble;
  public final int arity;
  @Child public Body body;
  protected final TruffleLanguage<?> language;

  public final boolean isSuperCombinator() { return envPreamble.length != 0; }

  public FunctionRoot(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, int arity, FrameBuilder[] envPreamble, FrameBuilder[] argPreamble, Body body) {
    super(language, frameDescriptor);
    this.language = language;
    this.arity = arity;
    this.envPreamble = envPreamble;
    this.argPreamble = argPreamble;
    this.body = body;
  }

  // need a copy constructor for instrumentation
  public FunctionRoot(FunctionRoot other) {
    super(other.language,other.getFrameDescriptor());
    this.language = other.language;
    this.arity = other.arity;
    this.envPreamble = other.envPreamble;
    this.argPreamble = other.argPreamble;
    this.body = other.body;
  }

  @ExplodeLoop
  private VirtualFrame preamble(VirtualFrame frame) {
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

  public Object execute(VirtualFrame frame) {
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

  public static FunctionRoot create(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, int arity, FrameBuilder[] envPreamble, FrameBuilder[] argPreamble, Expr body) {
    return new FunctionRoot(language, frameDescriptor, arity, envPreamble, argPreamble, new FunctionRoot.Body(body));
  }

  public static FunctionRoot create(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, int arity, FrameBuilder[] argPreamble, Expr body) {
    return new FunctionRoot(language, frameDescriptor, arity, FrameBuilder.noFrameBuilders, argPreamble, new FunctionRoot.Body(body));
  }

  public static FunctionRoot create(TruffleLanguage<?> language, int arity, FrameBuilder[] argPreamble, Expr body) {
    return new FunctionRoot(language, new FrameDescriptor(), arity, FrameBuilder.noFrameBuilders, argPreamble, new FunctionRoot.Body(body));
  }

  public boolean hasTag(Class<? extends Tag> tag) {
    return tag == StandardTags.RootTag.class;
  }

  @Override public WrapperNode createWrapper(ProbeNode probeNode) {
    return new FunctionRootWrapper(this, this, probeNode);
  }

  @Override
  public boolean isInstrumentable() { return super.isInstrumentable(); }

  public static class Body extends Expr {
    @Child protected Expr content;
    public Body(Expr content) {
      this.content = content;
    }
    public Object execute(VirtualFrame frame) { return content.execute(frame); }
    public int executeInteger(VirtualFrame frame) throws UnexpectedResultException { return content.executeInteger(frame); }
    public Closure executeClosure(VirtualFrame frame) throws UnexpectedResultException { return content.executeClosure(frame); }
    public boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException { return content.executeBoolean(frame); }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
      return tag == StandardTags.RootBodyTag.class;
    }
  }

}
