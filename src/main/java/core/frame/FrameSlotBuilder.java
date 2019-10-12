package core.frame;

import core.Types;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import core.node.expr.Expression;

@TypeSystemReference(Types.class)
@NodeInfo(shortName = "FrameBuilder")
//@NodeChild(type = Expression.class)
@NodeField(name="slot", type = FrameSlot.class)
public abstract class FrameSlotBuilder extends Node {
  protected abstract FrameSlot getSlot();
  public abstract void execute(VirtualFrame frame, Frame newFrame);


//  @Specialization(rewriteOn = {FrameSlotTypeException.class, UnexpectedResultException.class})
  void copyBoolean(VirtualFrame frame, Frame newFrame, boolean rhs) throws FrameSlotTypeException, UnexpectedResultException {
     newFrame.setBoolean(getSlot(), rhs);
  }

//  @Specialization(rewriteOn = {FrameSlotTypeException.class, UnexpectedResultException.class})
  void copyLong(VirtualFrame frame, Frame newFrame, long rhs) throws FrameSlotTypeException, UnexpectedResultException {
    newFrame.setLong(getSlot(), rhs);
  }

//  @Fallback
  void copyObject(VirtualFrame frame, Frame newFrame, Object rhs) {
    newFrame.setObject(getSlot(), rhs);
  }

  public boolean isAdoptable() { return false; }

}
