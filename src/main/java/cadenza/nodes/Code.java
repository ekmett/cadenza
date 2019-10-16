package cadenza.nodes;

import cadenza.nbe.*;
import cadenza.types.*;
import cadenza.values.*;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrumentation.*;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import static cadenza.util.Errors.*;

// Used for expressions: variables, applications, abstractions, etc.

@GenerateWrapper
@NodeInfo(language = "core", description = "core nodes")
@TypeSystemReference(Types.class)
public abstract class Code extends Node implements InstrumentableNode {
  // execution

  public abstract Object execute(VirtualFrame frame) throws NeutralException;

  public Object executeAny(VirtualFrame frame) {
    try {
      return execute(frame);
    } catch (NeutralException e) {
      return e.get();
    }
  }

  public Closure executeClosure(VirtualFrame frame) throws UnexpectedResultException, NeutralException {
    return TypesGen.expectClosure(execute(frame));
  }

  public int executeInteger(VirtualFrame frame) throws UnexpectedResultException, NeutralException {
    return TypesGen.expectInteger(execute(frame));
  }

  public boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException, NeutralException {
    return TypesGen.expectBoolean(execute(frame));
  }

  public void executeVoid(VirtualFrame frame) throws NeutralException {
    execute(frame);
  }

  // instrumentation

  public static final int NO_SOURCE = -1;
  public static final int UNAVAILABLE_SOURCE = -2;
  private int sourceCharIndex = NO_SOURCE;
  private int sourceLength = 0;

  public boolean hasSource() {
    return sourceCharIndex != NO_SOURCE;
  }

  // invoked by the parser to set the source
  public final void setSourceSection(int charIndex, int length) {
    assert sourceCharIndex == NO_SOURCE : "source must only be set once";
    if (charIndex < 0) throw new IllegalArgumentException("charIndex < 0");
    if (length < 0) throw new IllegalArgumentException("length < 0");
    sourceCharIndex = charIndex;
    sourceLength = length;
  }

  public final void setUnavailableSourceSection() {
    assert sourceCharIndex == NO_SOURCE : "source must only be set once";
    sourceCharIndex = UNAVAILABLE_SOURCE;
  }

  public final SourceSection getSourceSection() {
    if (sourceCharIndex == NO_SOURCE) return null;
    RootNode rootNode = getRootNode();
    if (rootNode == null) return null;
    SourceSection rootSourceSection = rootNode.getSourceSection();
    if (rootSourceSection == null) return null;
    Source source = rootSourceSection.getSource();
    return (sourceCharIndex == UNAVAILABLE_SOURCE)
      ? source.createUnavailableSection()
      : source.createSection(sourceCharIndex, sourceLength);
  }

  @Override
  public boolean isInstrumentable() { return hasSource(); }

  public InstrumentableNode.WrapperNode createWrapper(ProbeNode probe) {
    return new CodeWrapper(this,probe);
  }

  public boolean hasTag(Class<? extends Tag> tag) {
    return tag == StandardTags.ExpressionTag.class;
  }

  // statics

  @TypeSystemReference(Types.class)
  @NodeInfo(shortName = "App")
  public static final class App extends Code {
    // construct a call node here we can use to invoke the closure appropriately?
    protected App(Code rator, Code[] rands) {
      this.indirectCallNode = Truffle.getRuntime().createIndirectCallNode(); // TODO: custom PIC?
      this.rator = rator;
      this.rands = rands;
    }


    @SuppressWarnings("CanBeFinal") @Child protected IndirectCallNode indirectCallNode;
    @SuppressWarnings("CanBeFinal") @Child protected Code rator;
    @Children protected final Code[] rands;

    @ExplodeLoop
    private Object[] executeRands(VirtualFrame frame) {
      int len = rands.length;
      CompilerAsserts.partialEvaluationConstant(len);
      Object[] values = new Object[len];
      for (int i=0;i<len;++i) values[i] = rands[i].executeAny(frame); // closures can handle VNeutral
      return values;
    }

    public final Object execute(VirtualFrame frame) throws NeutralException {
      Closure fun;
      try {
        fun = rator.executeClosure(frame);
      } catch (UnexpectedResultException e) {
        throw panic("closure expected", e);
      } catch (NeutralException e) {
        throw e.apply(executeRands(frame));
      }
      return indirectCallNode.call(fun.callTarget, executeRands(frame));
    }

    public boolean hasTag(Class<? extends Tag> tag) {
      if (tag == StandardTags.CallTag.class) return true;
      return super.hasTag(tag);
    }
  }

  // once a variable binding has been inferred to refer to the local arguments of the current frame and mapped to an actual arg index
  // this node replaces the original node.
  // used during frame materialization to access numbered arguments. otherwise not available
  @TypeSystemReference(Types.class)
  @NodeInfo(shortName = "Arg")
  public static class Arg extends Code {
    private final int index;
    public Arg(int index) {
      assert 0 <= index : "negative index";
      this.index = index;
    }

    @Override public Object execute(VirtualFrame frame) {
      Object[] arguments = frame.getArguments();
      assert index < arguments.length : "insufficient arguments";
      return arguments[index];
    }

    @Override public boolean isAdoptable() { return false; }
  }

  public static class If extends Code {
    public final Type type;
    @SuppressWarnings("CanBeFinal")
    @Child
    private Code bodyNode, thenNode, elseNode;
    private final ConditionProfile conditionProfile = ConditionProfile.createBinaryProfile();
    public If(Type type, Code bodyNode, Code thenNode, Code elseNode) {
      this.type = type;
      this.bodyNode = bodyNode;
      this.thenNode = thenNode;
      this.elseNode = elseNode;
    }

    private boolean branch(VirtualFrame frame) throws NeutralException {
      try {
        return conditionProfile.profile(bodyNode.executeBoolean(frame));
      } catch (UnexpectedResultException e) {
        throw panic("non-boolean branch",e);
      } catch (NeutralException e) {
        throw new NeutralException(type, Neutral.nif(e.term, thenNode.executeAny(frame), elseNode.executeAny(frame)));
      }
    }

    @Override
    public Object execute(VirtualFrame frame) throws NeutralException {
      return branch(frame)
        ? thenNode.execute(frame)
        : elseNode.execute(frame);
    }

    @Override
    public int executeInteger(VirtualFrame frame) throws UnexpectedResultException, NeutralException {
      return branch(frame)
        ? thenNode.executeInteger(frame)
        : elseNode.executeInteger(frame);
    }
  }

  // lambdas can be constructed from foreign calltargets, you just need to supply an arity
  @TypeSystemReference(Types.class)
  @NodeInfo(shortName = "Lambda")
  public static class Lambda extends Code {
    final FrameDescriptor closureFrameDescriptor; // used to manufacture the temporary copy that we freeze in the closure
    @Children final FrameBuilder[] captureSteps; // steps used to capture the closure's environment
    @SuppressWarnings("CanBeFinal")
    @Child RootCallTarget callTarget;
    final int arity;
    final Type type; // must have at least as many Arr layers as the arity

    private Lambda(final FrameDescriptor closureFrameDescriptor, final FrameBuilder[] captureSteps, final int arity, final RootCallTarget callTarget, Type type) {
      this.closureFrameDescriptor = closureFrameDescriptor;
      this.captureSteps = captureSteps;
      this.arity = arity;
      this.callTarget = callTarget;
      this.type = type;
    }

    // do we need to capture an environment?
    public final boolean isSuperCombinator() { return closureFrameDescriptor != null; }

    public final Closure execute(VirtualFrame frame) {
      return new Closure(captureEnv(frame), arity, type, callTarget);
    }

    @Override
    public final Closure executeClosure(VirtualFrame frame) {
      return new Closure(captureEnv(frame), arity, type, callTarget);
    }

    @ExplodeLoop
    private MaterializedFrame captureEnv(VirtualFrame frame) {
      if (!isSuperCombinator()) return null;
      VirtualFrame env = Truffle.getRuntime().createMaterializedFrame(new Object[]{}, closureFrameDescriptor);
      for (FrameBuilder captureStep : captureSteps) captureStep.build(env, frame);
      return env.materialize();
    }

    // invariant callTarget points to a native function body with known arity
    public static Lambda create(final RootCallTarget callTarget, Type type) {
      RootNode root = callTarget.getRootNode();
      assert root instanceof ClosureRootNode;
      return create(((ClosureRootNode)root).arity, callTarget, type);
    }

    // package a foreign root call target with known arity
    public static Lambda create(final int arity, final RootCallTarget callTarget, Type type) {
      return create(null, FrameBuilder.noFrameBuilders, arity, callTarget, type);
    }

    public static Lambda create(final FrameDescriptor closureFrameDescriptor, final FrameBuilder[] captureSteps, final RootCallTarget callTarget, Type type) {
      RootNode root = callTarget.getRootNode();
      assert root instanceof ClosureRootNode;
      return create(closureFrameDescriptor, captureSteps,((ClosureRootNode)root).arity, callTarget, type);
    }

    // ensures that all the invariants for the constructor are satisfied
    public static Lambda create(final FrameDescriptor closureFrameDescriptor, final FrameBuilder[] captureSteps, final int arity, final RootCallTarget callTarget, Type type) {
      assert arity > 0;
      boolean hasCaptureSteps = captureSteps.length != 0;
      assert hasCaptureSteps == isSuperCombinator(callTarget) : "mismatched calling convention";
      return new Lambda(
          hasCaptureSteps ? null
        : closureFrameDescriptor == null ? new FrameDescriptor()
        : closureFrameDescriptor,
        captureSteps,
        arity,
        callTarget,
        type
      );
    }

    // utility
    public static boolean isSuperCombinator(final RootCallTarget callTarget) {
      RootNode root = callTarget.getRootNode();
      return root instanceof ClosureRootNode && ((ClosureRootNode)root).isSuperCombinator();
    }

    // root to render capture steps opaque
    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
      return tag == StandardTags.RootTag.class || tag == StandardTags.ExpressionTag.class;
    }
  }

  @NodeInfo(shortName = "Read")
  public abstract static class Var extends Code {
    protected Var(FrameSlot slot) { this.slot = slot; }
    protected final FrameSlot slot;

    @Specialization(rewriteOn = FrameSlotTypeException.class)
    protected long readLong(VirtualFrame frame) throws FrameSlotTypeException {
      return frame.getLong(slot);
    }

    @Specialization(rewriteOn = FrameSlotTypeException.class)
    protected boolean readBoolean(VirtualFrame frame) throws FrameSlotTypeException {
      return frame.getBoolean(slot);
    }

    @Specialization(replaces = {"readLong","readBoolean"})
    protected Object read(VirtualFrame frame) {
      return frame.getValue(slot);
    }

    @Override public boolean isAdoptable() { return false; }
  }

  public static class Ann extends Code {
    @Child protected Code body;
    public final Type type;

    public Ann(Code body, Type type) {
      this.body = body;
      this.type = type;
    }

    @Override
    public Object execute(VirtualFrame frame) throws NeutralException {
      return body.execute(frame);
    }

    @Override
    public Object executeAny(VirtualFrame frame) {
      return body.executeAny(frame);
    }

    @Override
    public int executeInteger(VirtualFrame frame) throws UnexpectedResultException, NeutralException {
      return body.executeInteger(frame);
    }

    @Override
    public boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException, NeutralException  {
      return body.executeBoolean(frame);
    }

    @Override
    public Closure executeClosure(VirtualFrame frame) throws UnexpectedResultException, NeutralException {
      return body.executeClosure(frame);
    }
  }

  // a fully saturated call to a builtin
  // invariant: builtins themselves do not return neutral values, other than through evaluating their argument
  public abstract class CallBuiltin extends Code {
    public Type type;
    public final Builtin builtin;
    public @Child
    Code arg;
    public CallBuiltin(Type type, Builtin builtin, Code arg) {
      this.type = type;
      this.builtin = builtin;
      this.arg = arg;
    }

    @Override
    public Object executeAny(VirtualFrame frame) {
      try {
        return builtin.execute(frame, arg);
      } catch (NeutralException n) {
        return new NeutralValue(type, Neutral.ncallbuiltin(builtin, n.term));
      }
    }

    @Override
    public Object execute(VirtualFrame frame) throws NeutralException {
      try {
        return builtin.execute(frame, arg);
      } catch (NeutralException n) {
        throw new NeutralException(type, Neutral.ncallbuiltin(builtin, n.term));
      }
    }

    @Override
    public int executeInteger(VirtualFrame frame) throws UnexpectedResultException, NeutralException {
      try {
        return builtin.executeInteger(frame, arg);
      } catch (NeutralException n) {
        throw new NeutralException(type, Neutral.ncallbuiltin(builtin, n.term));
      }
    }

    @Override
    public void executeVoid(VirtualFrame frame) throws NeutralException {
      try {
        builtin.executeVoid(frame, arg);
      } catch (NeutralException n) {
        throw new NeutralException(type, Neutral.ncallbuiltin(builtin, n.term));
      }
    }

    @Override
    public boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException, NeutralException {
      try {
        return builtin.executeBoolean(frame, arg);
      } catch (NeutralException n) {
        throw new NeutralException(type, Neutral.ncallbuiltin(builtin, n.term));
      }
    }
  }

  public static Ann ann(Code e, Type t) { return new Ann(e,t); }
  public static Arg arg(int i) { return new Code.Arg(i); }
  public static Var var(FrameSlot slot) { return CodeFactory.VarNodeGen.create(slot); }

  public static Lambda lam(RootCallTarget callTarget, Type type) { return Lambda.create(callTarget, type); }
  public static Lambda lam(int arity, RootCallTarget callTarget, Type type) { return Lambda.create(arity, callTarget, type); }
  public static Lambda lam(FrameDescriptor closureFrameDescriptor, FrameBuilder[] captureSteps, RootCallTarget callTarget, Type type) { return Lambda.create(closureFrameDescriptor, captureSteps, callTarget,type); }
  public static Lambda lam(FrameDescriptor closureFrameDescriptor, FrameBuilder[] captureSteps, int arity, RootCallTarget callTarget, Type type) { return Lambda.create(closureFrameDescriptor, captureSteps, arity, callTarget,type); }

  public static App app(Code rator, Code... rands) {
    return new Code.App(rator, rands);
  }
  public static FrameBuilder put(FrameSlot slot, Code value) { return FrameBuilderNodeGen.create(slot,value); }

//  public static Add add(Expr x, Expr y) { return ExprFactory.AddNodeGen.create(x,y); }
  public static Code booleanLiteral(boolean b) {
    return new Code() {
      @Override public Object execute(VirtualFrame frame) { return b; }
      @Override public boolean executeBoolean(VirtualFrame frame) { return b; }
    };
  }
  public static Code intLiteral(int i) {
    return new Code() {
      @Override public Object execute(VirtualFrame frame) { return i; }
      @Override public int executeInteger(VirtualFrame frame) { return i; }
    };
  }

  public static Code bigLiteral(Int i) {
    return new Code() {
      @Override public Object execute(VirtualFrame frame) { return i; }
      @Override public int executeInteger(VirtualFrame frame) throws UnexpectedResultException {
        //noinspection CatchMayIgnoreException
        try {
          if (i.fitsInInt()) return i.asInt();
        } catch (UnsupportedMessageException e) {}
        throw new UnexpectedResultException(i);
      }
    };
  }
}
