package stg.nodes;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import stg.Language;

@NodeInfo(language = "stg", description = "A root of an STG tree.")
public class StgRootNode extends RootNode {
  public StgRootNode(Language language, FrameDescriptor frameDescriptor, StgStatementNode body, SourceSection source, boolean cloningAllowed) {
    super(language, frameDescriptor);
    this.body = body;
    this.sourceSection = sourceSection;
    this.cloningAllowed = cloningAllowed;
  }

  private StgStatementNode body;
  private SourceSection sourceSection;
  private boolean cloningAllowed;
  
  @Override
  public boolean isCloningAllowed() { return cloningAllowed; }

  public void setCloningAllowed(boolean cloningAllowed) {
    this.cloningAllowed = cloningAllowed;
  }

  @Override
  public Object execute(VirtualFrame frame) {
    assert(lookupContextReference(Language.class).get() != null);
    return body.execute(frame);
  }

  @Override
  public SourceSection getSourceSection() { 
    return sourceSection; 
  }
}
