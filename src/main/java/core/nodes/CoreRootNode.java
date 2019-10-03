package core.nodes;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import core.Language;

@NodeInfo(language = "core", description = "A root of a core tree.")
public class CoreRootNode extends RootNode {
  public CoreRootNode(
    Language language,
    FrameDescriptor frameDescriptor,
    CoreStatementNode body,
    SourceSection source,
    boolean cloningAllowed
  ) {
    super(language, frameDescriptor);
    this.body = body;
    this.sourceSection = sourceSection;
    this.cloningAllowed = cloningAllowed;
  }

  private CoreStatementNode body;
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
