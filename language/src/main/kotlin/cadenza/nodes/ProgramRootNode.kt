package cadenza.nodes

import cadenza.Language
import cadenza.types.Types
import com.oracle.truffle.api.dsl.TypeSystemReference
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.NodeInfo
import com.oracle.truffle.api.nodes.RootNode

// root nodes are needed by Truffle.getRuntime().createCallTarget(someRoot), which is the way to manufacture callable
// things in truffle.
@NodeInfo(language = "core", description = "A root of a core tree.")
@TypeSystemReference(Types::class)
class ProgramRootNode constructor(
  val language: Language,
  @field:Child private var body: Code,
  fd: FrameDescriptor
) : RootNode(language, fd) {

  // eventually disallow selectively when we have the equivalent of NOINLINE / top level implicitly constructed references?
  override fun isCloningAllowed(): Boolean {
    return true
  }

  // returns neutral terms
  override fun execute(frame: VirtualFrame): Any? {
    return body.executeAny(frame)
  }
}
