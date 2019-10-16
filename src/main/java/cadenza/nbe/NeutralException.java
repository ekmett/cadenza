package cadenza.nbe;

import cadenza.types.Type;
import cadenza.values.NeutralValue;
import com.oracle.truffle.api.nodes.SlowPathException;

// this is used to suck the program through a straw using normalization-by-evaluation
// to produce a beta-eta-long normal form versions of the resulting program that remains

public class NeutralException extends SlowPathException {
  private static final long serialVersionUID = 5587798688299594259L;
  public final Type type;
  public final Neutral term;
  public NeutralValue get() { return new NeutralValue(type, term); }
  public NeutralException(Type type, Neutral term) { this.type = type; this.term = term; }
  public NeutralException apply(final Object... rands) {
    int len = rands.length;
    Type currentType = type;
    for (int i=0;i<len;++i) currentType = ((Type.Arr)currentType).result;
    return new NeutralException(currentType, term.apply(rands));
  }
}
