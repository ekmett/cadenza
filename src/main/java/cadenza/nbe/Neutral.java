package cadenza.nbe;

import cadenza.nodes.Builtin;
import cadenza.util.Arrays;
import com.oracle.truffle.api.CompilerDirectives;

// neutral terms allow for normalization-by-evaluation

// neutral terms are values that are are 'stuck' carried by normalization by evaluation
// eventually these will carry types to help guide the walk, return arity, etc.

// for now these are strict, but if large neutral terms become a problem we _could_ render them lazy
// by materializing frames at the call sites and putting thunks that use materialized frames here instead
// for lazier alpha equivalence checking
@CompilerDirectives.ValueType // screw your reference equality
public abstract class Neutral {
  // other people can execute this, but it just builds a bigger and bigger NApp
  public NApp apply(Object... arguments) {
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

    public final NApp apply(Object... arguments) {
      return napp(rator, Arrays.add(rands,arguments));
    }
  }

  public static NApp napp(Neutral rator, Object... rands) {
    return new NApp(rator, rands);
  }

  public static class NIf extends Neutral {
    public final Neutral body;
    public final Object thenValue, elseValue; // lazy values?
    NIf(Neutral body, Object thenValue, Object elseValue) {
      this.body = body;
      this.thenValue = thenValue;
      this.elseValue = elseValue;
    }
  }

  public static NIf nif(final Neutral condition, final Object thenValue, final Object elseValue) {
    return new NIf(condition, thenValue, elseValue);
  }

  public static class NCallBuiltin extends Neutral {
    public final Builtin builtin;
    public final Neutral arg;
    NCallBuiltin(Builtin builtin, Neutral arg) {
      this.builtin = builtin;
      this.arg = arg;
    }
  }

  // stuck call to builtin
  public static Neutral ncallbuiltin(final Builtin builtin, final Neutral arg) {
    return new NCallBuiltin(builtin, arg);
  }

  // we'll wind up with an extra neutral form for natural numbers modeling peano arithmetic:
  // NAdd BigInteger Neutral
}
