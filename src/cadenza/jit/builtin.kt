package cadenza.jit

import cadenza.Language
import cadenza.data.*
import cadenza.semantics.ConsEnv
import cadenza.semantics.Ctx
import cadenza.semantics.NameInfo
import cadenza.semantics.Type
import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.dsl.*
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.*
import java.io.Serializable

@TypeSystemReference(DataTypes::class)
@ReportPolymorphism
abstract class Builtin(@Suppress("unused") open val type: Type, val arity: Int) : Node(), Serializable {
  @Throws(NeutralException::class)
  abstract fun run(frame: VirtualFrame, args: Array<Any?>): Any?
  @Throws(NeutralException::class)
  open fun runUnit(frame: VirtualFrame, args: Array<Any?>) { run(frame, args) }
  @Throws(NeutralException::class)
  open fun runClosure(frame: VirtualFrame, args: Array<Any?>): Closure = DataTypesGen.expectClosure(run(frame, args))
  @Throws(NeutralException::class)
  open fun runBoolean(frame: VirtualFrame, args: Array<Any?>): Boolean = DataTypesGen.expectBoolean(run(frame, args))
  @Throws(NeutralException::class)
  open fun runInteger(frame: VirtualFrame, args: Array<Any?>): Int = DataTypesGen.expectInteger(run(frame, args))
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
  override fun run(frame: VirtualFrame, args: Array<Any?>): Any? { return execute(args[0], args[1]) }
  @Throws(NeutralException::class)
  override fun runUnit(frame: VirtualFrame, args: Array<Any?>) { execute(args[0], args[1]) }
  @Throws(NeutralException::class)
  override fun runBoolean(frame: VirtualFrame, args: Array<Any?>): Boolean { return executeBoolean(args[0], args[1]) }
  @Throws(NeutralException::class)
  override fun runClosure(frame: VirtualFrame, args: Array<Any?>): Closure { return executeClosure(args[0], args[1]) }
  @Throws(NeutralException::class)
  override fun runInteger(frame: VirtualFrame, args: Array<Any?>): Int { return executeInteger(args[0], args[1]) }
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
  override fun run(frame: VirtualFrame, args: Array<Any?>): Any? { return execute(args[0]) }
  @Throws(NeutralException::class)
  override fun runUnit(frame: VirtualFrame, args: Array<Any?>) { execute(args[0]) }
  @Throws(NeutralException::class)
  override fun runBoolean(frame: VirtualFrame, args: Array<Any?>): Boolean { return executeBoolean(args[0]) }
  @Throws(NeutralException::class)
  override fun runClosure(frame: VirtualFrame, args: Array<Any?>): Closure { return executeClosure(args[0]) }
  @Throws(NeutralException::class)
  override fun runInteger(frame: VirtualFrame, args: Array<Any?>): Int { return executeInteger(args[0]) }
}

abstract class Le : Builtin2(Type.Arr(Type.Nat,Type.Arr(Type.Nat, Type.Bool))) {
  @Specialization
  internal fun leInt(left: Int, right: Int): Boolean = left <= right
}

abstract class Eq : Builtin2(Type.Arr(Type.Nat,Type.Arr(Type.Nat, Type.Bool))) {
  @Specialization
  internal fun eqInt(left: Int, right: Int): Boolean = left == right
}


abstract class Plus : Builtin2(Type.Arr(Type.Nat,Type.Arr(Type.Nat, Type.Nat))) {
  @Specialization(rewriteOn = [ArithmeticException::class])
  internal fun addInt(left: Int, right: Int): Int = Math.addExact(left, right)
  @Specialization
  internal fun addBigInt(left: BigInt, right: BigInt): BigInt = BigInt(left.value.add(right.value))
}

abstract class Mod : Builtin2(Type.Arr(Type.Nat,Type.Arr(Type.Nat, Type.Nat))) {
  @Specialization
  internal fun modInt(x: Int, y: Int): Int = x.rem(y)
}

abstract class Div : Builtin2(Type.Arr(Type.Nat,Type.Arr(Type.Nat, Type.Nat))) {
  @Specialization
  internal fun divInt(x: Int, y: Int): Int = x.div(y)
}

abstract class Mult : Builtin2(Type.Arr(Type.Nat,Type.Arr(Type.Nat, Type.Nat))) {
  @Specialization
  internal fun multInt(x: Int, y: Int): Int = x * y
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

// fixNatF f x = f (fixNatF f) x. Cache values, never nodes owned by another root.
abstract class FixNatF : Builtin(Type.Arr(natFF, natF), 2) {
  @CompilerDirectives.CompilationFinal private var fixedTarget: RootCallTarget? = null
  abstract fun execute(frame: VirtualFrame, l: Any?, r: Any?): Any?
  override fun run(frame: VirtualFrame, args: Array<Any?>) = execute(frame, args[0], args[1])

  @Specialization(guards = ["f.equals(cachedF)"], limit = "3")
  fun cached(frame: VirtualFrame, f: Closure, right: Any?,
             @Cached("f") cachedF: Closure,
             @Cached(value = "mkFix(cachedF)", neverDefault = true) fix: Closure,
             @Cached.Exclusive @Cached(value = "createDispatch()", neverDefault = true) dispatch: Dispatch): Any? =
    dispatch.executeDispatch(frame, fix, arrayOf(right))

  @Specialization(replaces = ["cached"])
  fun generic(frame: VirtualFrame, f: Closure, right: Any?,
              @Cached.Exclusive @Cached(value = "createDispatch()", neverDefault = true) dispatch: Dispatch): Any? =
    dispatch.executeDispatch(frame, mkFix(f), arrayOf(right))

  fun createDispatch(): Dispatch = DispatchNodeGen.create(1, false)

  fun mkFix(f: Closure): Closure {
    if (fixedTarget == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate()
      fixedTarget = FixApplyRootNode(Language.currentLanguage(this)).callTarget
    }
    return FixedFunction(f, fixedTarget!!).self
  }
}

/**
 * The knot is tied once, before this value becomes visible outside its constructor.
 * Both fields are immutable; recursive calls reuse the same closure and capture array.
 */
private class FixedFunction(val function: Closure, target: RootCallTarget) {
  val self = Closure(null, arrayOf(this), 1, fixedFunctionType, target)

  // Match the structural equality of the former partial application of function.
  // In particular, never compare self: that would follow the recursive knot.
  override fun equals(other: Any?): Boolean = other is FixedFunction && function == other.function
  override fun hashCode(): Int = function.hashCode()

  companion object {
    private val fixedFunctionType = Type.Arr(Type.Obj, natF)
  }
}

/** The recursive function partially applies this shared body to its immutable knot. */
class FixApplyRootNode(language: Language) : CadenzaRootNode(language, FrameLayout().build()) {
  override val hasTailCallFrame = true
  @Child private var dispatch: Dispatch = DispatchNodeGen.create(2, true)
  override fun execute(frame: VirtualFrame): Any? {
    frame.setLong(FrameLayout.BLOOM_FILTER, (frame.arguments[0] as Long) or mask)
    val fixed = frame.arguments[1] as FixedFunction
    return dispatch.executeDispatch(frame, fixed.function, arrayOf(fixed.self, frame.arguments[2]))
  }
  override fun getName() = "fixNatF"
}


class PrintId : Builtin1(natF) {
  @CompilerDirectives.TruffleBoundary
  override fun execute(x: Any?): Any? {
    val output = Language.currentContext(this).env.out()
    output.write("$x\n".toByteArray(Charsets.UTF_8))
    output.flush()
    return x
  }
}

val initialCtx: Ctx = arrayOf<Pair<String, () -> Builtin>>(
  "le" to { LeNodeGen.create() },
  "fixNatF" to { FixNatFNodeGen.create() },
  "plus" to { PlusNodeGen.create() },
  "minus" to { MinusNodeGen.create() },
  "eq" to { EqNodeGen.create() },
  "mod" to { ModNodeGen.create() },
  "div" to { DivNodeGen.create() },
  "mult" to { MultNodeGen.create() },
  "printId" to { PrintId() }
).fold(null as Ctx) { ctx, (name, factory) ->
  ConsEnv(name, NameInfo(factory().type, factory), ctx)
}
