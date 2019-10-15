package cadenza;

import cadenza.types.Type;
import cadenza.values.VNeutral;
import com.oracle.truffle.api.nodes.SlowPathException;

public class NeutralException extends SlowPathException {
  private static final long serialVersionUID = 5587798688299594259L;
  public final Type type;
  public final Neutral term;
  public VNeutral get() { return new VNeutral(type, term); }
  public NeutralException(Type type, Neutral term) { this.term = term; }
}
