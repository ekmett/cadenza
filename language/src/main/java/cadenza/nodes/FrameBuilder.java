package cadenza.nodes;

import cadenza.nbe.NeutralException;
import cadenza.types.Types;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

// this copies information from the VirtualFrame frame into a materialized frame
@TypeSystemReference(Types.class)
@NodeInfo(shortName = "FrameBuilder")
public abstract class FrameBuilder extends Node {
  protected final FrameSlot slot;
  @SuppressWarnings("CanBeFinal")
  @Child protected Code rhs;
  public FrameBuilder(FrameSlot slot, Code rhs) {
    this.slot = slot;
    this.rhs = rhs;
  }

  public void build(VirtualFrame frame, VirtualFrame oldFrame) {
    execute(frame,0, oldFrame);
  }
  @SuppressWarnings("UnusedReturnValue")
  public abstract Object execute(VirtualFrame frame, final int hack, VirtualFrame oldFrame);

  boolean allowsSlotKind(VirtualFrame frame, FrameSlotKind kind) {
    var currentKind = frame.getFrameDescriptor().getFrameSlotKind(slot);
    if (currentKind == FrameSlotKind.Illegal) {
      frame.getFrameDescriptor().setFrameSlotKind(slot,kind);
      return true;
    }
    return currentKind == kind;
  }
  boolean allowsBooleanSlot(VirtualFrame frame) { return allowsSlotKind(frame, FrameSlotKind.Boolean); }
  boolean allowsIntegerSlot(VirtualFrame frame) { return allowsSlotKind(frame, FrameSlotKind.Int); }

  // UnexpectedResultException lets us "accept" an answer on the slow path, but it forces me to give back an Object. small price to pay
  @Specialization(guards = "allowsBooleanSlot(frame)", rewriteOn = {UnexpectedResultException.class})
  boolean buildBoolean(VirtualFrame frame, final int _hack, VirtualFrame oldFrame) throws UnexpectedResultException {
    boolean result;
    try {
      result = rhs.executeBoolean(oldFrame);
    } catch (UnexpectedResultException e) {
      frame.setObject(slot, e);
      throw e;
    } catch (NeutralException e) {
      frame.setObject(slot, e.get());
      return false; // nonsense, the results are never used, result is to use @Specialization only
    }
    frame.setBoolean(slot,result);
    return result;
  }

  @Specialization(guards = "allowsIntegerSlot(frame)", rewriteOn = {UnexpectedResultException.class})
  int buildInteger(VirtualFrame frame, final int _hack, VirtualFrame oldFrame) throws UnexpectedResultException {
    int result;
    try {
      result = rhs.executeInteger(oldFrame);
    } catch (UnexpectedResultException e) {
      frame.setObject(slot, e);
      throw e;
    } catch (NeutralException e) {
      frame.setObject(slot, e.get());
      return 0;
    }
    frame.setInt(slot,result);
    return result;
  }

  @Fallback
  Object buildObject(VirtualFrame frame, final int _hack, VirtualFrame oldFrame) {
    var result = rhs.executeAny(oldFrame);
    frame.setObject(slot, result);
    return result;
  }

  @Override
  public boolean isAdoptable() { return false; }

  public static final FrameBuilder[] noFrameBuilders = new FrameBuilder[]{};
}