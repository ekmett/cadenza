package core.values;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import core.node.expr.Expression;
import core.node.CoreRootNode;
import core.Language;

@ExportLibrary(InteropLibrary.class)
public class Closure implements TruffleObject {
  public final RootCallTarget target;
  public final MaterializedFrame env;
  public Closure(MaterializedFrame env, RootCallTarget target) {
    this.env = env;
    this.target = target;
  }

  public static Closure create(Language language, MaterializedFrame env, Expression body) {
    return new Closure(
      env,
      Truffle.getRuntime().createCallTarget(
        new CoreRootNode(
          language,
          body,
          env.getFrameDescriptor()
        )
      )
    );
  }

  @ExportMessage
  public boolean isExecutable() { return true; }

  @ExportMessage
  @CompilerDirectives.TruffleBoundary
  public Object execute(Object... arguments) throws ArityException {
    return target.call(arguments);
  }
}