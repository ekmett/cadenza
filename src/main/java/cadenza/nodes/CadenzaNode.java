package cadenza.nodes;

import cadenza.types.Types;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.nodes.*;
import cadenza.Language;

// CoreNode is just an instrumentable node that is also a Node
// CoreNode.Simple is an instrumentable node that uses lazy source elaboration
@TypeSystemReference(Types.class)
public abstract class CadenzaNode extends Node implements InstrumentableNode {

  // implement source by allowing you to set a source section
  public abstract static class Simple extends CadenzaNode {
  }

  // used in response to inlineparsing requests
  // the only real applicable domain for these is to create literal watching
  // and eventually it'd be kinda cool to have them for antiquoters for some kind of template haskell thing
  // as this gives access to the current environment

  public static ProgramRootNode root(Language language, Code body, FrameDescriptor fd) {
    return new ProgramRootNode(language, body, fd);
  }
  public static ProgramRootNode root(Language language, Code body) {
    return new ProgramRootNode(language, body, new FrameDescriptor());
  }

}
