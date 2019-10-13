package core.node.expr;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.nodes.NodeInfo;
import core.values.BigNumber;

@NodeChild
@NodeChild
@NodeInfo(shortName="+")
public abstract class Add extends Expression {
  @Specialization
  public int add(int x, int y) {
    return x + y;
  }
  @Specialization
  public BigNumber add(BigNumber x, BigNumber y) {
    return new BigNumber(x.value.add(y.value));
  }
}
