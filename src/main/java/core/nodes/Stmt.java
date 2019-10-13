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
  public static Def def(FrameSlot slot, Expr body) { return Stmt.Def.create(slot, body); }
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
    @SuppressWarnings("CanBeFinal")
    public FrameSlot slot;
    @SuppressWarnings("CanBeFinal")
    @Child public Expr arg;
    public Def(FrameSlot slot, Expr arg) {
      this.slot = slot;
      this.arg = arg;
    }

    public void defObject(VirtualFrame frame, FrameSlot slot, Expr arg) {
      frame.setObject(slot, arg.execute(frame));
    }

    public void defFirst(VirtualFrame frame, FrameSlot slot, Expr arg) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      FrameDescriptor fd = frame.getFrameDescriptor();
      this.replace
        ( isKindOrIllegal(fd, FrameSlotKind.Boolean) ? new DefBoolean(slot, arg)
        : isKindOrIllegal(fd, FrameSlotKind.Long) ? new DefLong(slot, arg)
        : new DefObject(slot, arg)
        ).execute(frame);
    }

    protected boolean isLongOrIllegal(FrameDescriptor fd) {
      return isKindOrIllegal(fd, FrameSlotKind.Long);
    }

    protected boolean isBooleanOrIllegal(FrameDescriptor fd) {
      return isKindOrIllegal(fd, FrameSlotKind.Boolean);
    }

    protected boolean isKindOrIllegal(FrameDescriptor fd, FrameSlotKind kind) {
      FrameSlotKind slotKind = fd.getFrameSlotKind(slot); // the frameSlot.getKind() deprecation is bullshit
      if (kind == slotKind) return true;
      if (slotKind == FrameSlotKind.Illegal) {
        CompilerDirectives.transferToInterpreterAndInvalidate(); // bail and set up the slot
        fd.setFrameSlotKind(slot, kind);
        return true;
      }
      return false;
    }

    protected static class DefLong extends Def {
      protected DefLong(FrameSlot slot, Expr arg) { super(slot, arg); }
      @Override public void execute(VirtualFrame frame) {
        try {
          if (!isLongOrIllegal(frame.getFrameDescriptor())) throw new FrameSlotTypeException();
          frame.setLong(slot, arg.executeInteger(frame));
        } catch (FrameSlotTypeException| UnexpectedResultException e) {
          CompilerDirectives.transferToInterpreterAndInvalidate();
          this.replace(new DefObject(slot,arg)).defObject(frame,slot,arg);
        }
      }
    }

    protected static class DefBoolean extends Def {
      DefBoolean(FrameSlot slot, Expr arg) { super(slot, arg); }
      @Override public void execute(VirtualFrame frame) {
        try {
          if (!isBooleanOrIllegal(frame.getFrameDescriptor())) throw new FrameSlotTypeException();
          frame.setInt(slot, arg.executeInteger(frame));
        } catch (FrameSlotTypeException|UnexpectedResultException e) {
          CompilerDirectives.transferToInterpreterAndInvalidate();
          this.replace(new DefObject(slot,arg)).defObject(frame,slot,arg);
        }
      }
    }

    protected static class DefObject extends Def {
      DefObject(FrameSlot slot, Expr arg) { super(slot, arg); }
      @Override public void execute(VirtualFrame frame) { defObject(frame, slot, arg); }
    }

    public static Def create(FrameSlot slot, Expr arg) {
      return new Def(slot,arg) {
        @Override void execute(VirtualFrame frame) { defFirst(frame, slot, arg); }
      };
    }
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