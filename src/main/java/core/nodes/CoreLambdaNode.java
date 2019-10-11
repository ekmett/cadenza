package core.nodes;

import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.frame.VirtualFrame;
import core.values.CoreClosure;

@NodeField(name = "function", type = CoreClosure.class)
public abstract class CoreLambdaNode extends CoreNode {
  public abstract CoreClosure getFunction();

  public CoreClosure getScopedFunction(VirtualFrame frame) {
    return this.getFunction();
  }
}
