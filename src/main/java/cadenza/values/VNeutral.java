package cadenza.values;

import cadenza.Neutral;
import cadenza.types.Type;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary.class)
public class VNeutral implements TruffleObject {
  public final Type type;
  public final Neutral term;
  VNeutral(Type type, Neutral term) {
    this.type = type;
    this.term = term;
  }

  // other people can execute this, but it just builds a bigger and bigger NApp
  @ExportMessage
  boolean isExecutable() { return true; }

  @ExportMessage
  public VNeutral execute(Object... arguments) {
    Type resultType = type;
    for (int i=0;i<arguments.length;++i)
      resultType = ((Type.Arr)resultType).result;
    return new VNeutral( resultType, term.apply(arguments));
  }
}
