package cadenza.nodes

import cadenza.types.Types
import com.oracle.truffle.api.dsl.TypeSystemReference
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.instrumentation.InstrumentableNode
import com.oracle.truffle.api.nodes.*
import cadenza.Language

// CoreNode is just an instrumentable node that is also a Node
// CoreNode.Simple is an instrumentable node that uses lazy source elaboration
@TypeSystemReference(Types::class)
abstract class CadenzaNode : Node(), InstrumentableNode {

    // implement source by allowing you to set a source section
    abstract class Simple : CadenzaNode()

    companion object {

        // used in response to inlineparsing requests
        // the only real applicable domain for these is to create literal watching
        // and eventually it'd be kinda cool to have them for antiquoters for some kind of template haskell thing
        // as this gives access to the current environment

        fun root(language: Language, body: Code, fd: FrameDescriptor): ProgramRootNode {
            return ProgramRootNode(language, body, fd)
        }

        fun root(language: Language, body: Code): ProgramRootNode {
            return ProgramRootNode(language, body, FrameDescriptor())
        }
    }

}
