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

// TODO: this should probably catch TailCallExceptions and rewrite into a form that handles self-tailcalls or full tailcalls
// when that happens.

@TypeSystemReference(CoreTypes.class)
@NodeInfo(shortName = "FunctionBody")
public final class FunctionBody extends RootNode {
  private static final Object[] noArguments = new Object[]{};

  final int arity; // expected number of arguments, for eval-apply
  @Node.Children private final FrameBuilder[] envPreamble; // used to initialize the live environment from the environment
  @Node.Children private final FrameBuilder[] argPreamble; // used to initialize the live environment from the arguments (numbered from 1 if envPreamble != null)
  @Node.Child public Expression body;

  public final boolean hasEnv() { return envPreamble != null; }

  // envPreamble = null means we use arguments 0 .. as a combinator with no environment
  // if we do have an envPreamble then we use argument 0 to pass the materialized frame
  public FunctionBody(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, int arity, FrameBuilder[] envPreamble, FrameBuilder[] argPreamble, Expression body) {
    super(language, frameDescriptor);
    this.arity = arity;
    this.envPreamble = envPreamble;
    this.argPreamble = argPreamble;
    this.body = body;
  }

  @ExplodeLoop
  private void copyEnv(VirtualFrame frame, VirtualFrame local) {
    if (hasEnv()) {
      MaterializedFrame env = (MaterializedFrame) frame.getArguments()[0];
      for (FrameBuilder builder : envPreamble) builder.execute(env, local);
    }
  }

  @ExplodeLoop
  private void copyArgs(VirtualFrame frame, VirtualFrame local) {
    for (FrameBuilder builder : argPreamble) builder.execute(frame, local);
  }

  public final Object execute(VirtualFrame frame) {
    VirtualFrame local = Truffle.getRuntime().createVirtualFrame(noArguments,getFrameDescriptor());
    copyEnv(frame,local);
    copyArgs(frame,local);
    return body.execute(local); // TODO: call a customized execute method indicating this is in tail position
  }

  // TODO:
  // catch a tail call check to see if it has this function body, if so it is a self-tailcall
  // if not we're doing arbitrary tail calls
  // in the self-tailcall case, we only need to re-run the argPreamble and can reuse the current frame
  // in the arbitrary tailcall case we need to set up a classic trampoline
}