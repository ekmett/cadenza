package core.node.stmt;

import com.oracle.truffle.api.frame.VirtualFrame;

public class BlockStatement extends Statement {
  @Children Statement[] body;
  BlockStatement(Statement[] body) { this.body = body; }

  @Override
  void execute(VirtualFrame frame) {
    for (Statement stmt : body) stmt.execute(frame);
  }
}
