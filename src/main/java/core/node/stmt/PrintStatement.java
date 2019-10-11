package core.node.stmt;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;
import core.node.expr.Expression;

@NodeInfo(shortName = "Print")
@NodeChild(value = "value", type = Expression.class)
public abstract class PrintStatement extends Statement {
  @Specialization
  void print(Object value) {
    System.out.println(value);
  }
}
