package core;

import com.oracle.truffle.api.nodes.ControlFlowException;
import core.values.Closure;

public class TailCallException extends ControlFlowException {
    private static final long serialVersionUID = 4749484936180230134L;
    public final Closure closure;
    public final Object[] arguments;
    public TailCallException(Closure closure, Object[] arguments) {
        this.closure = closure;
        this.arguments = arguments;
    }
}