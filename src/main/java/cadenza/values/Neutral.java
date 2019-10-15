package cadenza.values;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

// neutral terms allow for normalization-by-evaluation

// neutral terms are values that are are 'stuck' carried by normalization by evaluation
// eventually these will carry types to help guide the walk, return arity, etc.

// for now these are strict, but if large neutral terms become a problem we _could_ render them lazy
// by materializing frames at the call sites and putting thunks that use materialized frames here instead
// for lazier alpha equivalence checking
@CompilerDirectives.ValueType // screw your reference equality
@ExportLibrary(InteropLibrary.class)
public class Neutral implements TruffleObject {
  // other people can execute this, but it just builds a bigger and bigger NApp
  @ExportMessage
  boolean isExecutable() { return true; }

  @ExportMessage
  NApp execute(Object... arguments) {
    return new NApp(this, arguments);
  }

  // stuck application
  public static final class NApp extends Neutral {
    public final Neutral rator;
    public final Object[] rands; // TODO: can we give this a shape and put each entry into a new slot using a DynamicObject with Layout?

    NApp(Neutral rator, Object... rands) {
      this.rator = rator;
      this.rands = rands;
    }

    final NApp execute(Object... arguments) {
      return new NApp(rator, add(rands,arguments));
    }

    private static Object[] add(final Object[] xs, final Object... ys) {
      Object[] zs = new Object[xs.length + ys.length];
      System.arraycopy(xs, 0, zs, 0, xs.length);
      System.arraycopy(ys, 0, zs,  xs.length, ys.length);
      return zs;
    }
  }

  public static class NIf extends Neutral {
    public final Neutral condition;
    public final Object thenValue, elseValue;
    public NIf(final Neutral condition, final Object thenValue, final Object elseValue) {
      this.condition = condition;
      this.thenValue = thenValue;
      this.elseValue = elseValue;
    }
    //public abstract Object getThenValue();
    //public abstract Object getElseValue();
    //public NIf(final Neutral condition) {
    //  this.condition = condition;
    //}
  }
  public static NIf nif(final Neutral condition, final Object thenValue, final Object elseValue) {
    return new NIf(condition, thenValue, elseValue);
  }
}
