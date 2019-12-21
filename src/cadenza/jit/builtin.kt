package cadenza.jit

import cadenza.Language
import cadenza.data.*
import cadenza.semantics.ConsEnv
import cadenza.semantics.Ctx
import cadenza.semantics.NameInfo
import cadenza.semantics.Type
import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.dsl.TypeSystemReference
import com.oracle.truffle.api.nodes.*
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

abstract class Builtin1(type: Type) : Builtin(type, 1) {
  @Throws(NeutralException::class)
  abstract fun execute(x: Any?): Any?
  @Throws(NeutralException::class)
  open fun executeBoolean(x: Any?): Boolean = DataTypesGen.expectBoolean(execute(x))
  @Throws(NeutralException::class)
  open fun executeClosure(x: Any?): Closure = DataTypesGen.expectClosure(execute(x))
  @Throws(NeutralException::class)
  open fun executeInteger(x: Any?): Int = DataTypesGen.expectInteger(execute(x))

  @Throws(NeutralException::class)
  override fun run(args: Array<Any?>): Any? { return execute(args[0]) }
  @Throws(NeutralException::class)
  override fun runUnit(args: Array<Any?>) { execute(args[0]) }
  @Throws(NeutralException::class)
  override fun runBoolean(args: Array<Any?>): Boolean { return executeBoolean(args[0]) }
  @Throws(NeutralException::class)
  override fun runClosure(args: Array<Any?>): Closure { return executeClosure(args[0]) }
  @Throws(NeutralException::class)
  override fun runInteger(args: Array<Any?>): Int { return executeInteger(args[0]) }
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

abstract class Le : Builtin2(Type.Arr(Type.Nat,Type.Arr(Type.Nat, Type.Bool))) {
  @Specialization
  internal fun leInt(left: Int, right: Int): Boolean {
    return left <= right
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
  // TODO: cache f => FixNatF1?
  override fun execute(left: Any?, right: Any?): Any? {
    val language = this.rootNode.getLanguage(Language::class.java)
    val it = FixNatF1(DataTypesGen.expectClosure(left), language)
    return it.execute(right)
  }
}

// fixNatF with a known function
// inlines up to graal.TruffleMaximumRecursiveInlining
class FixNatF1(private val f: Closure, language: Language) : Builtin1(natF) {
  @Child var callNode: DirectCallNode = DirectCallNode.create(f.callTarget)
  private val target: RootCallTarget = Truffle.getRuntime().createCallTarget(BuiltinRootNode(language, this))
  private val self = Closure(null, arrayOf(), 1, type, target)

  override fun execute(x: Any?):  Any? {
    return f.callDirectWith(callNode, arrayOf(self, x))
  }
}


val initialCtx: Ctx = arrayOf(
  Pair("le", LeNodeGen.create()),
  Pair("fixNatF", FixNatF),
  Pair("plus", PlusNodeGen.create()),
  Pair("minus", MinusNodeGen.create())
).fold(null as Ctx) { c, x ->
  ConsEnv(x.first, NameInfo(x.second.type, x.second), c)
}

