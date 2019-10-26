package cadenza.nodes

import cadenza.*
import cadenza.types.Neutral.*
import cadenza.types.*
import cadenza.values.*
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.dsl.TypeSystemReference
import com.oracle.truffle.api.frame.*
import com.oracle.truffle.api.instrumentation.*
import com.oracle.truffle.api.interop.UnsupportedMessageException
import com.oracle.truffle.api.nodes.*
import com.oracle.truffle.api.profiles.ConditionProfile
import com.oracle.truffle.api.source.SourceSection
import java.util.Arrays

@Suppress("NOTHING_TO_INLINE")
@GenerateWrapper
@NodeInfo(language = "core", description = "core nodes")
@TypeSystemReference(Types::class)
abstract class Code : Node(), InstrumentableNode {
  private var sourceCharIndex = NO_SOURCE
  private var sourceLength = 0

  @Throws(NeutralException::class)
  abstract fun execute(frame: VirtualFrame): Any?

  open fun executeAny(frame: VirtualFrame): Any? {
    try {
      return execute(frame)
    } catch (e: NeutralException) {
      return e.get()
    }
  }

  @Throws(UnexpectedResultException::class, NeutralException::class)
  open fun executeClosure(frame: VirtualFrame): Closure = TypesGen.expectClosure(execute(frame))

  @Throws(UnexpectedResultException::class, NeutralException::class)
  open fun executeInteger(frame: VirtualFrame): Int = TypesGen.expectInteger(execute(frame))

  @Throws(UnexpectedResultException::class, NeutralException::class)
  open fun executeBoolean(frame: VirtualFrame): Boolean = TypesGen.expectBoolean(execute(frame))

  @Throws(NeutralException::class)
  open fun executeUnit(frame: VirtualFrame): Unit {
    execute(frame); return
  }

  // invoked by the parser to set the source
  fun setSourceSection(charIndex: Int, length: Int) {
    assert(sourceCharIndex == NO_SOURCE) { "source must only be set once" }
    if (charIndex < 0) throw IllegalArgumentException("charIndex < 0")
    if (length < 0) throw IllegalArgumentException("length < 0")
    sourceCharIndex = charIndex
    sourceLength = length
  }

  fun setUnavailableSourceSection() {
    assert(sourceCharIndex == NO_SOURCE) { "source must only be set once" }
    sourceCharIndex = UNAVAILABLE_SOURCE
  }

  override fun getSourceSection(): SourceSection? {
    if (sourceCharIndex == NO_SOURCE) return null
    val rootNode = rootNode ?: return null
    val rootSourceSection = rootNode.sourceSection ?: return null
    val source = rootSourceSection.source
    return if (sourceCharIndex == UNAVAILABLE_SOURCE)
      source.createUnavailableSection()
    else
      source.createSection(sourceCharIndex, sourceLength)
  }

  override fun isInstrumentable() = sourceCharIndex != NO_SOURCE
  override fun hasTag(tag: Class<out Tag>?) = tag == StandardTags.ExpressionTag::class.java
  override fun createWrapper(probe: ProbeNode): InstrumentableNode.WrapperNode = CodeWrapper(this, probe)
}

@TypeSystemReference(Types::class)
@NodeInfo(shortName = "App")
class App(
  @field:Child var rator: Code,
  @field:Children val rands: Array<Code>
) : Code() {
  @Child
  protected var indirectCallNode: IndirectCallNode

  init {
    this.indirectCallNode = Truffle.getRuntime().createIndirectCallNode()
  }

  @ExplodeLoop
  private fun executeRands(frame: VirtualFrame): Array<Any?> {
    return rands.map { it.executeAny(frame) }.toTypedArray()
  }

  @Throws(NeutralException::class)
  override fun execute(frame: VirtualFrame): Any? {
    val fn: Closure
    try {
      fn = rator.executeClosure(frame)
    } catch (e: UnexpectedResultException) {
      panic("closure expected", e)
    } catch (e: NeutralException) {
      e.apply(executeRands(frame))
    }

    if (fn.arity == rands.size) {
      return indirectCallNode.call(fn.callTarget, *executeRands(frame))
    } else if (fn.arity > rands.size) {
      return fn.pap(executeRands(frame)) // not enough arguments, pap node
    } else {
      CompilerDirectives.transferToInterpreterAndInvalidate()
      this.replace(App(App(rator, Arrays.copyOf<Code>(rands, fn.arity)),
        Arrays.copyOfRange(rands, fn.arity, rands.size)))
      return fn.call(executeRands(frame)) // on slow path handling over-application
    }
  }

  override fun hasTag(tag: Class<out Tag>?) =
    (tag == StandardTags.CallTag::class.java) || super.hasTag(tag)
}

// once a variable binding has been inferred to refer to the local arguments of the current frame and mapped to an actual arg index
// this node replaces the original node.
// used during frame materialization to access numbered arguments. otherwise not available
@TypeSystemReference(Types::class)
@NodeInfo(shortName = "Arg")
class Arg(private val index: Int) : Code() {
  init { assert(0 <= index) { "negative index" } }

  override fun execute(frame: VirtualFrame): Any {
    val arguments = frame.arguments
    assert(index < arguments.size) { "insufficient arguments" }
    return arguments[index]
  }

  override fun isAdoptable() = false
}

class If(
  val type: Type,
  @field:Child private var bodyNode: Code,
  @field:Child private var thenNode: Code,
  @field:Child private var elseNode: Code
) : Code() {
  private val conditionProfile = ConditionProfile.createBinaryProfile()

  @Throws(NeutralException::class)
  private fun branch(frame: VirtualFrame): Boolean =
    try {
      conditionProfile.profile(bodyNode.executeBoolean(frame))
    } catch (e: UnexpectedResultException) {
      panic("non-boolean branch", e)
    } catch (e: NeutralException) {
      neutral(type, NIf(e.term, thenNode.executeAny(frame), elseNode.executeAny(frame)))
    }

  @Throws(NeutralException::class)
  override fun execute(frame: VirtualFrame): Any? =
    if (branch(frame)) thenNode.execute(frame)
    else elseNode.execute(frame)

  @Throws(UnexpectedResultException::class, NeutralException::class)
  override fun executeInteger(frame: VirtualFrame): Int =
    if (branch(frame)) thenNode.executeInteger(frame)
    else elseNode.executeInteger(frame)
}

// lambdas can be constructed from foreign calltargets, you just need to supply an arity
@Suppress("NOTHING_TO_INLINE")
@TypeSystemReference(Types::class)
@NodeInfo(shortName = "Lambda")
class Lam(
  val closureFrameDescriptor: FrameDescriptor?,
  @field:Children internal val captureSteps: Array<FrameBuilder>,
  private val arity: Int,
  @field:Child internal var callTarget: RootCallTarget,
  internal val type: Type
) : Code() {

  // do we need to capture an environment?
  inline fun isSuperCombinator() = closureFrameDescriptor != null

  override fun execute(frame: VirtualFrame) = Closure(captureEnv(frame), arity, type, callTarget)
  override fun executeClosure(frame: VirtualFrame): Closure = Closure(captureEnv(frame), arity, type, callTarget)

  @ExplodeLoop
  private fun captureEnv(frame: VirtualFrame): MaterializedFrame? {
    if (!isSuperCombinator()) return null
    val env = Truffle.getRuntime().createMaterializedFrame(arrayOf(), closureFrameDescriptor)
    for (captureStep in captureSteps) captureStep.build(env, frame)
    return env.materialize()
  }

  // root to render capture steps opaque
  override fun hasTag(tag: Class<out Tag>?) = tag == StandardTags.RootTag::class.java || tag == StandardTags.ExpressionTag::class.java
}

// utility
@Suppress("NOTHING_TO_INLINE")
inline fun isSuperCombinator(callTarget: RootCallTarget): Boolean {
  val root = callTarget.rootNode
  return root is ClosureRootNode && root.isSuperCombinator()
}

@NodeInfo(shortName = "Read")
abstract class Var protected constructor(protected val slot: FrameSlot) : Code() {

  @Specialization(rewriteOn = [FrameSlotTypeException::class])
  @Throws(FrameSlotTypeException::class)
  protected fun readLong(frame: VirtualFrame): Long = frame.getLong(slot)

  @Specialization(rewriteOn = [FrameSlotTypeException::class])
  @Throws(FrameSlotTypeException::class)
  protected fun readBoolean(frame: VirtualFrame): Boolean = frame.getBoolean(slot)

  @Specialization(replaces = ["readLong", "readBoolean"])
  protected fun read(frame: VirtualFrame): Any = frame.getValue(slot)

  override fun isAdoptable() = false
}

class Ann(@field:Child protected var body: Code, val type: Type) : Code() {
  @Throws(NeutralException::class)
  override fun execute(frame: VirtualFrame): Any? = body.execute(frame)
  override fun executeAny(frame: VirtualFrame): Any? = body.executeAny(frame)
  @Throws(UnexpectedResultException::class, NeutralException::class)
  override fun executeInteger(frame: VirtualFrame): Int = body.executeInteger(frame)
  @Throws(UnexpectedResultException::class, NeutralException::class)
  override fun executeBoolean(frame: VirtualFrame): Boolean = body.executeBoolean(frame)
  @Throws(UnexpectedResultException::class, NeutralException::class)
  override fun executeClosure(frame: VirtualFrame): Closure = body.executeClosure(frame)
}

// a fully saturated call to a builtin
// invariant: builtins themselves do not return neutral values, other than through evaluating their argument
abstract class CallBuiltin(val type: Type, val builtin: Builtin, @field:Child
internal var arg: Code) : Code() {
  override fun executeAny(frame: VirtualFrame): Any? =
    try {
      builtin.execute(frame, arg)
    } catch (n: NeutralException) {
      NeutralValue(type, NCallBuiltin(builtin, n.term))
    }

  @Throws(NeutralException::class)
  override fun execute(frame: VirtualFrame): Any? =
    try {
      builtin.execute(frame, arg)
    } catch (n: NeutralException) {
      neutral(type, NCallBuiltin(builtin, n.term))
    }

  @Throws(UnexpectedResultException::class, NeutralException::class)
  override fun executeInteger(frame: VirtualFrame): Int =
    try {
      builtin.executeInteger(frame, arg)
    } catch (n: NeutralException) {
      neutral(type, NCallBuiltin(builtin, n.term))
    }

  @Throws(NeutralException::class)
  override fun executeUnit(frame: VirtualFrame): Unit =
    try {
      builtin.executeUnit(frame, arg)
    } catch (n: NeutralException) {
      neutral(type, NCallBuiltin(builtin, n.term))
    }

  @Throws(UnexpectedResultException::class, NeutralException::class)
  override fun executeBoolean(frame: VirtualFrame): Boolean =
    try {
      builtin.executeBoolean(frame, arg)
    } catch (n: NeutralException) {
      neutral(type, NCallBuiltin(builtin, n.term))
    }
}

// instrumentation

const val NO_SOURCE = -1
const val UNAVAILABLE_SOURCE = -2

// invariant callTarget points to a native function body with known arity
@Suppress("NOTHING_TO_INLINE")
inline fun lam(callTarget: RootCallTarget, type: Type): Lam {
  val root = callTarget.rootNode
  assert(root is ClosureRootNode)
  return lam((root as ClosureRootNode).arity, callTarget, type)
}

// package a foreign root call target with known arity
@Suppress("NOTHING_TO_INLINE")
inline fun lam(arity: Int, callTarget: RootCallTarget, type: Type): Lam {
  return lam(null, noFrameBuilders, arity, callTarget, type)
}

@Suppress("NOTHING_TO_INLINE")
inline fun lam(closureFrameDescriptor: FrameDescriptor, captureSteps: Array<FrameBuilder>, callTarget: RootCallTarget, type: Type): Lam {
  val root = callTarget.rootNode
  assert(root is ClosureRootNode)
  return lam(closureFrameDescriptor, captureSteps, (root as ClosureRootNode).arity, callTarget, type)
}

// ensures that all the invariants for the constructor are satisfied
@Suppress("NOTHING_TO_INLINE")
inline fun lam(closureFrameDescriptor: FrameDescriptor?, captureSteps: Array<FrameBuilder>, arity: Int, callTarget: RootCallTarget, type: Type): Lam {
  assert(arity > 0)
  val hasCaptureSteps = captureSteps.size != 0
  assert(hasCaptureSteps == isSuperCombinator(callTarget)) { "mismatched calling convention" }
  return Lam(
    if (hasCaptureSteps) null else closureFrameDescriptor ?: FrameDescriptor(),
    captureSteps,
    arity,
    callTarget,
    type
  )
}

@Suppress("NOTHING_TO_INLINE")
inline fun `var`(slot: FrameSlot): Var = VarNodeGen.create(slot)


@Suppress("NOTHING_TO_INLINE")
inline fun booleanLiteral(b: Boolean): Code = object : Code() {
  @Suppress("UNUSED_PARAMETER")
  override fun execute(frame: VirtualFrame) = b
  @Suppress("UNUSED_PARAMETER")
  override fun executeBoolean(frame: VirtualFrame) = b
}

@Suppress("NOTHING_TO_INLINE")
inline fun intLiteral(i: Int): Code = object : Code() {
  @Suppress("UNUSED_PARAMETER")
  override fun execute(frame: VirtualFrame) = i
  @Suppress("UNUSED_PARAMETER")
  override fun executeInteger(frame: VirtualFrame) = i
}

@Suppress("NOTHING_TO_INLINE")
inline fun bigLiteral(i: BigInt): Code = object : Code() {
  @Suppress("UNUSED_PARAMETER")
  override fun execute(frame: VirtualFrame) = i
  @Throws(UnexpectedResultException::class)
  @Suppress("UNUSED_PARAMETER")
  override fun executeInteger(frame: VirtualFrame): Int =
    try {
      if (i.fitsInInt()) i.asInt().toInt()
      else throw UnexpectedResultException(i)
    } catch (e: UnsupportedMessageException) {
      panic("fitsInInt lied", e)
    }
}
