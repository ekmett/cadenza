package core;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.SourceSection;

// report a runtime type mismatch in the core
public class Dump extends RuntimeException implements TruffleException {

  private final Node location;

  @SuppressWarnings("WeakerAccess")
  @TruffleBoundary
  public Dump(String message, Node location) {
    super(message);
    this.location = location;
  }

  @SuppressWarnings("sync-override")
  @Override
  public final Throwable fillInStackTrace() {
    return this;
  }

  public Node getLocation() {
    return location;
  }

  @TruffleBoundary
  public static Dump typeError(Node operation, Object... values) {
    StringBuilder result = new StringBuilder();
    result.append("Type error");

    if (operation != null) {
      SourceSection ss = operation.getEncapsulatingSourceSection();
      if (ss != null && ss.isAvailable()) {
        result.append(" at ").append(ss.getSource().getName()).append(" line ").append(ss.getStartLine()).append(" col ").append(ss.getStartColumn());
      }
    }

    result.append(": operation");
    if (operation != null) {
      NodeInfo nodeInfo = Context.lookupNodeInfo(operation.getClass());
      if (nodeInfo != null) {
        result.append(" \"").append(nodeInfo.shortName()).append("\"");
      }
    }

    result.append(" not defined for");

    String sep = " ";
    for (Object value : values) {
      result.append(sep);
      sep = ", ";
      if (value == null || InteropLibrary.getFactory().getUncached().isNull(value)) {
        result.append(Language.toString(value));
      } else {
        result.append(Language.getMetaObject(value));
        result.append(" ");
        if (InteropLibrary.getFactory().getUncached().isString(value)) {
          result.append("\"");
        }
        result.append(Language.toString(value));
        if (InteropLibrary.getFactory().getUncached().isString(value)) {
          result.append("\"");
        }
      }
    }
    return new Dump(result.toString(), operation);
  }
}
