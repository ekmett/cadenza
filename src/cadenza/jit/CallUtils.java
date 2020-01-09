package cadenza.jit;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
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
  @ExplodeLoop
  public static Object[] copyArray(Object[] array, int len) {
    Object[] out = new Object[len];
//    for (int i = 0; i < len; i++) {
//      out[i] = array[i];
//    }
    System.arraycopy(array, 0, out, 0, len);
    return out;
  }
}
