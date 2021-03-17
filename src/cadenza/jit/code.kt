package cadenza.jit

import cadenza.Language
import cadenza.Loc
import cadenza.data.*
import cadenza.frame.BuildFrame
import cadenza.frame.BuildFrameNodeGen
import cadenza.frame.DataFrame
import cadenza.panic
import cadenza.section
import cadenza.semantics.Type
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.dsl.ReportPolymorphism
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.dsl.TypeSystemReference
import com.oracle.truffle.api.frame.*
import com.oracle.truffle.api.instrumentation.*
import com.oracle.truffle.api.interop.NodeLibrary
import com.oracle.truffle.api.interop.UnsupportedMessageException
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.nodes.*
import com.oracle.truffle.api.profiles.ConditionProfile
import com.oracle.truffle.api.source.SourceSection

// utility
@Suppress("NOTHING_TO_INLINE")
private inline fun isSuperCombinator(callTarget: RootCallTarget) =
  callTarget.rootNode.let { it is ClosureRootNode && it.isSuperCombinator() }

@ReportPolymorphism
@Suppress("NOTHING_TO_INLINE","unused")
@GenerateWrapper
@NodeInfo(language = "core", description = "core nodes")
@TypeSystemReference(DataTypes::class)
abstract class Code(val loc: Loc?) : Node(), InstrumentableNode {
  constructor(that: Code) : this(that.loc)

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
  override fun createWrapper(probe: ProbeNode): InstrumentableNode.WrapperNode = CodeWrapper(this, this, probe)


  @TypeSystemReference(DataTypes::class)
  @NodeInfo(shortName = "App")
  open class App(
    @field:Child var rator: Code,
    @field:Children val rands: Array<Code>,
    loc: Loc? = null,
    tail_call: Boolean = false
  ) : Code(loc) {
    @Child private var dispatch: Dispatch = DispatchNodeGen.create(rands.size, tail_call)

    @ExplodeLoop
    private fun executeRands(frame: VirtualFrame): Array<Any?> = rands.map { it.executeAny(frame) }.toTypedArray()

    private fun executeFn(frame: VirtualFrame, fn: Closure): Any? {
      // TODO: think about how foreign calltargets should work with nbe
      return dispatch.executeDispatch(frame, fn, executeRands(frame))
    }

    @Throws(NeutralException::class)
    override fun execute(frame: VirtualFrame): Any? {
      val fn = try {
        rator.executeClosure(frame)
      } catch (e: UnexpectedResultException) {
        panic("closure expected, got ${e.result}", e)
      } catch (e: NeutralException) {
        e.apply(executeRands(frame))
      }
      return executeFn(frame, fn)
    }

    override fun hasTag(tag: Class<out Tag>?) = tag == StandardTags.CallTag::class.java || super.hasTag(tag)
  }

  // only used in argPreamble
  @TypeSystemReference(DataTypes::class)
  @NodeInfo(shortName = "Arg")
  class Arg(private val index: Int, loc: Loc) : Code(loc) {
    @Throws(NeutralException::class)
    override fun execute(frame: VirtualFrame): Any? = throwIfNeutralValue(frame.arguments[index])
    override fun executeAny(frame: VirtualFrame): Any? = frame.arguments[index]
    override fun isAdoptable() = false
  }

  class If(
    val type: Type,
    @field:Child var condNode: Code,
    @field:Child var thenNode: Code,
    @field:Child var elseNode: Code,
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
    @CompilerDirectives.CompilationFinal(dimensions = 1) val captures: Array<FrameSlot>,
    private val arity: Int,
    @field:CompilerDirectives.CompilationFinal
    internal var callTarget: RootCallTarget,
    internal val type: Type,
    loc: Loc? = null
  ) : Code(loc) {
    @Child var builder: BuildFrame = BuildFrameNodeGen.create()

    // do we need to capture an environment?
    private inline fun isSuperCombinator() = closureFrameDescriptor != null

    // TODO: statically allocate the Closure when possible (when no env)
    // split between capturing Lam and not?
    // might help escape analysis w/ App
    override fun execute(frame: VirtualFrame) = Closure(captureEnv(frame), arrayOf(), arity, type, callTarget)
    override fun executeClosure(frame: VirtualFrame): Closure = Closure(captureEnv(frame), arrayOf(), arity, type, callTarget)

    @ExplodeLoop
    private fun captureEnv(frame: VirtualFrame): DataFrame? {
      if (!isSuperCombinator()) return null
      val cs = map(captures) { frame.getValue(it) }
      return builder.execute(cs)
    }

    // root to render capture steps opaque
    override fun hasTag(tag: Class<out Tag>?) = tag == StandardTags.RootTag::class.java || tag == StandardTags.ExpressionTag::class.java
  }


  @NodeInfo(shortName = "Read")
  abstract class Var protected constructor(private val slot: FrameSlot, loc: Loc? = null) : Code(loc) {

//    @Specialization(rewriteOn = [FrameSlotTypeException::class])
//    @Throws(FrameSlotTypeException::class)
//    protected fun readInt(frame: VirtualFrame): Int = frame.getInt(slot)
//
//    @Specialization(rewriteOn = [FrameSlotTypeException::class])
//    @Throws(FrameSlotTypeException::class)
//    protected fun readBoolean(frame: VirtualFrame): Boolean = frame.getBoolean(slot)

    @Specialization() //(replaces = ["readInt", "readBoolean"])
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
  class CallBuiltin(
    // type of whole application
    val type: Type,
    @field:Child private var builtin: Builtin,
    @field:Children internal var args: Array<Code>,
    loc: Loc? = null
  ) : Code(loc) {
    @ExplodeLoop
    private fun executeArgs(frame: VirtualFrame): Pair<Array<Any?>,Boolean> {
      var neutral = false
      val vals: Array<Any?> = arrayOfNulls(args.size)
      args.forEachIndexed { ix, x ->
        vals[ix] = try {
          x.execute(frame)
        } catch (n: NeutralException) {
          neutral = true
          n.get()
        }
      }
      return Pair(vals, neutral)
    }

    override fun executeAny(frame: VirtualFrame): Any? {
      val (vals,neutral) = executeArgs(frame)
      if (neutral) {
        return NeutralValue(type, Neutral.NCallBuiltin(builtin, vals))
      } else {
        return builtin.run(frame, vals)
      }
    }

    @Throws(NeutralException::class)
    override fun execute(frame: VirtualFrame): Any? {
      val (vals,neutral) = executeArgs(frame)
      if (neutral) {
        neutral(type, Neutral.NCallBuiltin(builtin, vals))
      } else {
        return builtin.run(frame, vals)
      }
    }

    @Throws(NeutralException::class)
    override fun executeInteger(frame: VirtualFrame): Int {
      val (vals,neutral) = executeArgs(frame)
      if (neutral) {
        neutral(type, Neutral.NCallBuiltin(builtin, vals))
      } else {
        return builtin.runInteger(frame, vals)
      }
    }

    @Throws(NeutralException::class)
    override fun executeUnit(frame: VirtualFrame) {
      val (vals,neutral) = executeArgs(frame)
      if (neutral) {
        neutral(type, Neutral.NCallBuiltin(builtin, vals))
      } else {
        return builtin.runUnit(frame, vals)
      }
    }

    @Throws(NeutralException::class)
    override fun executeBoolean(frame: VirtualFrame): Boolean {
      val (vals,neutral) = executeArgs(frame)
      if (neutral) {
        neutral(type, Neutral.NCallBuiltin(builtin, vals))
      } else {
        return builtin.runBoolean(frame, vals)
      }
    }
  }

  class LetRec(
    val slot: FrameSlot,
    val type: Type,
    @field:Child var value: Code,
    @field:Child var body: Code,
    loc: Loc?
  ): Code(loc) {
    @CompilerDirectives.CompilationFinal var readTarget: RootCallTarget? = null

    override fun execute(frame: VirtualFrame): Any? {
      if (readTarget === null) {
        CompilerDirectives.transferToInterpreterAndInvalidate()
        val language = lookupLanguageReference(Language::class.java).get()
        readTarget = Truffle.getRuntime().createCallTarget(ReadIndirectionRootNode(language))
      }

      val indir = Indirection()
      val clos = Closure(null, arrayOf(indir), 0, Type.Arr(Type.Obj,type), readTarget!!)
      // need to set it here in case a lambda in value captures it
      frame.setObject(slot, clos)
      val x = value.executeAny(frame)
      // ... but if not, we can avoid the indirection
      // and need to set it here anyways in case value shadows us with a let
//      frame.setObject(slot, x)
      frame.setObject(slot, clos)
      indir.value = x
      indir.set = true
      return body.execute(frame)
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

//    // invariant callTarget points to a native function body with known arity
//    @Suppress("UNUSED")
//    fun lam(callTarget: RootCallTarget, type: Type, loc: Loc? = null): Lam {
//      val root = callTarget.rootNode
//      assert(root is ClosureRootNode)
//      return lam((root as ClosureRootNode).arity, callTarget, type, loc)
//    }
//
    // package a foreign root call target with known arity
    fun lam(arity: Int, callTarget: RootCallTarget, type: Type, loc: Loc? = null): Lam {
      return lam(null, arrayOf(), arity, callTarget, type, loc)
    }
//
//    //@Suppress("unused")
//    fun lam(closureFrameDescriptor: FrameDescriptor, captureSteps: Array<FrameBuilder>, callTarget: RootCallTarget, type: Type, loc: Loc? = null): Lam {
//      val root = callTarget.rootNode
//      assert(root is ClosureRootNode)
//      return lam(closureFrameDescriptor, captureSteps, (root as ClosureRootNode).arity, callTarget, type, loc)
//    }

    // ensures that all the invariants for the constructor are satisfied
    fun lam(closureFrameDescriptor: FrameDescriptor?, captures: Array<FrameSlot>, arity: Int, callTarget: RootCallTarget, type: Type, loc: Loc? = null): Lam {
      assert(arity > 0)
      val hasCaptureSteps = captures.isNotEmpty()
      assert(hasCaptureSteps == isSuperCombinator(callTarget)) { "mismatched calling convention" }
      return Lam(
        if (!hasCaptureSteps) null else closureFrameDescriptor ?: FrameDescriptor(),
        captures,
        arity,
        callTarget,
        type,
        loc
      )
    }
  }
}