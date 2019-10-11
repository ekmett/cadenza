package core.node.stmt;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import core.node.expr.Expression;

//TODO: use a better internal state management system like the generated code would
@NodeInfo(shortName = "Def")
public abstract class DefStatement extends Statement {
  public FrameSlot slot;
  @Child public Expression arg;
  public DefStatement(FrameSlot slot, Expression arg) {
    this.slot = slot;
    this.arg = arg;
  }

  @Override public boolean isAdoptable() { return false; }

  public void defLong(VirtualFrame frame, FrameSlot slot, Expression arg) {
    try {
      if (!isLongOrIllegal(frame.getFrameDescriptor())) throw new FrameSlotTypeException();
      frame.setLong(slot, arg.executeLong(frame));
    } catch (FrameSlotTypeException|UnexpectedResultException e) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      this.replace(new DefObject(slot,arg)).defObject(frame,slot,arg);
    }
  }

  public void defBoolean(VirtualFrame frame, FrameSlot slot, Expression arg) {
    try {
      if (!isBooleanOrIllegal(frame.getFrameDescriptor())) throw new FrameSlotTypeException();
      frame.setLong(slot, arg.executeLong(frame));
    } catch (FrameSlotTypeException|UnexpectedResultException e) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      this.replace(new DefObject(slot,arg)).defObject(frame,slot,arg);
    }
  }

  public void defObject(VirtualFrame frame, FrameSlot slot, Expression arg) {
    frame.setObject(slot, arg.execute(frame));
  }

  public void defFirst(VirtualFrame frame, FrameSlot slot, Expression arg) {
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

  static class DefLong extends DefStatement {
    DefLong(FrameSlot slot, Expression arg) { super(slot, arg); }
    public void execute(VirtualFrame frame) { defLong(frame, slot, arg); }
  }

  static class DefBoolean extends DefStatement {
    DefBoolean(FrameSlot slot, Expression arg) { super(slot, arg); }
    public void execute(VirtualFrame frame) { defBoolean(frame, slot, arg); }
  }

  static class DefObject extends DefStatement {
    DefObject(FrameSlot slot, Expression arg) { super(slot, arg); }
    public void execute(VirtualFrame frame) { defObject(frame, slot, arg); }
  }

  static class DefFirst extends DefStatement {
    DefFirst(FrameSlot slot, Expression arg) { super(slot, arg); }
    public void execute(VirtualFrame frame) { defFirst(frame, slot, arg); }
  }

  public static DefStatement create(FrameSlot slot, Expression arg) {
    return new DefFirst(slot,arg);
  }
}