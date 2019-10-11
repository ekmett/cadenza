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

@CompilerDirectives.ValueType // screw your reference equality
@ExportLibrary(InteropLibrary.class)
public abstract class Closure implements TruffleObject {


  @ExportMessage
  public boolean isExecutable() {
    return true;
  }

  @ExportMessage
  @CompilerDirectives.TruffleBoundary
  public abstract Object execute(Object... arguments);

  public static Closure create(Language language, MaterializedFrame env, Expression body) {
    RootCallTarget target = Truffle.getRuntime().createCallTarget(CoreRootNode.create(language, body, env.getFrameDescriptor()));
    return new Closure() {
      public Object execute(Object... arguments) {
        return target.call(arguments);
      }
    };
  }

  public static Closure create(MaterializedFrame newFrame, RootCallTarget callTarget) {
    return new Closure() {
      public Object execute(Object... arguments) {
        return null; // too sleepy to finish
        // Truffle.getRuntime().createCallTarget(CoreRootNode.create(null, Expressions.lam(callTarget)));  11111111111111``````````````````````1``````````````````````        callTarget.getRootNode().execute(newFrame);
        //return callTarget.call(arguments);
      }
    };
  }

}