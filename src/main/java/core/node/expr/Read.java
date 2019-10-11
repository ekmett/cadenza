package core.node.expr;

import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;

// read a variable from the frame
@NodeField(name = "slot", type = FrameSlot.class)
@NodeInfo(shortName = "Read")
public abstract class Read extends CoreExpressionNode {
  public abstract FrameSlot getSlot();
  //@Override String toString() {
//    return "'" + this.getSlot().getIdentifier();
//  }

  @Specialization(rewriteOn = FrameSlotTypeException.class)
  protected long readLong(VirtualFrame frame) throws FrameSlotTypeException {
    return frame.getLong(getSlot());
  }

  @Specialization(rewriteOn = FrameSlotTypeException.class)
  protected boolean readBoolean(VirtualFrame frame) throws FrameSlotTypeException {
    return frame.getBoolean(getSlot());
  }

  @Specialization(replaces = {"readLong", "readBoolean"})
  protected Object read(VirtualFrame frame) {
    return frame.getValue(getSlot());
  }
}