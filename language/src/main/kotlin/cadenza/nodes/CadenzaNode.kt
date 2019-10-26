package cadenza.nodes

import cadenza.types.Types
import com.oracle.truffle.api.dsl.TypeSystemReference
import com.oracle.truffle.api.instrumentation.InstrumentableNode
import com.oracle.truffle.api.nodes.*

@TypeSystemReference(Types::class)
abstract class CadenzaNode : Node(), InstrumentableNode;