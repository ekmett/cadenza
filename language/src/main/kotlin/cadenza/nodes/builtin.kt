package cadenza.nodes

import cadenza.types.NeutralException
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
  open fun executeUnit(frame: VirtualFrame, arg: Code) {
    execute(frame, arg)
  }

  @Throws(UnexpectedResultException::class, NeutralException::class)
  fun executeClosure(frame: VirtualFrame, arg: Code): Closure =
    TypesGen.expectClosure(execute(frame, arg))

  @Throws(UnexpectedResultException::class, NeutralException::class)
  fun executeBoolean(frame: VirtualFrame, arg: Code): Boolean =
    TypesGen.expectBoolean(execute(frame, arg))

  @Throws(UnexpectedResultException::class, NeutralException::class)
  fun executeInteger(frame: VirtualFrame, arg: Code): Int =
    TypesGen.expectInteger(execute(frame, arg))
}

@NodeInfo(shortName = "print$")
object Print : Builtin(Type.Action) {
  @Throws(NeutralException::class)
  override fun execute(frame: VirtualFrame, arg: Code) = executeUnit(frame, arg)

  @Throws(NeutralException::class)
  override fun executeUnit(frame: VirtualFrame, arg: Code) {
    println(arg.execute(frame))
  }
}
