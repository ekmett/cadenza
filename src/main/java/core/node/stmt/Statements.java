package core.node.stmt;

import com.oracle.truffle.api.frame.FrameSlot;
import core.node.expr.Expression;

public interface Statements {
   static Do block(Statement... nodes) { return new Do(nodes); }
   static Def def(FrameSlot slot, Expression body) { return Def.create(slot, body); }
   static Print def(Expression body) { return PrintNodeGen.create(body); }
}
