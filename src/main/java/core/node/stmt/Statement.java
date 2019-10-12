package core.node.stmt;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

// A top level core statement, definitions, imports, exports, imperative print statements, whatever.
public abstract class Statement extends Node {
  abstract void execute(VirtualFrame frame);
  public boolean isAdoptable() { return true; }
}