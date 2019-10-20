package cadenza.nodes;

import cadenza.Language;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExecutableNode;

// to support this, frameslots should be created such that they carry the Type as the extra field!
// then we can reconstitute type information for variable watches.
public class InlineCode extends ExecutableNode {
  @SuppressWarnings("CanBeFinal")
  @Child public Code body;

  protected InlineCode(Language language, Code body) {
    super(language);
    this.body = body;
  }

  @Override
  public Object execute(VirtualFrame frame) {
    return body.executeAny(frame);
  }

  public static InlineCode create(Language language, Code body) {
    return new InlineCode(language, body);
  }
}
