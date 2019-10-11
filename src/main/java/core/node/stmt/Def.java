package core.node.stmt;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import core.node.expr.CoreExpressionNode;

//TODO: use a better internal state management system like the generated code would
@NodeInfo(shortName = "Def")
public abstract class Def extends CoreStatementNode {
  public FrameSlot slot;
  @Child public CoreExpressionNode arg;
  public Def(FrameSlot slot, CoreExpressionNode arg) {
    this.slot = slot;
    this.arg = arg;
  }

  @Override public boolean isAdoptable() { return false; }

  public void defLong(VirtualFrame frame, FrameSlot slot, CoreExpressionNode arg) {
    try {
      if (!isLongOrIllegal(frame.getFrameDescriptor())) throw new FrameSlotTypeException();
      frame.setLong(slot, arg.executeLong(frame));
    } catch (FrameSlotTypeException|UnexpectedResultException e) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      this.replace(new DefObject(slot,arg)).defObject(frame,slot,arg);
    }
  }

  public void defBoolean(VirtualFrame frame, FrameSlot slot, CoreExpressionNode arg) {
    try {
      if (!isBooleanOrIllegal(frame.getFrameDescriptor())) throw new FrameSlotTypeException();
      frame.setLong(slot, arg.executeLong(frame));
    } catch (FrameSlotTypeException|UnexpectedResultException e) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      this.replace(new DefObject(slot,arg)).defObject(frame,slot,arg);
    }
  }

  public void defObject(VirtualFrame frame, FrameSlot slot, CoreExpressionNode arg) {
    frame.setObject(slot, arg.execute(frame));
  }

  public void defFirst(VirtualFrame frame, FrameSlot slot, CoreExpressionNode arg) {
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

  static class DefLong extends Def {
    DefLong(FrameSlot slot, CoreExpressionNode arg) { super(slot, arg); }
    public void execute(VirtualFrame frame) { defLong(frame, slot, arg); }
  }

  static class DefBoolean extends Def {
    DefBoolean(FrameSlot slot, CoreExpressionNode arg) { super(slot, arg); }
    public void execute(VirtualFrame frame) { defBoolean(frame, slot, arg); }
  }

  static class DefObject extends Def {
    DefObject(FrameSlot slot, CoreExpressionNode arg) { super(slot, arg); }
    public void execute(VirtualFrame frame) { defObject(frame, slot, arg); }
  }

  static class DefFirst extends Def {
    DefFirst(FrameSlot slot, CoreExpressionNode arg) { super(slot, arg); }
    public void execute(VirtualFrame frame) { defFirst(frame, slot, arg); }
  }

  public static Def create(FrameSlot slot, CoreExpressionNode arg) {
    return new DefFirst(slot,arg);
  }
}