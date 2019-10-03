package core;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.nodes.ControlFlowException;

public class TailCallException extends ControlFlowException {
    private static final long serialVersionUID = 4794670470976114363L;
    public final CallTarget callTarget;
    public final Object[] arguments;
    public TailCallException(CallTarget callTarget, Object[] arguments) {
        this.callTarget = callTarget;
        this.arguments = arguments;
    }
}