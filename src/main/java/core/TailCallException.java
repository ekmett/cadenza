package core;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.nodes.ControlFlowException;
import core.values.Closure;

public class TailCallException extends ControlFlowException {
    private static final long serialVersionUID = 4794670470976114363L;
    public final Closure closure;
    public final Object[] arguments;
    public TailCallException(Closure closure, Object[] arguments) {
        this.closure = closure;
        this.arguments = arguments;
    }
}