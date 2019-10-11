package core.node;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import core.Types;
import core.node.expr.CoreExpressionNode;

@TypeSystemReference(Types.class)
public abstract class FrameBuilder extends Node {
  public abstract void run(VirtualFrame frame, MaterializedFrame newFrame);

  public static FrameBuilder createSlotBuilder(FrameSlot slot, CoreExpressionNode rhs) {
    return new FrameSlotBuilder.FirstBuilder(slot, rhs);
  }
}

// we need to manually rebuild these because I want to pass newFrame to run and the truffle folks never imagined that
abstract class FrameSlotBuilder extends FrameBuilder {
  protected final FrameSlot slot;
  @Child protected CoreExpressionNode rhs;

  FrameSlotBuilder(FrameSlot slot, CoreExpressionNode rhs) {
    this.slot = slot;
    this.rhs = rhs;
  }

  protected boolean isKindOrIllegal(FrameDescriptor fd, FrameSlotKind kind) {
    FrameSlotKind slotKind = fd.getFrameSlotKind(slot);
    if (kind == slotKind) return true;
    if (slotKind == FrameSlotKind.Illegal) {
      CompilerDirectives.transferToInterpreterAndInvalidate(); // bail and set up the slot
      fd.setFrameSlotKind(slot,kind);
      return true;
    }
    return false;
  }

  protected void runObject(VirtualFrame frame, MaterializedFrame newFrame) {
    newFrame.setObject(slot, rhs.execute(frame));
  }

  protected void runBoolean(VirtualFrame frame, MaterializedFrame newFrame) {
    try {
      if (!isKindOrIllegal(frame.getFrameDescriptor(),FrameSlotKind.Boolean)) throw new FrameSlotTypeException();
      newFrame.setBoolean(slot, rhs.executeBoolean(frame));
    } catch (UnexpectedResultException | FrameSlotTypeException e) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      runObject(frame, newFrame);
      this.replace(new ObjectBuilder(slot, rhs));
    }
  }

  protected void runLong(VirtualFrame frame, MaterializedFrame newFrame) {
    try {
      if (!isKindOrIllegal(frame.getFrameDescriptor(),FrameSlotKind.Long)) throw new FrameSlotTypeException();
      newFrame.setLong(slot, rhs.executeLong(frame));
    } catch (UnexpectedResultException | FrameSlotTypeException e) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      runObject(frame, newFrame);
      this.replace(new ObjectBuilder(slot, rhs));
    }
  }

  public void runFirst(VirtualFrame frame, MaterializedFrame newFrame) {
    CompilerDirectives.transferToInterpreterAndInvalidate();
    FrameDescriptor fd = newFrame.getFrameDescriptor();
    this.replace
    ( isKindOrIllegal(fd, FrameSlotKind.Boolean) ? new BooleanBuilder(slot, rhs)
    : isKindOrIllegal(fd, FrameSlotKind.Long) ? new LongBuilder(slot, rhs)
      // extend with other refinements here
    : new ObjectBuilder(slot, rhs)
    ).run(frame, newFrame);
  }


  static class ObjectBuilder extends FrameSlotBuilder {
    ObjectBuilder(FrameSlot slot, CoreExpressionNode rhs) { super(slot, rhs); }
    public void run(VirtualFrame frame, MaterializedFrame newFrame) { runObject(frame, newFrame); }
  }

  static class BooleanBuilder extends FrameSlotBuilder {
    BooleanBuilder(FrameSlot slot, CoreExpressionNode rhs) { super(slot, rhs); }
    public void run(VirtualFrame frame, MaterializedFrame newFrame) { runBoolean(frame, newFrame); }
  }

  static class LongBuilder extends FrameSlotBuilder {
    LongBuilder(FrameSlot slot, CoreExpressionNode rhs) { super(slot, rhs); }
    public void run(VirtualFrame frame, MaterializedFrame newFrame) { runLong(frame, newFrame); }
  }

  static class FirstBuilder extends FrameSlotBuilder {
    FirstBuilder(FrameSlot slot, CoreExpressionNode rhs) { super(slot, rhs); }
    public void run(VirtualFrame frame, MaterializedFrame newFrame) { runFirst(frame, newFrame); }
  }
}