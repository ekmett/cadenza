package cadenza.jit

import cadenza.Loc
import cadenza.data.*
import cadenza.panic
import cadenza.section
import cadenza.semantics.Type
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


// utility
@Suppress("NOTHING_TO_INLINE")
private inline fun isSuperCombinator(callTarget: RootCallTarget) =
  callTarget.rootNode.let { it is ClosureRootNode && it.isSuperCombinator() }

@Suppress("NOTHING_TO_INLINE","unused")
@GenerateWrapper
@NodeInfo(language = "core", description = "core nodes")
@TypeSystemReference(DataTypes::class)
abstract class Code(val loc: Loc? = null) : Node(), InstrumentableNode {

  @Throws(NeutralException::class)
  abstract fun execute(frame: VirtualFrame): Any?

  open fun executeAny(frame: VirtualFrame): Any? =
    try { execute(frame) } catch (e: NeutralException) { e.get() }

  @Throws(UnexpectedResultException::class, NeutralException::class)
  open fun executeClosure(frame: VirtualFrame): Closure = DataTypesGen.expectClosure(execute(frame))

  @Throws(UnexpectedResultException::class, NeutralException::class)
  open fun executeInteger(frame: VirtualFrame): Int = DataTypesGen.expectInteger(execute(frame))

  @Throws(UnexpectedResultException::class, NeutralException::class)
  open fun executeBoolean(frame: VirtualFrame): Boolean = DataTypesGen.expectBoolean(execute(frame))

  @Throws(NeutralException::class)
  open fun executeUnit(frame: VirtualFrame) { execute(frame) }

  override fun getSourceSection(): SourceSection? = loc?.let { rootNode?.sourceSection?.source?.section(it) }
  override fun isInstrumentable() = loc !== null

  override fun hasTag(tag: Class<out Tag>?) = tag == StandardTags.ExpressionTag::class.java
  override fun createWrapper(probe: ProbeNode): InstrumentableNode.WrapperNode = CodeWrapper(this, probe)

  @TypeSystemReference(DataTypes::class)
  @NodeInfo(shortName = "App")
  class App(
    @field:Child var rator: Code,
    @field:Children val rands: Array<Code>,
    loc: Loc? = null
  ) : Code(loc) {
    @Child private var indirectCallNode: IndirectCallNode = Truffle.getRuntime().createIndirectCallNode()

    @ExplodeLoop
    private fun executeRands(frame: VirtualFrame): Array<out Any?> = rands.map { it.executeAny(frame) }.toTypedArray()

    @Throws(NeutralException::class)
    override fun execute(frame: VirtualFrame): Any? {
      val fn = try {
        rator.executeClosure(frame)
      } catch (e: UnexpectedResultException) {
        panic("closure expected, got ${e.result}", e)
      } catch (e: NeutralException) {
        e.apply(executeRands(frame))
      }
      // TODO: support foreign CallTargets with nbe
      return when {
        // TODO: broken when fn is a pap
//        fn.arity == rands.size -> indirectCallNode.call(fn.callTarget, *executeRands(frame))
        fn.arity == rands.size -> fn.call(executeRands(frame))
        fn.arity > rands.size -> fn.pap(executeRands(frame)) // not enough arguments, pap node
        else -> {
          CompilerDirectives.transferToInterpreterAndInvalidate()
          @Suppress("UNCHECKED_CAST")
          this.replace(App(App(rator, rands.copyOf(fn.arity) as Array<Code>), rands.copyOfRange(fn.arity, rands.size)))
          fn.call(executeRands(frame))
        }
      }
    }

    override fun hasTag(tag: Class<out Tag>?) = tag == StandardTags.CallTag::class.java || super.hasTag(tag)
  }

  @TypeSystemReference(DataTypes::class)
  @NodeInfo(shortName = "Arg")
  class Arg(private val index: Int) : Code() {
    @Throws(NeutralException::class)
    override fun execute(frame: VirtualFrame): Any? = throwIfNeutralValue(frame.arguments[index])
    override fun executeAny(frame: VirtualFrame): Any? = frame.arguments[index]
    override fun isAdoptable() = false
  }

  class If(
    val type: Type,
    @field:Child private var condNode: Code,
    @field:Child private var thenNode: Code,
    @field:Child private var elseNode: Code,
    loc: Loc? = null
  ) : Code(loc) {
    private val conditionProfile = ConditionProfile.createBinaryProfile()

    @Throws(NeutralException::class)
    private fun branch(frame: VirtualFrame): Boolean =
      try {
        conditionProfile.profile(condNode.executeBoolean(frame))
      } catch (e: UnexpectedResultException) {
        panic("non-boolean branch", e)
      } catch (e: NeutralException) {
        neutral(type, Neutral.NIf(e.term, thenNode.executeAny(frame), elseNode.executeAny(frame)))
      }

    @Throws(NeutralException::class)
    override fun execute(frame: VirtualFrame): Any? =
      if (branch(frame)) thenNode.execute(frame)
      else elseNode.execute(frame)

    @Throws(UnexpectedResultException::class, NeutralException::class)
    override fun executeInteger(frame: VirtualFrame): Int =
      if (branch(frame)) thenNode.executeInteger(frame)
      else elseNode.executeInteger(frame)

    @Throws(UnexpectedResultException::class, NeutralException::class)
    override fun executeBoolean(frame: VirtualFrame): Boolean =
      if (branch(frame)) thenNode.executeBoolean(frame)
      else elseNode.executeBoolean(frame)
  }

  // lambdas can be constructed from foreign calltargets, you just need to supply an arity
  @Suppress("NOTHING_TO_INLINE")
  @TypeSystemReference(DataTypes::class)
  @NodeInfo(shortName = "Lambda")
  class Lam(
    private val closureFrameDescriptor: FrameDescriptor?,
    @field:Children internal val captureSteps: Array<FrameBuilder>,
    private val arity: Int,
    // @field:Child
    internal var callTarget: RootCallTarget,
    internal val type: Type,
    loc: Loc? = null
  ) : Code(loc) {

    // do we need to capture an environment?
    private inline fun isSuperCombinator() = closureFrameDescriptor != null

    override fun execute(frame: VirtualFrame) = Closure(captureEnv(frame), arrayOf(), arity, type, callTarget)
    override fun executeClosure(frame: VirtualFrame): Closure = Closure(captureEnv(frame), arrayOf(), arity, type, callTarget)

    @ExplodeLoop
    private fun captureEnv(frame: VirtualFrame): MaterializedFrame? {
      if (!isSuperCombinator()) return null
      val env = Truffle.getRuntime().createMaterializedFrame(arrayOf(), closureFrameDescriptor)
      captureSteps.forEach { it.build(env, frame) }
      return env.materialize()
    }

    // root to render capture steps opaque
    override fun hasTag(tag: Class<out Tag>?) = tag == StandardTags.RootTag::class.java || tag == StandardTags.ExpressionTag::class.java
  }


  @NodeInfo(shortName = "Read")
  abstract class Var protected constructor(private val slot: FrameSlot, loc: Loc? = null) : Code(loc) {

    @Specialization(rewriteOn = [FrameSlotTypeException::class])
    @Throws(FrameSlotTypeException::class)
    protected fun readInt(frame: VirtualFrame): Int = frame.getInt(slot)

    @Specialization(rewriteOn = [FrameSlotTypeException::class])
    @Throws(FrameSlotTypeException::class)
    protected fun readBoolean(frame: VirtualFrame): Boolean = frame.getBoolean(slot)

    @Specialization(replaces = ["readInt", "readBoolean"])
    protected fun read(frame: VirtualFrame): Any? = frame.getValue(slot)

    override fun isAdoptable() = false
  }

  @Suppress("unused")
  class Ann(@field:Child private var body: Code, val type: Type, loc: Loc? = null) : Code(loc) {
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
  @Suppress("unused")
  abstract class CallBuiltin(
    // type of whole application
    val type: Type,
    private val builtin: Builtin,
    @field:Children internal var args: Array<Code>,
    loc: Loc? = null
  ) : Code(loc) {
    // TODO: could inline this?
    private fun executeArgs(frame: VirtualFrame): Pair<Array<Any?>,Boolean> {
      var neutral = false
      val vals: ArrayList<Any?> = ArrayList()
      for (x in args) {
        vals.add(try {
          x.execute(frame)
        } catch (n: NeutralException) {
          neutral = true
          n.get()
        })
      }
      return Pair(vals.toTypedArray(), neutral)
    }

    override fun executeAny(frame: VirtualFrame): Any? {
      val (vals,neutral) = executeArgs(frame)
      if (neutral) {
        return NeutralValue(type, Neutral.NCallBuiltin(builtin, vals))
      } else {
        return builtin.execute(vals)
      }
    }

    @Throws(NeutralException::class)
    override fun execute(frame: VirtualFrame): Any? {
      val (vals,neutral) = executeArgs(frame)
      if (neutral) {
        neutral(type, Neutral.NCallBuiltin(builtin, vals))
      } else {
        return builtin.execute(vals)
      }
    }

    @Throws(NeutralException::class)
    override fun executeInteger(frame: VirtualFrame): Int {
      val (vals,neutral) = executeArgs(frame)
      if (neutral) {
        neutral(type, Neutral.NCallBuiltin(builtin, vals))
      } else {
        return builtin.executeInteger(vals)
      }
    }

    @Throws(NeutralException::class)
    override fun executeUnit(frame: VirtualFrame) {
      val (vals,neutral) = executeArgs(frame)
      if (neutral) {
        neutral(type, Neutral.NCallBuiltin(builtin, vals))
      } else {
        return builtin.executeUnit(vals)
      }
    }

    @Throws(NeutralException::class)
    override fun executeBoolean(frame: VirtualFrame): Boolean {
      val (vals,neutral) = executeArgs(frame)
      if (neutral) {
        neutral(type, Neutral.NCallBuiltin(builtin, vals))
      } else {
        return builtin.executeBoolean(vals)
      }
    }
  }

// instrumentation
  @Suppress("NOTHING_TO_INLINE","unused")
  @NodeInfo(shortName = "LitBool")
  class LitBool(val value: Boolean, loc: Loc? = null): Code(loc) {
    @Suppress("UNUSED_PARAMETER")
    override fun execute(frame: VirtualFrame) = value
    @Suppress("UNUSED_PARAMETER")
    override fun executeBoolean(frame: VirtualFrame) = value
  }

  @Suppress("NOTHING_TO_INLINE")
  @NodeInfo(shortName = "LitInt")
  class LitInt(val value: Int, loc: Loc? = null): Code(loc) {
    @Suppress("UNUSED_PARAMETER")
    override fun execute(frame: VirtualFrame) = value
    @Suppress("UNUSED_PARAMETER")
    override fun executeInteger(frame: VirtualFrame) = value
  }

  @Suppress("NOTHING_TO_INLINE","unused")
  @NodeInfo(shortName = "LitBigInt")
  class LitBigInt(val value: BigInt, loc: Loc? = null): Code(loc) {
    @Suppress("UNUSED_PARAMETER")
    override fun execute(frame: VirtualFrame) = value
    @Throws(UnexpectedResultException::class)
    @Suppress("UNUSED_PARAMETER")
    override fun executeInteger(frame: VirtualFrame): Int =
      try {
        if (value.fitsInInt()) value.asInt()
        else throw UnexpectedResultException(value)
      } catch (e: UnsupportedMessageException) {
        panic("fitsInInt lied", e)
      }
  }

  class Lit(val value: Any, loc: Loc? = null): Code(loc) {
    override fun execute(frame: VirtualFrame) = value
  }

  companion object {
    fun `var`(slot: FrameSlot, loc: Loc? = null): Var = CodeFactory.VarNodeGen.create(slot, loc)

    // invariant callTarget points to a native function body with known arity
    @Suppress("UNUSED")
    fun lam(callTarget: RootCallTarget, type: Type, loc: Loc? = null): Lam {
      val root = callTarget.rootNode
      assert(root is ClosureRootNode)
      return lam((root as ClosureRootNode).arity, callTarget, type, loc)
    }

    // package a foreign root call target with known arity
    fun lam(arity: Int, callTarget: RootCallTarget, type: Type, loc: Loc? = null): Lam {
      return lam(null, noFrameBuilders, arity, callTarget, type, loc)
    }

    //@Suppress("unused")
    fun lam(closureFrameDescriptor: FrameDescriptor, captureSteps: Array<FrameBuilder>, callTarget: RootCallTarget, type: Type, loc: Loc? = null): Lam {
      val root = callTarget.rootNode
      assert(root is ClosureRootNode)
      return lam(closureFrameDescriptor, captureSteps, (root as ClosureRootNode).arity, callTarget, type, loc)
    }

    // ensures that all the invariants for the constructor are satisfied
    fun lam(closureFrameDescriptor: FrameDescriptor?, captureSteps: Array<FrameBuilder>, arity: Int, callTarget: RootCallTarget, type: Type, loc: Loc? = null): Lam {
      assert(arity > 0)
      val hasCaptureSteps = captureSteps.isNotEmpty()
      assert(hasCaptureSteps == isSuperCombinator(callTarget)) { "mismatched calling convention" }
      return Lam(
        if (!hasCaptureSteps) null else closureFrameDescriptor ?: FrameDescriptor(),
        captureSteps,
        arity,
        callTarget,
        type,
        loc
      )
    }
  }
}