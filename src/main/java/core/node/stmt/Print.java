package core.node.stmt;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import core.node.expr.CoreExpressionNode;

@NodeChild(value = "value", type = CoreExpressionNode.class)
public abstract class Print extends CoreStatementNode {
  @Specialization
  void print(Object value) {
    System.out.println(value);
  }
}
