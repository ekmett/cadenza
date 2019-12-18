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
  abstract fun run(args: Array<Any?>): Any?
  @Throws(NeutralException::class)
  abstract fun runUnit(args: Array<Any?>)
  @Throws(NeutralException::class)
  abstract fun runClosure(args: Array<Any?>): Closure
  @Throws(NeutralException::class)
  abstract fun runBoolean(args: Array<Any?>): Boolean
  @Throws(NeutralException::class)
  abstract fun runInteger(args: Array<Any?>): Int
}


abstract class Builtin2(type: Type) : Builtin(type, 2) {
  @Throws(NeutralException::class)
  abstract fun execute(left: Any?, right: Any?): Any?
  @Throws(NeutralException::class)
  open fun executeBoolean(left: Any?, right: Any?): Boolean = DataTypesGen.expectBoolean(execute(left, right))
  @Throws(NeutralException::class)
  open fun executeClosure(left: Any?, right: Any?): Closure = DataTypesGen.expectClosure(execute(left, right))
  @Throws(NeutralException::class)
  open fun executeInteger(left: Any?, right: Any?): Int = DataTypesGen.expectInteger(execute(left, right))

  @Throws(NeutralException::class)
  override fun run(args: Array<Any?>): Any? { return execute(args[0], args[1]) }
  @Throws(NeutralException::class)
  override fun runUnit(args: Array<Any?>) { execute(args[0], args[1]) }
  @Throws(NeutralException::class)
  override fun runBoolean(args: Array<Any?>): Boolean { return executeBoolean(args[0], args[1]) }
  @Throws(NeutralException::class)
  override fun runClosure(args: Array<Any?>): Closure { return executeClosure(args[0], args[1]) }
  @Throws(NeutralException::class)
  override fun runInteger(args: Array<Any?>): Int { return executeInteger(args[0], args[1]) }
}


//
////@NodeInfo(shortName = "Print")
////object Print : Builtin(Type.Action, 1) {
////  @Throws(NeutralException::class)
////  override fun execute(args: Array<Any?>) = executeUnit(args)
////
////  @Throws(NeutralException::class)
////  override fun executeUnit(args: Array<Any?>) {
////    println(args[0])
////  }
////}

object Le : Builtin2(Type.Arr(Type.Nat,Type.Arr(Type.Nat, Type.Bool))) {
  override fun execute(left: Any?, right: Any?): Any? {
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
object FixNatF : Builtin2(Type.Arr(natFF, natF)) {
  @CompilerDirectives.TruffleBoundary
  override fun execute(left: Any?, right: Any?): Any? {
    val root = (this.rootNode as BuiltinRootNode).callTarget
    val f = expectClosure(left)
    val selfApp = Closure(null, arrayOf(f), 1, type, root)
    val x = expectInteger(right)
    return f.call(arrayOf(selfApp, x))
  }
}


val initialCtx: Ctx = arrayOf(
  Pair("le", Le),
  Pair("fixNatF", FixNatF),
  Pair("plus", PlusNodeGen.create()),
  Pair("minus", MinusNodeGen.create())
).fold(null as Ctx) { c, x ->
  ConsEnv(x.first, NameInfo(x.second.type, x.second), c)
}

