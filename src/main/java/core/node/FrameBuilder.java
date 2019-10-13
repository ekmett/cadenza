package core.node;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import core.CoreTypes;
import core.node.expr.Expression;

// this copies information from the VirtualFrame frame into a materialized frame
@TypeSystemReference(CoreTypes.class)
@NodeInfo(shortName = "FrameBuilder")
public abstract class FrameBuilder extends Node {
  protected final FrameSlot slot;
  @Child protected Expression rhs;
  public FrameBuilder(FrameSlot slot, Expression rhs) {
    this.slot = slot;
    this.rhs = rhs;
  }

  public void build(VirtualFrame frame, VirtualFrame oldFrame) {
    execute(frame,0, oldFrame);
  }
  public abstract Object execute(VirtualFrame frame, final int hack, VirtualFrame oldFrame);

  boolean allowsSlotKind(VirtualFrame frame, FrameSlotKind kind) {
    FrameSlotKind currentKind = frame.getFrameDescriptor().getFrameSlotKind(slot);
    if (currentKind == FrameSlotKind.Illegal) {
      frame.getFrameDescriptor().setFrameSlotKind(slot,kind);
      return true;
    }
    return currentKind == kind;
  }
  boolean allowsBooleanSlot(VirtualFrame frame) { return allowsSlotKind(frame, FrameSlotKind.Boolean); }
  boolean allowsIntSlot(VirtualFrame frame) { return allowsSlotKind(frame, FrameSlotKind.Int); }

  // UnexpectedResultException lets us "accept" an answer on the slow path, but it forces me to give back an Object. small price to pay
  @Specialization(guards = "allowsBooleanSlot(frame)", rewriteOn = {UnexpectedResultException.class})
  boolean buildBoolean(VirtualFrame frame, final int hack, VirtualFrame oldFrame) throws UnexpectedResultException {
    boolean result;
    try {
      result = rhs.executeBoolean(oldFrame);
    } catch (UnexpectedResultException e) {
      frame.setObject(slot, e);
      throw e;
    }
    frame.setBoolean(slot,result);
    return result;
  }

  @Specialization(guards = "allowsIntSlot(frame)", rewriteOn = {UnexpectedResultException.class})
  int buildInt(VirtualFrame frame, final int hack, VirtualFrame oldFrame) throws UnexpectedResultException {
    int result;
    try {
      result = rhs.executeInteger(oldFrame);
    } catch (UnexpectedResultException e) {
      frame.setObject(slot, e);
      throw e;
    }
    frame.setLong(slot,result);
    return result;
  }

  @Fallback
  Object buildObject(VirtualFrame frame, final int hack, VirtualFrame oldFrame) {
    Object result = rhs.execute(oldFrame);
    frame.setObject(slot, result);
    return result;
  }

  public boolean isAdoptable() { return false; }

  public static FrameBuilder[] noFrameBuilders = new FrameBuilder[]{};
}