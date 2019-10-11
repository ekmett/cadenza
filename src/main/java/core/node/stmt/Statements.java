package core.node.stmt;

import com.oracle.truffle.api.frame.FrameSlot;
import core.node.expr.Expression;

public interface Statements {
   static BlockStatement block(Statement... nodes) { return new BlockStatement(nodes); }
   static DefStatement def(FrameSlot slot, Expression body) { return DefStatement.create(slot, body); }
   static PrintStatement def(Expression body) { return PrintStatementNodeGen.create(body); }
}
