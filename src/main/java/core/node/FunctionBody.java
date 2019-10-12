package core.node;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RootNode;
import core.CoreTypes;
import core.frame.FrameBuilder;
import core.node.expr.Expression;

// and associate a callTarget with this?
@TypeSystemReference(CoreTypes.class)
@NodeInfo(shortName = "FunctionBody")
public final class FunctionBody extends RootNode {
  private static final Object[] noArguments = new Object[]{};

  final int arity; // expected number of arguments, for eval-apply
  @Node.Children private final FrameBuilder[] envPreamble; // used to initialize the live environment from the environment
  @Node.Children private final FrameBuilder[] argPreamble; // used to initialize the live environment from the arguments (numbered from 1)
  @Node.Child public Expression body; // we manufacture an entire root node with a funny calling convention that arg[0] = the materialized frame, we're copying from, and arg[1..] are the actual arguments

  final boolean isCombinator() { return envPreamble == null; }

  // envPreamble = null means we use arguments 0 .. as a combinator with no environment
  // if we do have an envPreamble then we use argument 0 to pass the materialized frame
  protected FunctionBody(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, int arity, FrameBuilder[] envPreamble, FrameBuilder[] argPreamble, Expression body) {
    super(language, frameDescriptor);
    this.arity = arity;
    this.envPreamble = envPreamble;
    this.argPreamble = argPreamble;
    this.body = body;
  }

  // execute our calling convention
  @ExplodeLoop
  private VirtualFrame callingConvention(VirtualFrame frame) {
    VirtualFrame local = Truffle.getRuntime().createVirtualFrame(noArguments,getFrameDescriptor());
    if (!isCombinator()) { // we need environment, we're a super combinator
      MaterializedFrame env = (MaterializedFrame) frame.getArguments()[0];
      for (FrameBuilder builder : envPreamble) builder.execute(env, local);
    }
    for (FrameBuilder builder : argPreamble) builder.execute(frame, local);
    return local;
  }

  public Object execute(VirtualFrame frame) {
    // manufacture a new frame
    // now local is ready for business
    return body.execute(callingConvention(frame));
  }
}