package cadenza.nodes

import cadenza.Language
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExecutableNode

class InlineCode(language: Language, @field:Child var body: Code) : ExecutableNode(language) {
    override fun execute(frame: VirtualFrame): Any? {
        return body.executeAny(frame)
    }
}
