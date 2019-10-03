package core.nodes;

import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.NodeInfo;

@GenerateWrapper
@ReportPolymorphism
@NodeInfo(language = "core", description = "The abstract base node for all STG statements")
public abstract class CoreStatementNode extends CoreNode {
  public boolean hasTag(Class<? extends Tag> tag) {
    if (tag == StandardTags.StatementTag.class) return true;
    return super.hasTag(tag);
  }

  public WrapperNode createWrapper(ProbeNode probe) {
    return new CoreStatementNodeWrapper(this, probe);
  }
}

