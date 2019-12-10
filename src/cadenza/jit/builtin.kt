package cadenza.jit

import cadenza.data.*
import cadenza.data.DataTypesGen.expectClosure
import cadenza.data.DataTypesGen.expectInteger
import cadenza.semantics.ConsEnv
import cadenza.semantics.Ctx
import cadenza.semantics.NameInfo
import cadenza.semantics.Type
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.dsl.TypeSystemReference
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.NodeInfo
import com.oracle.truffle.api.nodes.UnexpectedResultException
import java.io.Serializable

@TypeSystemReference(DataTypes::class)
abstract class Builtin(@Suppress("unused") open val type: Type, val arity: Int) : Node(), Serializable {
  @Throws(NeutralException::class)
  abstract fun execute(args: Array<Any?>): Any?

  @Throws(NeutralException::class)
  open fun executeUnit(args: Array<Any?>) {
    execute(args)
  }

  @Throws(UnexpectedResultException::class, NeutralException::class)
  @Suppress("unused")
  open fun executeClosure(args: Array<Any?>): Closure =
    DataTypesGen.expectClosure(execute(args))

  @Throws(UnexpectedResultException::class, NeutralException::class)
  open fun executeBoolean(args: Array<Any?>): Boolean =
    DataTypesGen.expectBoolean(execute(args))

  @Throws(UnexpectedResultException::class, NeutralException::class)
  open fun executeInteger(args: Array<Any?>): Int =
    DataTypesGen.expectInteger(execute(args))
}

@NodeInfo(shortName = "Print")
object Print : Builtin(Type.Action, 1) {
  @Throws(NeutralException::class)
  override fun execute(args: Array<Any?>) = executeUnit(args)

  @Throws(NeutralException::class)
  override fun executeUnit(args: Array<Any?>) {
    println(args[0])
  }
}

// TODO: remove this
// name the execute in Builtin something else to be able to remove this
// (@Specialization looks for things named execute* & wants them to all have the same shape)
class Builtin2B(val builtin2: Builtin2) : Builtin(builtin2.type, 2) {
  override fun execute(args: Array<Any?>): Any {
    return builtin2.execute(args[0], args[1])
  }
}

abstract class Builtin2(val type: Type) : Node() {
  abstract fun execute(left: Any?, right: Any?): Any
}

object Le : Builtin2(Type.Arr(Type.Nat,Type.Arr(Type.Nat, Type.Bool))) {
  override fun execute(left: Any?, right: Any?): Boolean {
    return expectInteger(left) <= expectInteger(right)
  }
}

abstract class Plus : Builtin2(Type.Arr(Type.Nat,Type.Arr(Type.Nat, Type.Nat))) {
  @Specialization(rewriteOn = [ArithmeticException::class])
  internal fun addInt(left: Int, right: Int): Int {
    return Math.addExact(left, right)
  }

  @Specialization
  internal fun addBigInt(left: BigInt, right: BigInt): BigInt {
    return BigInt(left.value.add(right.value))
  }
}

// TODO: check result positive
abstract class Minus : Builtin2(Type.Arr(Type.Nat,Type.Arr(Type.Nat, Type.Nat))) {
  @Specialization(rewriteOn = [ArithmeticException::class])
  internal fun subInt(left: Int, right: Int): Int {
    return Math.subtractExact(left, right)
  }

  @Specialization
  internal fun subBigInt(left: BigInt, right: BigInt): BigInt {
    return BigInt(left.value.subtract(right.value))
  }
}

val natF = Type.Arr(Type.Nat, Type.Nat)
val natFF = Type.Arr(natF, natF)

// fixNatF f x = f (fixNatF f) x
object FixNatF : Builtin(Type.Arr(natFF, natF), 2) {
  @CompilerDirectives.TruffleBoundary
  override fun execute(args: Array<Any?>): Any? {
    val root = (this.rootNode as BuiltinRootNode).callTarget
    val f = expectClosure(args[0])
    val selfApp = Closure(null, arrayOf(f), 1, type, root)
    val x = expectInteger(args[1])
    return f.call(arrayOf(selfApp, x))
  }
}


val initialCtx: Ctx = arrayOf(
  Pair("le", Builtin2B(Le)),
  Pair("fixNatF", FixNatF),
  Pair("plus", Builtin2B(PlusNodeGen.create())),
  Pair("minus", Builtin2B(MinusNodeGen.create()))
).fold(null as Ctx) { c, x ->
  ConsEnv(x.first, NameInfo(x.second.type, x.second), c)
}

