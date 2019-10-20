package cadenza.nodes;

import cadenza.types.Types;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.*;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;

@GenerateWrapper
@TypeSystemReference(Types.class)
public class ClosureRootNode extends RootNode implements InstrumentableNode {
  private static final Object[] noArguments = new Object[]{};
  @Children private final FrameBuilder[] envPreamble;
  @Children private final FrameBuilder[] argPreamble;
  public final int arity;
  @SuppressWarnings("CanBeFinal")
  @Child public ClosureBody body;
  protected final TruffleLanguage<?> language;

  public final boolean isSuperCombinator() { return envPreamble.length != 0; }

  public ClosureRootNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, int arity, FrameBuilder[] envPreamble, FrameBuilder[] argPreamble, ClosureBody body) {
    super(language, frameDescriptor);
    this.language = language;
    this.arity = arity;
    this.envPreamble = envPreamble;
    this.argPreamble = argPreamble;
    this.body = body;
  }

  // need a copy constructor for instrumentation
  public ClosureRootNode(ClosureRootNode other) {
    super(other.language,other.getFrameDescriptor());
    this.language = other.language;
    this.arity = other.arity;
    this.envPreamble = other.envPreamble;
    this.argPreamble = other.argPreamble;
    this.body = other.body;
  }

  @ExplodeLoop
  private VirtualFrame preamble(VirtualFrame frame) {
    var local = Truffle.getRuntime().createVirtualFrame(noArguments,getFrameDescriptor());
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

  public static ClosureRootNode create(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, int arity, FrameBuilder[] envPreamble, FrameBuilder[] argPreamble, Code body) {
    return new ClosureRootNode(language, frameDescriptor, arity, envPreamble, argPreamble, new ClosureBody(body));
  }

  public static ClosureRootNode create(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, int arity, FrameBuilder[] argPreamble, Code body) {
    return new ClosureRootNode(language, frameDescriptor, arity, FrameBuilder.noFrameBuilders, argPreamble, new ClosureBody(body));
  }

  public static ClosureRootNode create(TruffleLanguage<?> language, int arity, FrameBuilder[] argPreamble, Code body) {
    return new ClosureRootNode(language, new FrameDescriptor(), arity, FrameBuilder.noFrameBuilders, argPreamble, new ClosureBody(body));
  }

  public boolean hasTag(Class<? extends Tag> tag) {
    return tag == StandardTags.RootTag.class;
  }

  @Override public WrapperNode createWrapper(ProbeNode probeNode) {
    return new ClosureRootNodeWrapper(this, this, probeNode);
  }

  @Override
  public boolean isInstrumentable() { return super.isInstrumentable(); }


  // checking two closures for alpha equivalence equality involves using nbe to probe to see if they are the same.
  // then converting to debruijn form.

  // we'd also need to convert the hashcode to work similarly.
}
