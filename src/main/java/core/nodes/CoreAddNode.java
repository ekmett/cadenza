package core.nodes;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImplicitCast;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;
import core.Dump;
import core.values.CoreBigInteger;

@NodeInfo(shortName = "+")
public abstract class CoreAddNode extends CoreBinaryNode {
  @Specialization(rewriteOn = ArithmeticException.class)
  protected long add(long left, long right) {
    return Math.addExact(left,right);
  }

  @Specialization
  @TruffleBoundary
  protected CoreBigInteger add(CoreBigInteger left, CoreBigInteger right) {
    return new CoreBigInteger(left.getValue().add(right.getValue()));
  }

  @Fallback
  protected Object typeError(Object left, Object right) {
    throw Dump.typeError(this,left,right);
  }
}
