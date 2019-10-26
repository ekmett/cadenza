package cadenza

import cadenza.nodes.*
import cadenza.types.Term
import cadenza.types.Type
import cadenza.types.Type.*
import cadenza.types.Type.Companion.Nat
import cadenza.values.Closure
import com.oracle.truffle.api.*
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy
import com.oracle.truffle.api.debug.DebuggerTags
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.instrumentation.ProvidedTags
import com.oracle.truffle.api.instrumentation.StandardTags.*
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnsupportedMessageException
import com.oracle.truffle.api.nodes.NodeInfo
import com.oracle.truffle.api.source.SourceSection
import org.graalvm.options.*
import org.graalvm.polyglot.Source
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

const val LANGUAGE_ID = "cadenza"
const val LANGUAGE_NAME = "Cadenza"
const val LANGUAGE_VERSION = "0"
const val LANGUAGE_MIME_TYPE = "application/x-cadenza"
const val LANGUAGE_EXTENSION = "za"
val LANGUAGE_BUILTIN_SOURCE = Source.newBuilder(LANGUAGE_ID, "", "[cadenza builtin]").buildLiteral()
private val LANGUAGE_SHEBANG_REGEXP = Pattern.compile("^#! ?/usr/bin/(env +cadenza|cadenza).*")

fun lookupNodeInfo(clazz: Class<*>?): NodeInfo? {
  if (clazz == null) return null
  val info = clazz.getAnnotation<NodeInfo>(NodeInfo::class.java)
  return info ?: lookupNodeInfo(clazz.superclass)
}

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
    throw RuntimeException("unknown type")
  }
}

@Option.Group("cadenza")
@TruffleLanguage.Registration(
  id = LANGUAGE_ID,
  name = LANGUAGE_NAME,
  version = LANGUAGE_VERSION,
  defaultMimeType = LANGUAGE_MIME_TYPE,
  characterMimeTypes = [LANGUAGE_MIME_TYPE],
  contextPolicy = ContextPolicy.SHARED,
  fileTypeDetectors = [Detector::class]
)
@ProvidedTags(
  CallTag::class, StatementTag::class, RootTag::class, RootBodyTag::class, ExpressionTag::class,
  DebuggerTags.AlwaysHalt::class
)
class Language : TruffleLanguage<Context>() {
  val singleContextAssumption = Truffle.getRuntime().createAssumption("Only a single context is active")

  override fun createContext(env: TruffleLanguage.Env): Context {
    return Context(this, env)
  } // cheap and easy

  override fun initializeContext(ctx: Context?) {}
  override fun finalizeContext(ctx: Context) = ctx.shutdown()

  // stubbed: for now inline parsing requests just return 'const'
  override fun parse(request: TruffleLanguage.InlineParsingRequest?): InlineCode {
    val body = K(Nat, Nat)
    return InlineCode(this, body)
  }

  // stubbed: returns a calculation that adds two numbers
  override fun parse(request: TruffleLanguage.ParsingRequest): CallTarget {
    val rootNode = ProgramRootNode(this, intLiteral(0), FrameDescriptor())
    return Truffle.getRuntime().createCallTarget(rootNode)
  }

  override fun isObjectOfLanguage(obj: Any) = obj is TruffleObject
  override fun initializeMultipleContexts() = singleContextAssumption.invalidate()
  override fun areOptionsCompatible(a: OptionValues?, b: OptionValues?) = true
  override fun getOptionDescriptors(): OptionDescriptors? = null // Language.OPTION_DESCRIPTORS
  override fun initializeMultiThreading(ctx: Context) = ctx.singleThreadedAssumption.invalidate()
  override fun isThreadAccessAllowed(thread: Thread, singleThreaded: Boolean) = true
  override fun initializeThread(ctx: Context, thread: Thread?) {}
  override fun disposeThread(ctx: Context, thread: Thread?) {}
  override fun findMetaObject(ctx: Context, value: Any?): Any = getMetaObject(value)
  override fun findSourceLocation(ctx: Context, value: Any?): SourceSection? = null
  override fun isVisible(ctx: Context, value: Any?) = true
  override fun toString(ctx: Context, value: Any?): String = toString(value)
  override fun patchContext(ctx: Context, env: TruffleLanguage.Env): Boolean {
    ctx.env = env
    return true
  }

  fun findExportedSymbol(
    @Suppress("UNUSED_PARAMETER") context: org.graalvm.polyglot.Context,
    globalName: String,
    @Suppress("UNUSED_PARAMETER") onlyExplicit: Boolean
  ): Any? =
    when (globalName) {
      "S" -> S(Arr(Type.Nat, Arr(Type.Nat, Type.Nat)), Arr(Type.Nat, Type.Nat), Type.Nat)
      "K" -> K(Type.Nat, Type.Nat)
      "I" -> I(Type.Nat)
      "main" -> 42
      else -> null
    }

  // for testing

  fun I(tx: Type) = unary({ x -> x }, tx)
  fun K(tx: Type, ty: Type) = binary({ x, _ -> x }, tx, ty)

  @Suppress("UNUSED_PARAMETER")
  fun S(tx: Type, ty: Type, tz: Type): Code = todo("S")

  @Suppress("UNUSED_PARAMETER")
  fun unary(f: (x: Term) -> Term, argument: Type): Code = todo("unary")

  @Suppress("UNUSED_PARAMETER")
  fun binary(f: (x: Term, y: Term) -> Term, tx: Type, ty: Type): Code = todo("binary")

//        val OPTION_DESCRIPTORS: OptionDescriptors = LanguageOptionDescriptors()
//        @com.oracle.truffle.api.Option(name = "tco", help = "Tail-call optimization", category = com.oracle.truffle.api.OptionCategory.USER, stability = com.oracle.truffle.api.OptionStability.EXPERIMENTAL)
//        const val TAIL_CALL_OPTIMIZATION = OptionKey(false)
}

class Detector : TruffleFile.FileTypeDetector {
  override fun findMimeType(file: TruffleFile): String? {
    val name = file.name ?: return null
    if (name.endsWith(LANGUAGE_EXTENSION)) return LANGUAGE_MIME_TYPE
    try {
      file.newBufferedReader(StandardCharsets.UTF_8).use { fileContent ->
        val firstLine = fileContent.readLine()
        if (firstLine != null && LANGUAGE_SHEBANG_REGEXP.matcher(firstLine).matches())
          return LANGUAGE_MIME_TYPE
      }
    } catch (e: IOException) { // ok
    } catch (e: SecurityException) { // ok
    }
    return null
  }

  override fun findEncoding(_file: TruffleFile) = StandardCharsets.UTF_8
}

class Context(
  val language: Language,
  var env: TruffleLanguage.Env
) {
  val singleThreadedAssumption = Truffle.getRuntime().createAssumption("context is single threaded")!!
  fun shutdown() {}
}

fun panic(msg: String, base: Exception?): Nothing {
  CompilerDirectives.transferToInterpreter();
  val e = RuntimeException(msg, base)
  e.stackTrace = e.stackTrace.drop(1).toTypedArray()
  throw e;
}

fun panic(msg: String): Nothing {
  CompilerDirectives.transferToInterpreter();
  val e = RuntimeException(msg, null)
  e.stackTrace = e.stackTrace.drop(1).toTypedArray()
  throw e;
}

fun todo(msg: String, base: Exception?): Nothing {
  CompilerDirectives.transferToInterpreter();
  val e = RuntimeException(msg, base)
  e.stackTrace = e.stackTrace.drop(1).toTypedArray()
  throw e;
}

fun todo(msg: String): Nothing {
  CompilerDirectives.transferToInterpreter();
  val e = RuntimeException(msg, null)
  e.stackTrace = e.stackTrace.drop(1).toTypedArray()
  throw e;
}