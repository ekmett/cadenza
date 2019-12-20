package cadenza.jit;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.nodes.IndirectCallNode;

public abstract class CallUtils {
  // work around kotlin's brain-dead use of array copying
  public static Object call(IndirectCallNode node, CallTarget target, Object[] arguments) {
    return node.call(target, arguments);
  }
}
