package cadenza.nodes

import cadenza.NeutralException
import cadenza.types.Type
import cadenza.types.Types
import cadenza.types.TypesGen
import cadenza.values.Closure
import com.oracle.truffle.api.dsl.TypeSystemReference
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.NodeInfo
import com.oracle.truffle.api.nodes.UnexpectedResultException

import java.io.Serializable

@TypeSystemReference(Types::class)
abstract class Builtin(val resultType: Type) : Node(), Serializable {

  @Throws(NeutralException::class)
  abstract fun execute(frame: VirtualFrame, arg: Code): Any?

  @Throws(NeutralException::class)
  open fun executeUnit(frame: VirtualFrame, arg: Code): Unit {
    execute(frame, arg)
  }

  @Throws(UnexpectedResultException::class, NeutralException::class)
  fun executeClosure(frame: VirtualFrame, arg: Code): Closure {
    return TypesGen.expectClosure(execute(frame, arg))
  }

  @Throws(UnexpectedResultException::class, NeutralException::class)
  fun executeBoolean(frame: VirtualFrame, arg: Code): Boolean {
    return TypesGen.expectBoolean(execute(frame, arg))
  }

  @Throws(UnexpectedResultException::class, NeutralException::class)
  fun executeInteger(frame: VirtualFrame, arg: Code): Int {
    return TypesGen.expectInteger(execute(frame, arg))
  }

  @NodeInfo(shortName = "print$")
  internal class Print : Builtin(Type.Action) {
    @Throws(NeutralException::class)
    override fun execute(frame: VirtualFrame, arg: Code): Any? {
      executeUnit(frame, arg)
      return Unit
    }

    @Throws(NeutralException::class)
    override fun executeUnit(frame: VirtualFrame, arg: Code): Unit {
      println(arg.execute(frame))
    }
  }

  companion object {
    var print: Builtin = Print()
  }
}
