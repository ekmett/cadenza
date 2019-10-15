package cadenza;

import cadenza.values.Neutral;
import com.oracle.truffle.api.nodes.ControlFlowException;

public class NeutralException extends ControlFlowException {
  private static final long serialVersionUID = 5587798688299594259L;
  public final Neutral term;
  public NeutralException(Neutral term) { this.term = term; }
}
