package core.frame;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import core.node.expr.Expression;

public class FrameSlotBuilder extends FrameBuilder {
  final FrameSlot slot;
  @Child Expression rhs;

  public FrameSlotBuilder(FrameSlot slot, Expression rhs) {
    this.slot = slot;
    this.rhs = rhs;
  }

  // @Specialization(rewriteOn = {FrameSlotTypeException.class, UnexpectedResultException.class})
  void executeBoolean(VirtualFrame frame, MaterializedFrame newFrame, FrameSlot slot, Expression rhs) throws FrameSlotTypeException, UnexpectedResultException {
     newFrame.setBoolean(slot, rhs.executeBoolean(frame));
  }

  // @Specialization(rewriteOn = {FrameSlotTypeException.class, UnexpectedResultException.class})
  void executeLong(VirtualFrame frame, MaterializedFrame newFrame, FrameSlot slot, Expression rhs) throws FrameSlotTypeException, UnexpectedResultException {
    newFrame.setLong(slot, rhs.executeLong(frame));
  }

  // @Specialization(replaces = {"executeBoolean","executeLong"})
  void executeObject(VirtualFrame frame, MaterializedFrame newFrame, FrameSlot slot, Expression rhs) {
    newFrame.setLong(slot, rhs.executeLong(frame));
  }

  public boolean isAdoptable() { return false; }

  @Override
  public void execute(VirtualFrame frame, MaterializedFrame newFrame) {
    // this is the workhorse
  }
}
