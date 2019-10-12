package core.values;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import core.CoreTypesGen;
import core.frame.FrameBuilder;
import core.node.FunctionBody;
import core.node.expr.Expression;

@CompilerDirectives.ValueType // screw your reference equality
@ExportLibrary(InteropLibrary.class)
public final class Closure implements TruffleObject {
  // supercombinator referencing the environment
  public Closure(MaterializedFrame env, FunctionBody body) {
    this.env = env;
    this.directCallNode = Truffle.getRuntime().createDirectCallNode(Truffle.getRuntime().createCallTarget(body));
  }

  // combinator
  public Closure(FunctionBody body) { this(null, body); }

  // null implies that this is a simple combinator with no environment
  private MaterializedFrame env;
  private DirectCallNode directCallNode;

  @ExplodeLoop
  private Object[] passEnv(Object[] arguments) {
    Object[] newArguments = new Object[arguments.length + 1];
    newArguments[0] = env;
    for (int i = 0; i < arguments.length; ++i) newArguments[i + 1] = arguments[i];
    return newArguments;
  }

  @ExportMessage
  public final boolean isExecutable() { return true; }

  @ExportMessage
  @CompilerDirectives.TruffleBoundary
  public final Object execute(Object... arguments) {
    return call(arguments);
  }
  // NOT a truffle boundary
  public final Object call(Object... arguments) {
    return env == null
      ? directCallNode.call(arguments)
      : directCallNode.call(passEnv(arguments));
  }

  public long callLong(Object... arguments) throws UnexpectedResultException {
    return CoreTypesGen.expectLong(call(arguments));
  }

  public boolean callBoolean(Object... arguments) throws UnexpectedResultException {
    return CoreTypesGen.expectBoolean(call(arguments));
  }

  public Closure callClosure(Object... arguments) throws UnexpectedResultException {
    return CoreTypesGen.expectClosure(call(arguments));
  }
}