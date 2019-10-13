package core.node.stmt;

import com.oracle.truffle.api.frame.VirtualFrame;

public class Do extends Statement {
  @SuppressWarnings("CanBeFinal")
  @Children Statement[] body;
  Do(Statement[] body) { this.body = body; }

  @Override
  void execute(VirtualFrame frame) {
    for (Statement stmt : body) stmt.execute(frame);
  }
}
