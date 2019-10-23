package cadenza

import cadenza.nodes.ClosureRootNode
import cadenza.nodes.Code
import cadenza.nodes.FrameBuilder
import cadenza.nodes.InlineCode
import cadenza.types.Term
import cadenza.types.Type
import cadenza.types.Type.*
import cadenza.types.Type.Companion.Nat
import cadenza.values.Closure
import cadenza.values.BigInt

import com.oracle.truffle.api.*
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy
import com.oracle.truffle.api.debug.DebuggerTags
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.FrameSlot
import com.oracle.truffle.api.instrumentation.ProvidedTags
import com.oracle.truffle.api.instrumentation.StandardTags.*
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnsupportedMessageException
import com.oracle.truffle.api.source.SourceSection
import com.palantir.logsafe.exceptions.SafeRuntimeException
import org.graalvm.options.*

import java.util.function.BiFunction
import java.util.function.Function
import java.util.stream.IntStream

import cadenza.nodes.Code.*

@Option.Group("cadenza")
@TruffleLanguage.Registration(id = Language.ID, name = Language.NAME, version = Language.VERSION, defaultMimeType = Language.MIME_TYPE, characterMimeTypes = [Language.MIME_TYPE], contextPolicy = ContextPolicy.SHARED, fileTypeDetectors = [Detector::class])

@ProvidedTags(CallTag::class, StatementTag::class, RootTag::class, RootBodyTag::class, ExpressionTag::class, DebuggerTags.AlwaysHalt::class)
class Language : TruffleLanguage<Context>() {

  val singleContextAssumption = Truffle.getRuntime().createAssumption("Only a single context is active")

  public override fun createContext(env: TruffleLanguage.Env): Context {
    return Context(this, env)
  } // cheap and easy

  public override fun initializeContext(ctx: Context?) {}

  public override fun finalizeContext(ctx: Context) {
    ctx.shutdown()
  }

  // stubbed: for now inline parsing requests just return 'const'
  public override fun parse(request: TruffleLanguage.InlineParsingRequest?): InlineCode {
    println("parse0")
    val body = K(Nat, Nat)
    return InlineCode(this, body)
  }

  // stubbed: returns a calculation that adds two numbers
  public override fun parse(request: TruffleLanguage.ParsingRequest): CallTarget {
/*
        val fd = FrameDescriptor()
        val argSlots : FrameSlot[] = null; = request.argumentNames.stream().map { fd.addFrameSlot(it) }.toArray<FrameSlot>(FrameSlot[]::new  /* Currently unsupported in Kotlin */)
        val preamble = IntStream.range(0, argSlots.size).mapToObj { i -> put(argSlots[i], arg(i)) }.toArray<FrameBuilder>(FrameBuilder[]::new  /* Currently unsupported in Kotlin */)
        val arity = argSlots.size
        val content: Code? = null // Expr.intLiteral(-42);
        val body = ClosureRootNode.create(this, arity, preamble, content)
        return Truffle.getRuntime().createCallTarget(body)
        //  }
*/
    throw RuntimeException("parse")
  }

  public override fun isObjectOfLanguage(obj: Any): Boolean {
    return if (obj !is TruffleObject) false else obj is BigInt || obj is Closure
  }

  public override fun initializeMultipleContexts() {
    singleContextAssumption.invalidate()
  }

  public override fun areOptionsCompatible(a: OptionValues?, b: OptionValues?): Boolean {
    return true
  } // no options!

  override fun getOptionDescriptors(): OptionDescriptors? {
    return null; // Language.OPTION_DESCRIPTORS
  }

  public override fun initializeMultiThreading(ctx: Context) {
    ctx.singleThreadedAssumption.invalidate()
  }

  public override fun isThreadAccessAllowed(thread: Thread, singleThreaded: Boolean): Boolean {
    return true
  }

  public override fun initializeThread(ctx: Context, thread: Thread?) {}

  public override fun disposeThread(ctx: Context, thread: Thread?) {}

  public override fun findMetaObject(ctx: Context, value: Any?): Any {
    return getMetaObject(value)
  }

  public override fun findSourceLocation(ctx: Context, value: Any?): SourceSection? {
    return null
  }

  public override fun isVisible(ctx: Context, value: Any?): Boolean {
    return true
  }

  public override fun toString(ctx: Context, value: Any?): String {
    return toString(value)
  }

  public override fun patchContext(ctx: Context, env: TruffleLanguage.Env): Boolean {
    ctx.env = env
    return true
  }

  fun findExportedSymbol(_context: org.graalvm.polyglot.Context, globalName: String, onlyExplicit: Boolean): Any? {
    when (globalName) {
      "S" -> return S(Arr(Type.Nat, Arr(Type.Nat, Type.Nat)), Arr(Type.Nat, Type.Nat), Type.Nat)
      "K" -> return K(Type.Nat, Type.Nat)
      "I" -> return I(Type.Nat)
      "main" -> return 42
    }
    return null
  }

  // for testing

  internal fun I(tx: Type): Code {
    return unary({ x -> x }, tx)
  }

  internal fun K(tx: Type, ty: Type): Code {
    return binary({ x, y -> x }, tx, ty)
  }

  internal fun S(_tx: Type, _ty: Type, _tz: Type): Code {
    throw RuntimeException("nope")
  }

  fun unary(_f: (x: Term) -> Term, _argument: Type): Code {
    throw RuntimeException("unary")
  }

  // construct a binary function
  fun binary(_f: (x: Term, y: Term) -> Term, _tx: Type, _ty: Type): Code {
    throw RuntimeException("binary")
  }

  companion object {
    const val ID = "cadenza"
    const val NAME = "Cadenza"
    const val VERSION = "0"
    const val MIME_TYPE = "application/x-cadenza"
    const val EXTENSION = "za"

//        val OPTION_DESCRIPTORS: OptionDescriptors = LanguageOptionDescriptors()

//        @com.oracle.truffle.api.Option(name = "tco", help = "Tail-call optimization", category = com.oracle.truffle.api.OptionCategory.USER, stability = com.oracle.truffle.api.OptionStability.EXPERIMENTAL)
//        const val TAIL_CALL_OPTIMIZATION = OptionKey(false)

    fun getMetaObject(value: Any?): String {
      if (value == null) return "ANY"
      val interop = InteropLibrary.getFactory().getUncached(value)
      if (interop.isNumber(value) || value is Number) return "Number"
      if (interop.isBoolean(value)) return "Boolean"
      if (interop.isString(value)) return "String"
      if (interop.isNull(value)) return "NULL"
      if (interop.isExecutable(value)) return "Function"
      return if (interop.hasMembers(value)) "Object" else "Unsupported"
    }

    fun toString(value: Any?): String {
      try {
        if (value == null) return "null"
        if (value is Number) return value.toString()
        if (value is Closure) return value.toString()
        val interop = InteropLibrary.getFactory().getUncached(value)
        if (interop.fitsInLong(value)) return java.lang.Long.toString(interop.asLong(value))
        if (interop.isBoolean(value)) return java.lang.Boolean.toString(interop.asBoolean(value))
        if (interop.isString(value)) return interop.asString(value)
        if (interop.isNull(value)) return "NULL"
        if (interop.isExecutable(value)) return "Function"
        return if (interop.hasMembers(value)) "Object" else "Unsupported"
      } catch (e: UnsupportedMessageException) {
        CompilerDirectives.transferToInterpreter()
        throw SafeRuntimeException("unknown type")
      }
    }
  }
}
