package core.node.expr;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.ConditionProfile;
import core.values.Closure;

public class If extends Expression {
  @SuppressWarnings("CanBeFinal")
  @Child
  private Expression bodyNode, thenNode, elseNode;
  private final ConditionProfile conditionProfile = ConditionProfile.createBinaryProfile();
  public If(Expression bodyNode, Expression thenNode, Expression elseNode) {
    this.bodyNode = bodyNode;
    this.thenNode = thenNode;
    this.elseNode = elseNode;
  }

  @Override
  public Object execute(VirtualFrame frame) {
    return conditionProfile.profile(branch(frame))
      ? this.thenNode.execute(frame)
      : this.elseNode.execute(frame);
  }

  @Override
  public long executeLong(VirtualFrame frame) throws UnexpectedResultException {
    return conditionProfile.profile(branch(frame))
      ? this.thenNode.executeLong(frame)
      : this.elseNode.executeLong(frame);
  }


  @Override
  public boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException {
    return conditionProfile.profile(branch(frame))
      ? this.thenNode.executeBoolean(frame)
      : this.elseNode.executeBoolean(frame);
  }

  @Override
  public Closure executeClosure(VirtualFrame frame) throws UnexpectedResultException {
    return conditionProfile.profile(branch(frame))
      ? this.thenNode.executeClosure(frame)
      : this.elseNode.executeClosure(frame);
  }


  private boolean branch(VirtualFrame frame) {
    try {
      return bodyNode.executeBoolean(frame);
    } catch (UnexpectedResultException e) {
      throw new RuntimeException("condition not boolean",e);
    }
  }
}
