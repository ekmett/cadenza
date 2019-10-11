package core.values;


import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import core.nodes.CoreNode;
import core.nodes.CoreRootNode;
import core.Language;

@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary.class)
public final class Closure implements TruffleObject {
  public final RootCallTarget callTarget;
  public final MaterializedFrame env;
  public final FrameSlot[] argNames;

  public Closure(Frame env, FrameSlot[] argNames, RootCallTarget callTarget) {
    this.env = env.materialize();
    this.argNames = argNames;
    this.callTarget = callTarget;
  }

  public static Closure create(Language language, MaterializedFrame env, FrameSlot[] argNames, CoreNode body) {
    return new Closure(
      env,
      argNames,
      Truffle.getRuntime().createCallTarget(
        new CoreRootNode(
          language,
          body,
          env.getFrameDescriptor()
        )
      )
    );
  }

  // to allow polyglot export of closures

  @ExportMessage
  public boolean isExecutable() { return true; }

  @ExportMessage
  @CompilerDirectives.TruffleBoundary
  public Object execute(Object... arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
    return callTarget.call(arguments);
  }
  // we should handle the arguments
  //}


}
