package core.values;


import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.library.ExportLibrary;
import core.nodes.CoreExpressionNode;
import core.nodes.CoreRootNode;
import core.Language;

@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary.class)
public final class CoreClosure implements TruffleObject {
  public final RootCallTarget callTarget;
  public final MaterializedFrame env;
  public final FrameSlot argName;

  public CoreClosure(Frame env, FrameSlot argName, RootCallTarget callTarget) {
    this.env = env.materialize();
    this.argName = argName;
    this.callTarget = callTarget;
  }

  //@ExportMessage
  //public boolean isExecutable() {
  //  return true;
  //}

  //@ExportMessage
  //@CompilerDirectives.TruffleBoundary
  //public Object execute(Object... arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
    // we should handle the arguments
  //}

  public static CoreClosure create(Language language, MaterializedFrame env, FrameSlot argName, CoreExpressionNode body) {
    return new CoreClosure(
      env,
      argName,
      Truffle.getRuntime().createCallTarget(
        new CoreRootNode(
          language,
          body,
          env.getFrameDescriptor()
        )
      )
    );
  }
}
