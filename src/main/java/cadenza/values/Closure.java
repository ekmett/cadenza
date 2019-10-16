package cadenza.values;

import cadenza.types.Type;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import cadenza.nodes.*;

@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary.class)
public class Closure implements TruffleObject {
  public final RootCallTarget callTarget;
  public final int arity; // local minimum arity to do anything, below this construct PAPs, above this pump arguments.
  public final Type type;
  public final MaterializedFrame env;

  // invariant: target should have been constructed from a FunctionBody
  // also assumes that env matches the shape expected by the function body
  public Closure(MaterializedFrame env, int arity, Type type, RootCallTarget callTarget) {
    assert callTarget.getRootNode() instanceof ClosureRootNode : "not a function body";
    assert (env != null) == ((ClosureRootNode)callTarget.getRootNode()).isSuperCombinator() : "calling convention mismatch";
    assert arity <= type.getArity();
    this.arity = arity;
    this.callTarget = callTarget;
    this.env = env;
    this.type = type;
  }

  // combinator
  public Closure(int arity, Type type, RootCallTarget callTarget) {
    this(null, arity, type, callTarget);
  }
  public final boolean isSuperCombinator() {
    return env != null;
  }

  @ExportMessage
  public final boolean isExecutable() { return true; }

  // allow the use of our closures from other polyglot languages
  @ExportMessage
  @ExplodeLoop
  public final Object execute(Object... arguments) throws ArityException, UnsupportedTypeException {
    int maxArity = type.getArity();
    int len = arguments.length;
    if (len > maxArity) throw ArityException.create(maxArity, len);
    Type currentType = type;
    for (int i=0;i<len;++i) { // lint foreign arguments for safety
      Type.Arr arr = (Type.Arr)currentType; // safe by arity check
      arr.argument.validate(arguments[i]);
      currentType = arr.result;
    }
    return call(arguments);
  }

  public final Object call(Object... arguments) {
    return isSuperCombinator()
      ? callTarget.call(cons(env,arguments))
      : callTarget.call(arguments);
  }

  @ExplodeLoop
  private static Object[] cons(Object x, Object[] xs) {
    Object[] ys = new Object[xs.length + 1];
    ys[0] = x;
    System.arraycopy(xs, 0, ys, 1, xs.length);
    return ys;
  }

}