package core.frame;

import core.CoreTypes;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

@TypeSystemReference(CoreTypes.class)
@NodeInfo(shortName = "FrameBuilder")
//@NodeChild(type = Expression.class)
@NodeField(name="slot", type = FrameSlot.class)
public abstract class MaterialBuilder extends Node {
  protected abstract FrameSlot getSlot();
  public abstract void execute(VirtualFrame frame, MaterializedFrame newFrame);

//  @Specialization(rewriteOn = {FrameSlotTypeException.class, UnexpectedResultException.class})
  void executeBoolean(VirtualFrame frame, Frame newFrame, boolean rhs) {
     newFrame.setBoolean(getSlot(), rhs);
  }

//  @Specialization(rewriteOn = {FrameSlotTypeException.class, UnexpectedResultException.class})
  void executeLong(VirtualFrame frame, Frame newFrame, long rhs) {
    newFrame.setLong(getSlot(), rhs);
  }

//  @Fallback
  void executeObject(VirtualFrame frame, Frame newFrame, Object rhs) {
    newFrame.setObject(getSlot(), rhs);
  }

  public boolean isAdoptable() { return false; }

}
