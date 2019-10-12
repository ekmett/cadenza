package core.frame;

import core.CoreTypes;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import core.node.expr.Expression;

@TypeSystemReference(CoreTypes.class)
@NodeInfo(shortName = "FrameBuilder")
public abstract class FrameBuilder extends Node {
  protected final FrameSlot slot;
  @Child protected Expression rhs;
  public FrameBuilder(FrameSlot slot, Expression rhs) {
    this.slot = slot;
    this.rhs = rhs;
  }

  public final void execute(VirtualFrame frame, Frame newFrame) { execute(frame, null, newFrame); }

  // 'hack' works around oracle/graal#1740
  public abstract void execute(VirtualFrame frame, final Object hack, Frame newFrame);

  //@Specialization(rewriteOn = {FrameSlotTypeException.class, UnexpectedResultException.class})
  void executeBoolean(VirtualFrame frame, final Object hack, Frame newFrame, boolean rhs) {
     newFrame.setBoolean(slot, rhs);
  }

  //@Specialization(rewriteOn = {FrameSlotTypeException.class, UnexpectedResultException.class})
  void executeLong(VirtualFrame frame, final Object hack, Frame newFrame, long rhs) {
    newFrame.setLong(slot, rhs);
  }

  //@Fallback
  void executeObject(VirtualFrame frame, final Object hack, Frame newFrame, Object rhs) {
    newFrame.setObject(slot, rhs);
  }

  public boolean isAdoptable() { return false; }

}
