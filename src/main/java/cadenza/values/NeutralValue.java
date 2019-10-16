package cadenza.values;

import cadenza.nbe.Neutral;
import cadenza.types.Type;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary.class)
public class NeutralValue implements TruffleObject {
  public final Type type;
  public final Neutral term;
  public NeutralValue(Type type, Neutral term) {
    this.type = type;
    this.term = term;
  }

  // other languages can execute this, but it just builds a bigger and bigger NApp
  @ExportMessage
  boolean isExecutable() { return true; }

  @ExportMessage
  public NeutralValue execute(Object... arguments) throws ArityException {
    Type resultType = type;
    // give arity exception on overflow
    for (int i=0;i<arguments.length;++i) {
      Type.Arr arr = (Type.Arr)resultType;
      if (arr == null) throw ArityException.create(i, arguments.length); // we over-applied, report by how much for FFI
      resultType = arr.result;
    }

    return new NeutralValue( resultType, term.apply(arguments));
  }

  // assumes this has been built legally. fails via unchecked null pointer exception
  public NeutralValue apply(Object... arguments) {
    Type resultType = type;
    for (int i=0;i<arguments.length;++i)
      resultType = ((Type.Arr)resultType).result;
    return new NeutralValue( resultType, term.apply(arguments));
  }

}
