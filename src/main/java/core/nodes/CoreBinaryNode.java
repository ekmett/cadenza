package core.nodes;

import com.oracle.truffle.api.dsl.NodeChild;

@NodeChild("left")
@NodeChild("right")
public abstract class CoreBinaryNode extends CoreExpressionNode {}
