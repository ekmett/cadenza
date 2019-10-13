package core.nodes;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrumentation.*;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.profiles.ConditionProfile;
import core.*;
import core.values.Int;
import core.values.Closure;

// Used for expressions: variables, applications, abstractions, etc.

@GenerateWrapper
@NodeInfo(language = "core", description = "core nodes")
@TypeSystemReference(Language.Types.class)
public abstract class Expr extends CoreNode.Simple implements ExpressionInterface {
  public Closure executeClosure(VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectClosure(execute(frame));
  }

  public int executeInteger(VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectInteger(execute(frame));
  }

  public boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectBoolean(execute(frame));
  }

  public InstrumentableNode.WrapperNode createWrapper(ProbeNode probe) {
    return new ExprWrapper(this,probe);
  }

  public boolean hasTag(Class<? extends Tag> tag) {
    return (tag == StandardTags.ExpressionTag.class) || super.hasTag(tag);
  }

  @NodeChild
  @NodeChild
  @NodeInfo(shortName="+")
  public abstract static class Add extends Expr {
    @Specialization
    public int add(int x, int y) {
      return x + y;
    }
    @Specialization
    public Int add(Int x, Int y) {
      return new Int(x.value.add(y.value));
    }
  }

  @TypeSystemReference(Language.Types.class)
  @NodeInfo(shortName = "App")
  public static final class App extends Expr {

    // construct a call node here we can use to invoke the closure appropriately?
    protected App(Expr rator, Expr[] rands) {
      this.indirectCallNode = Truffle.getRuntime().createIndirectCallNode(); // TODO: custom PIC?
      this.rator = rator;
      this.rands = rands;
    }

    // TODO: specialize when the rator always reduces to a closure with the same body
    @SuppressWarnings("CanBeFinal") @Child protected IndirectCallNode indirectCallNode;
    @SuppressWarnings("CanBeFinal") @Child protected Expr rator;
    @Children protected final Expr[] rands;

    @ExplodeLoop
    private Object[] executeRands(VirtualFrame frame) {
      int len = rands.length;
      CompilerAsserts.partialEvaluationConstant(len);
      Object[] values = new Object[len];
      for (int i=0;i<len;++i) values[i] = rands[i].execute(frame);
      return values;
    }

    public final Object execute(VirtualFrame frame)  {
      Closure fun;
      try {
        fun = rator.executeClosure(frame);
      } catch (UnexpectedResultException e) {
        throw new RuntimeException("closure expected", e); // hard fail. when we add neutrals maybe add a slow path here?
      }

      Object[] values = executeRands(frame);
      return indirectCallNode.call(fun.callTarget, values);
    }

    public boolean hasTag(Class<? extends Tag> tag) {
      if (tag == StandardTags.CallTag.class) return true;
      return super.hasTag(tag);
    }


    //CompilerAsserts.partialEvaluationConstant(this.isInTailPosition);
      //if (this.isInTailPosition) throw new TailCallException(closure, arguments);

    //@CompilerDirectives.CompilationFinal protected boolean isInTailPosition = false;
    // app nodes care if they are in tail position
    //@Override public final void setInTailPosition() { isInTailPosition = true; }
    //public boolean requiresTrampoline() { return isInTailPosition; } // if we're in tail position we require a trampoline


  }

  // once a variable binding has been inferred to refer to the local arguments of the current frame and mapped to an actual arg index
  // this node replaces the original node.
  // used during frame materialization to access numbered arguments. otherwise not available
  @TypeSystemReference(Language.Types.class)
  @NodeInfo(shortName = "Arg")
  public static class Arg extends Expr {
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

  public static class If extends Expr {
    @SuppressWarnings("CanBeFinal")
    @Child
    private Expr bodyNode, thenNode, elseNode;
    private final ConditionProfile conditionProfile = ConditionProfile.createBinaryProfile();
    public If(Expr bodyNode, Expr thenNode, Expr elseNode) {
      this.bodyNode = bodyNode;
      this.thenNode = thenNode;
      this.elseNode = elseNode;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      return conditionProfile.profile(branch(frame))
        ? this.thenNode.execute(frame)
        : this.elseNode.execute(frame);
    }

    @Override
    public int executeInteger(VirtualFrame frame) throws UnexpectedResultException {
      return conditionProfile.profile(branch(frame))
        ? this.thenNode.executeInteger(frame)
        : this.elseNode.executeInteger(frame);
    }


    @Override
    public boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException {
      return conditionProfile.profile(branch(frame))
        ? this.thenNode.executeBoolean(frame)
        : this.elseNode.executeBoolean(frame);
    }

    @Override
    public Closure executeClosure(VirtualFrame frame) throws UnexpectedResultException {
      return conditionProfile.profile(branch(frame))
        ? this.thenNode.executeClosure(frame)
        : this.elseNode.executeClosure(frame);
    }


    private boolean branch(VirtualFrame frame) {
      try {
        return bodyNode.executeBoolean(frame);
      } catch (UnexpectedResultException e) {
        throw new RuntimeException("condition not boolean",e);
      }
    }
  }

  // lambdas can be constructed from foreign calltargets, you just need to supply an arity
  @TypeSystemReference(Language.Types.class)
  @NodeInfo(shortName = "Lambda")
  public static class Lambda extends Expr {

    final FrameDescriptor closureFrameDescriptor; // used to manufacture the temporary copy that we freeze in the closure
    @Children final FrameBuilder[] captureSteps; // steps used to capture the closure's environment
    @SuppressWarnings("CanBeFinal")
    @Child RootCallTarget callTarget;
    final int arity;

    private Lambda(final FrameDescriptor closureFrameDescriptor, final FrameBuilder[] captureSteps, final int arity, final RootCallTarget callTarget) {
      this.closureFrameDescriptor = closureFrameDescriptor;
      this.captureSteps = captureSteps;
      this.arity = arity;
      this.callTarget = callTarget;
    }

    // do we need to capture an environment?
    public final boolean isSuperCombinator() { return closureFrameDescriptor != null; }

    public final Closure execute(VirtualFrame frame) {
      return executeClosure(frame);
    }

    @Override
    public final Closure executeClosure(VirtualFrame frame) {
      return new Closure(captureEnv(frame),arity,callTarget);
    }

    @ExplodeLoop
    private MaterializedFrame captureEnv(VirtualFrame frame) {
      if (!isSuperCombinator()) return null;
      VirtualFrame env = Truffle.getRuntime().createMaterializedFrame(new Object[]{}, closureFrameDescriptor);
      for (FrameBuilder captureStep : captureSteps) captureStep.build(env, frame);
      return env.materialize();
    }

    // smart constructors

    // invariant callTarget points to a native function body with known arity
    public static Lambda create(final RootCallTarget callTarget) {
      RootNode root = callTarget.getRootNode();
      assert root instanceof Closure.Root;
      return create(((Closure.Root)root).arity, callTarget);
    }

    // package a foreign root call target with known arity
    public static Lambda create(final int arity, final RootCallTarget callTarget) {
      return create(null, FrameBuilder.noFrameBuilders, arity, callTarget);
    }

    public static Lambda create(final FrameDescriptor closureFrameDescriptor, final FrameBuilder[] captureSteps, final RootCallTarget callTarget) {
      RootNode root = callTarget.getRootNode();
      assert root instanceof Closure.Root;
      return create(closureFrameDescriptor, captureSteps,((Closure.Root)root).arity, callTarget);
    }

    // ensures that all the invariants for the constructor are satisfied
    public static Lambda create(final FrameDescriptor closureFrameDescriptor, final FrameBuilder[] captureSteps, final int arity, final RootCallTarget callTarget) {
      assert arity > 0;
      boolean hasCaptureSteps = captureSteps.length != 0;
      assert hasCaptureSteps == isSuperCombinator(callTarget) : "mismatched calling convention";
      return new Lambda(
          hasCaptureSteps ? null
        : closureFrameDescriptor == null ? new FrameDescriptor()
        : closureFrameDescriptor,
        captureSteps,
        arity,
        callTarget
      );
    }

    // utility
    public static boolean isSuperCombinator(final RootCallTarget callTarget) {
      RootNode root = callTarget.getRootNode();
      return root instanceof Closure.Root && ((Closure.Root)root).isSuperCombinator();
    }

    // root to render capture steps opaque
    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
      return tag == StandardTags.RootTag.class || tag == StandardTags.ExpressionTag.class;
    }
  }

  @NodeInfo(shortName = "Read")
  public abstract static class Var extends Expr {
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

  public static Arg arg(int i) { return new Expr.Arg(i); }
  public static Var var(FrameSlot slot) { return ExprFactory.VarNodeGen.create(slot); }

  public static Lambda lam(RootCallTarget callTarget) { return Lambda.create(callTarget); }
  public static Lambda lam(int arity, RootCallTarget callTarget) { return Lambda.create(arity, callTarget); }
  public static Lambda lam(FrameDescriptor closureFrameDescriptor, FrameBuilder[] captureSteps, RootCallTarget callTarget) { return Lambda.create(closureFrameDescriptor, captureSteps, callTarget); }
  public static Lambda lam(FrameDescriptor closureFrameDescriptor, FrameBuilder[] captureSteps, int arity, RootCallTarget callTarget) { return Lambda.create(closureFrameDescriptor, captureSteps, arity, callTarget); }

  public static App app(Expr rator, Expr... rands) {
    return new Expr.App(rator, rands);
  }
  public static FrameBuilder put(FrameSlot slot, Expr value) { return FrameBuilderNodeGen.create(slot,value); }

  public static Add add(Expr x, Expr y) { return ExprFactory.AddNodeGen.create(x,y); }
  public static Expr booleanLiteral(boolean b) {
    return new Expr() {
      @Override public Object execute(VirtualFrame frame) { return b; }
      @Override public boolean executeBoolean(VirtualFrame frame) { return b; }
    };
  }
  public static Expr intLiteral(int i) {
    return new Expr() {
      @Override public Object execute(VirtualFrame frame) { return i; }
      @Override public int executeInteger(VirtualFrame frame) { return i; }
    };
  }
  public static Expr bigLiteral(Int i) {
    return new Expr() {
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
