package core.values;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import core.node.FunctionBody;

@CompilerDirectives.ValueType // screw your reference equality
@ExportLibrary(InteropLibrary.class)
public class Closure implements TruffleObject {
  public final RootCallTarget callTarget;
  public final MaterializedFrame env; // possibly null;

  // invariant: target should have been constructed from a FunctionBody
  // also assumes that env matches the shape expected by the function body
  public Closure(MaterializedFrame env, RootCallTarget callTarget) {
    assert callTarget.getRootNode() instanceof FunctionBody : "not a function body";
    assert (env != null) == ((FunctionBody)callTarget.getRootNode()).isSuperCombinator() : "calling convention mismatch";
    this.callTarget = callTarget;
    this.env = env;
  }

  // combinator
  public Closure(RootCallTarget callTarget) {
    this(null, callTarget);
  }
  public final boolean isSuperCombinator() {
    return env != null;
  }

  @ExportMessage
  public final boolean isExecutable() { return true; }

  // allow the use of our closures from other polyglot languages
  @ExportMessage
  @CompilerDirectives.TruffleBoundary
  public final Object execute(Object... arguments) {
    return call(arguments);
  }

  // not a truffle boundary, this code will likely wind up inlined into App, so KISS
  public final Object call(Object... arguments) {
    return isSuperCombinator()
      ? callTarget.call(cons(env,arguments))
      : callTarget.call(arguments);
  }

  @ExplodeLoop
  private static final Object[] cons(Object x, Object[] xs) {
    Object[] ys = new Object[xs.length + 1];
    ys[0] = x;
    for (int i = 0; i < xs.length; ++i) ys[i + 1] = xs[i];
    return ys;
  }
}