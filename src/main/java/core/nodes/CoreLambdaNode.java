package core.nodes;

import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import core.values.Closure;

@SuppressWarnings("ALL")
@NodeField(name = "function", type = Closure.class)
@TypeSystemReference(Types.class)
public abstract class CoreLambdaNode extends CoreNode {
  public abstract Closure getFunction();
}
