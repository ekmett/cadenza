package cadenza.jit

import cadenza.Type
import cadenza.Types
import cadenza.TypesGen
import cadenza.data.Closure
import cadenza.data.NeutralException
import com.oracle.truffle.api.dsl.TypeSystemReference
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.*

import java.io.Serializable

@TypeSystemReference(Types::class)
abstract class Builtin(@Suppress("unused") val resultType: Type) : Node(), Serializable {
  @Throws(NeutralException::class)
  abstract fun execute(frame: VirtualFrame, arg: Code): Any?

  @Throws(NeutralException::class)
  open fun executeUnit(frame: VirtualFrame, arg: Code) {
    execute(frame, arg)
  }

  @Throws(UnexpectedResultException::class, NeutralException::class)
  @Suppress("unused")
  open fun executeClosure(frame: VirtualFrame, arg: Code): Closure =
    TypesGen.expectClosure(execute(frame, arg))

  @Throws(UnexpectedResultException::class, NeutralException::class)
  open fun executeBoolean(frame: VirtualFrame, arg: Code): Boolean =
    TypesGen.expectBoolean(execute(frame, arg))

  @Throws(UnexpectedResultException::class, NeutralException::class)
  open fun executeInteger(frame: VirtualFrame, arg: Code): Int =
    TypesGen.expectInteger(execute(frame, arg))
}

@NodeInfo(shortName = "Print")
object Print : Builtin(Type.Action) {
  @Throws(NeutralException::class)
  override fun execute(frame: VirtualFrame, arg: Code) = executeUnit(frame, arg)

  @Throws(NeutralException::class)
  override fun executeUnit(frame: VirtualFrame, arg: Code) {
    println(arg.execute(frame))
  }
}
