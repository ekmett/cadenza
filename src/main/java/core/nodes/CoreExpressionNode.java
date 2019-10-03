package core.nodes;

import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.NodeInfo;

@GenerateWrapper
@NodeInfo(language = "core", description = "The abstract base node for all core expressions")
public abstract class CoreExpressionNode extends CoreNode {
  public boolean hasTag(Class<? extends Tag> tag) {
    return tag == StandardTags.ExpressionTag.class;
  }

  public WrapperNode createWrapper(ProbeNode probe) {
    return new CoreExpressionNodeWrapper(this, probe);
  }
}
