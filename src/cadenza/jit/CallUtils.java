package cadenza.jit;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;

public abstract class CallUtils {
  // work around kotlin's brain-dead use of array copying
  public static Object callIndirect(IndirectCallNode node, CallTarget target, Object[] arguments) {
    return node.call(target, arguments);
  }
  public static Object callDirect(DirectCallNode node, Object[] arguments) {
    return node.call(arguments);
  }
  public static Object callTarget(CallTarget target, Object[] arguments) {
    return target.call(arguments);
  }
}
