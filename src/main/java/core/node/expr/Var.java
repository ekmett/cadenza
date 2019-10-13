package core.node.expr;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;


@NodeInfo(shortName = "Read")
public abstract class Var extends Expression {
  protected Var(FrameSlot slot) { this.slot = slot; }
  protected final FrameSlot slot;

  @Specialization(rewriteOn = FrameSlotTypeException.class)
  protected long readLong(VirtualFrame frame) throws FrameSlotTypeException {
    return frame.getLong(slot);
  }

  @Specialization(rewriteOn = FrameSlotTypeException.class)
  protected boolean readBoolean(VirtualFrame frame) throws FrameSlotTypeException {
    return frame.getBoolean(slot);
  }

  //@Fallback //results in borked code
  @Specialization(replaces = {"readLong","readBoolean"})
  protected Object read(VirtualFrame frame) {
    return frame.getValue(slot);
  }

  @Override public boolean isAdoptable() { return false; }
}