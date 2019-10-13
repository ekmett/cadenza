package core.nodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrumentation.*;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

// A top level core statement, definitions, imports, exports, imperative print statements, whatever.
@GenerateWrapper
public abstract class Stmt extends CoreNode.Simple {
  public static Do block(Stmt... nodes) { return new Stmt.Do(nodes); }
  public static Def def(FrameSlot slot, Expr body) { return StmtFactory.DefNodeGen.create(slot, body); }
  public static Print print(Expr body) { return StmtFactory.PrintNodeGen.create(body); }

  @GenerateWrapper.OutgoingConverter
  Object convertOutgoing(@SuppressWarnings("unused") Object object) {
    return null;
  }

  abstract void execute(VirtualFrame frame);
  public boolean isAdoptable() { return true; }
  public InstrumentableNode.WrapperNode createWrapper(ProbeNode probe) {
    return new StmtWrapper(this,probe);
  }
  public boolean hasTag(Class<? extends Tag> tag) {
    return tag == StandardTags.StatementTag.class;
  }

  public static class Do extends Stmt {
    @SuppressWarnings("CanBeFinal")
    @Children Stmt[] body;
    Do(Stmt[] body) { this.body = body; }

    @Override
    void execute(VirtualFrame frame) {
      for (Stmt stmt : body) stmt.execute(frame);
    }
  }

  //TODO: use a better internal state management system like the generated code would
  @NodeInfo(shortName = "Def")
  public abstract static class Def extends Stmt {
    public final FrameSlot slot;
    @SuppressWarnings("CanBeFinal")
    @Child public Expr arg;
    public Def(FrameSlot slot, Expr arg) {
      this.slot = slot;
      this.arg = arg;
    }

    final void execute(VirtualFrame frame) { executeDef(frame); }

    protected abstract Object executeDef(VirtualFrame frame);

    @Specialization(guards = "allowsIntegerSlot(frame)", rewriteOn = {UnexpectedResultException.class})
    protected int defInteger(VirtualFrame frame) throws UnexpectedResultException {
      try {
        int result = arg.executeInteger(frame);
        frame.setInt(slot, result);
        return result;
      } catch (UnexpectedResultException e) {
        frame.setObject(slot, e);
        throw e;
      }
    }

    @Specialization(guards = "allowsBooleanSlot(frame)", rewriteOn = {UnexpectedResultException.class})
    protected boolean defBoolean(VirtualFrame frame) throws UnexpectedResultException {
      try {
        boolean result = arg.executeBoolean(frame);
        frame.setBoolean(slot, result);
        return result;
      } catch (UnexpectedResultException e) {
        frame.setObject(slot, e);
        throw e;
      }
    }

    @Specialization(replaces={"defInteger","defBoolean"})
    protected void defObject(VirtualFrame frame) {
      frame.setObject(slot, arg.execute(frame));
    }

    boolean allowsSlotKind(VirtualFrame frame, FrameSlotKind kind) {
      FrameSlotKind currentKind = frame.getFrameDescriptor().getFrameSlotKind(slot);
      if (currentKind == FrameSlotKind.Illegal) {
        frame.getFrameDescriptor().setFrameSlotKind(slot,kind);
        return true;
      }
      return currentKind == kind;
    }
    boolean allowsBooleanSlot(VirtualFrame frame) { return allowsSlotKind(frame, FrameSlotKind.Boolean); }
    boolean allowsIntegerSlot(VirtualFrame frame) { return allowsSlotKind(frame, FrameSlotKind.Int); }
  }

  @NodeInfo(shortName = "Print")
  @NodeChild(value = "value", type = Expr.class)
  public abstract static class Print extends Stmt {
    @Specialization
    void print(Object value) {
      System.out.println(value);
    }
  }
}