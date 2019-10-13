package core.values;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.*;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import core.Language;
import core.nodes.*;

@CompilerDirectives.ValueType // screw your reference equality
@ExportLibrary(InteropLibrary.class)
public class Closure implements TruffleObject {
  public final RootCallTarget callTarget;
  public final int arity;
  public final MaterializedFrame env; // possibly null;

  // invariant: target should have been constructed from a FunctionBody
  // also assumes that env matches the shape expected by the function body
  public Closure(MaterializedFrame env, int arity, RootCallTarget callTarget) {
    assert callTarget.getRootNode() instanceof Root : "not a function body";
    assert (env != null) == ((Root)callTarget.getRootNode()).isSuperCombinator() : "calling convention mismatch";
    this.arity = arity;
    this.callTarget = callTarget;
    this.env = env;
  }

  // combinator
  public Closure(int arity, RootCallTarget callTarget) {
    this(null, arity, callTarget);
  }
  public final boolean isSuperCombinator() {
    return env != null;
  }

  @ExportMessage
  public final boolean isExecutable() { return true; }

  // allow the use of our closures from other polyglot languages
  @ExportMessage
  @CompilerDirectives.TruffleBoundary
  public final Object execute(Object... arguments) {
    return call(arguments);
  }

  // not a truffle boundary, this code will likely wind up inlined into App, so KISS
  public final Object call(Object... arguments) {
    return isSuperCombinator()
      ? callTarget.call(cons(env,arguments))
      : callTarget.call(arguments);
  }

  @ExplodeLoop
  private static Object[] cons(Object x, Object[] xs) {
    Object[] ys = new Object[xs.length + 1];
    ys[0] = x;
    System.arraycopy(xs, 0, ys, 1, xs.length);
    return ys;
  }

  // this node implements RootTag, because it contains a preamble in which the current frame is only partially set up
  @GenerateWrapper
  @TypeSystemReference(Language.Types.class)
  public static class Root extends RootNode implements ExpressionInterface, InstrumentableNode {
    private static final Object[] noArguments = new Object[]{};
    @Children private final FrameBuilder[] envPreamble;
    @Children private final FrameBuilder[] argPreamble;
    public final int arity;
    @SuppressWarnings("CanBeFinal")
    @Child public Closure.Body body;
    protected final TruffleLanguage<?> language;

    public final boolean isSuperCombinator() { return envPreamble.length != 0; }

    public Root(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, int arity, FrameBuilder[] envPreamble, FrameBuilder[] argPreamble, Closure.Body body) {
      super(language, frameDescriptor);
      this.language = language;
      this.arity = arity;
      this.envPreamble = envPreamble;
      this.argPreamble = argPreamble;
      this.body = body;
    }

    // need a copy constructor for instrumentation
    public Root(Root other) {
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

    public static Root create(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, int arity, FrameBuilder[] envPreamble, FrameBuilder[] argPreamble, Expr body) {
      return new Root(language, frameDescriptor, arity, envPreamble, argPreamble, new Closure.Body(body));
    }

    public static Root create(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, int arity, FrameBuilder[] argPreamble, Expr body) {
      return new Root(language, frameDescriptor, arity, FrameBuilder.noFrameBuilders, argPreamble, new Closure.Body(body));
    }

    public static Root create(TruffleLanguage<?> language, int arity, FrameBuilder[] argPreamble, Expr body) {
      return new Root(language, new FrameDescriptor(), arity, FrameBuilder.noFrameBuilders, argPreamble, new Closure.Body(body));
    }

    public boolean hasTag(Class<? extends Tag> tag) {
      return tag == StandardTags.RootTag.class;
    }

    @Override public WrapperNode createWrapper(ProbeNode probeNode) {
      return new RootWrapper(this, this, probeNode);
    }

    @Override
    public boolean isInstrumentable() { return super.isInstrumentable(); }

  }

  public static class Body extends Expr {
    @SuppressWarnings("CanBeFinal")
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