package core.nodes;

import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

//@GenerateWrapper
//@ReportPolymorphism
@NodeInfo(language = "core", description = "Instrumentable STG nodes")
public abstract class CoreNode extends Node {
//public InstrumentableNode.WrapperNode createWrapper(ProbeNode probe) {
//    return new CoreNodeWrapper(this, probe);
//  }

  public abstract Object execute(VirtualFrame frame);

  public int executeInteger(VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectInteger(execute(frame));
  }
  public boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectBoolean(execute(frame));
  }
  public long executeLong(VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectLong(execute(frame));
  }
}
