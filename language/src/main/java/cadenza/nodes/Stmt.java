package cadenza.nodes;

import cadenza.nbe.NeutralException;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrumentation.*;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

// two kinds of statements, one is top level, the other is an expression?

// block, print can be done at the top level or be treated as a result of IO whatever
// def is a top level statement

// these will typically be 'IO' actions
@GenerateWrapper
public abstract class Stmt extends CadenzaNode.Simple {
  // execute a block of statements returning the last result, this is basically a chain of >>'s in IO. no intermediate lambda results
  public static Do block(Stmt... nodes) { return new Stmt.Do(nodes); }
  public static Def def(FrameSlot slot, Code body) { return StmtFactory.DefNodeGen.create(slot, body); }

  @SuppressWarnings({"unused", "SameReturnValue"})
  @GenerateWrapper.OutgoingConverter
  Object convertOutgoing(@SuppressWarnings("unused") Object object) {
    return null;
  }

  @Override public boolean isInstrumentable() { return true; }

  abstract void execute(VirtualFrame frame);
  public boolean isAdoptable() { return true; }
  public InstrumentableNode.WrapperNode createWrapper(ProbeNode probe) {
    return new StmtWrapper(this,probe);
  }

  public boolean hasTag(Class<? extends Tag> tag) {
    return tag == StandardTags.StatementTag.class || super.hasTag(tag);
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
    @Child public Code arg;
    public Def(FrameSlot slot, Code arg) {
      this.slot = slot;
      this.arg = arg;
    }

    public final void execute(VirtualFrame frame) { executeDef(frame); }

    @SuppressWarnings("UnusedReturnValue")
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
      } catch (NeutralException e) {
        frame.setObject(slot, e.get());
        return 0; // this result is never used
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
      } catch (NeutralException e) {
        frame.setObject(slot, e.get());
        return false; // never used
      }
    }

    @Specialization(replaces={"defInteger","defBoolean"})
    protected void defObject(VirtualFrame frame) {
      frame.setObject(slot, arg.executeAny(frame));
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
}