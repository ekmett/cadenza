package cadenza

import cadenza.jit.FrameLayout
import cadenza.jit.ProgramRootNode
import cadenza.jit.initialCtx
import cadenza.jit.GenericInteropApplyRootNode
import cadenza.bytecode.BytecodeCompiler
import cadenza.bytecode.BytecodeOptions
import cadenza.semantics.CompileInfo
import cadenza.semantics.TypeError
import cadenza.syntax.*
import com.oracle.truffle.api.*
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy
import com.oracle.truffle.api.debug.DebuggerTags
import com.oracle.truffle.api.instrumentation.ProvidedTags
import com.oracle.truffle.api.instrumentation.StandardTags.*
import com.oracle.truffle.api.nodes.NodeInfo
import com.oracle.truffle.api.source.Source
import org.graalvm.options.OptionDescriptors
import org.graalvm.options.OptionValues
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

const val LANGUAGE_ID = "cadenza"
const val LANGUAGE_NAME = "Cadenza"
const val LANGUAGE_VERSION = "0"
const val LANGUAGE_MIME_TYPE = "application/x-cadenza"
const val LANGUAGE_EXTENSION = "za"

@Suppress("unused")
private val LANGUAGE_BUILTIN_SOURCE by lazy { org.graalvm.polyglot.Source.newBuilder(LANGUAGE_ID, "", "[cadenza builtin]").buildLiteral()!! }
private val LANGUAGE_SHEBANG_REGEXP by lazy {
  Pattern.compile("""^#![ \t]*(?:/(?:[^ \t]+/)?cadenza|/usr/bin/env[ \t]+(?:-S[ \t]+)?cadenza)(?:[ \t].*)?$""")
}

@Suppress("unused")
private fun lookupNodeInfo(clazz: Class<*>?): NodeInfo? =
  if (clazz == null) null
  else clazz.getAnnotation<NodeInfo>(NodeInfo::class.java) ?: lookupNodeInfo(clazz.superclass)

@Option.Group("cadenza")
@TruffleLanguage.Registration(
  id = LANGUAGE_ID,
  name = LANGUAGE_NAME,
  version = LANGUAGE_VERSION,
  defaultMimeType = LANGUAGE_MIME_TYPE,
  characterMimeTypes = [LANGUAGE_MIME_TYPE],
  contextPolicy = ContextPolicy.SHARED,
  fileTypeDetectors = [Language.Detector::class]
)
@ProvidedTags(
  CallTag::class, StatementTag::class, RootTag::class, RootBodyTag::class, ExpressionTag::class,
  DebuggerTags.AlwaysHalt::class
)
class Language : TruffleLanguage<Language.Context>() {
  class Detector : TruffleFile.FileTypeDetector {
    override fun findEncoding(@Suppress("UNUSED_PARAMETER") file: TruffleFile): Charset = StandardCharsets.UTF_8
    override fun findMimeType(file: TruffleFile): String? {
      val name = file.name ?: return null
      if (name.endsWith(".$LANGUAGE_EXTENSION")) return LANGUAGE_MIME_TYPE
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
  }

  class Context(
    @Suppress("unused") val language: Language,
    var env: Env
  ) {
    val singleThreadedAssumption = Truffle.getRuntime().createAssumption("context is single threaded")!!
    fun shutdown() {}
  }

  private val singleContextAssumption = Truffle.getRuntime().createAssumption("Only a single context is active")!!
  override fun createContext(env: Env) = Context(this, env)
  override fun initializeContext(ctx: Context?) {}
  override fun finalizeContext(ctx: Context) = ctx.shutdown()
  override fun initializeMultipleContexts() = singleContextAssumption.invalidate()
  override fun areOptionsCompatible(a: OptionValues?, b: OptionValues?) =
    a?.get(BytecodeOptions.BACKEND) == b?.get(BytecodeOptions.BACKEND)
  override fun getOptionDescriptors(): OptionDescriptors = BytecodeOptions.descriptors()
  val genericInteropTarget by lazy { GenericInteropApplyRootNode(this).callTarget }
  override fun initializeMultiThreading(ctx: Context) = ctx.singleThreadedAssumption.invalidate()
  override fun isThreadAccessAllowed(thread: Thread, singleThreaded: Boolean) = true
  override fun initializeThread(ctx: Context, thread: Thread?) {}
  override fun disposeThread(ctx: Context, thread: Thread?) {}
  override fun isVisible(ctx: Context, value: Any?) = true
  override fun patchContext(ctx: Context, env: Env): Boolean {
    ctx.env = env
    return true
  }

  override fun parse(request: ParsingRequest): CallTarget {
    val source = request.source
    // todo: request.argumentNames
    // todo: parse decls here instead of expressions?
    return parse(source)
  }

  fun parse(source: Source): CallTarget {
    val result = source.parse { program }
    when (result) {
      is Failure -> {
        throw SyntaxError(result)
      }
      is Success -> {
        try {
          if (CONTEXT.get(null).env.options[BytecodeOptions.BACKEND] == "bytecode") {
            return BytecodeCompiler(this, source).compile(result.value)
          }
          val ci = CompileInfo(source, this)
          val fd = FrameLayout()
          val witness = result.value.infer(initialCtx)
          val rootNode = ProgramRootNode(this, witness.compile(ci, fd), fd.build(), source)
          return rootNode.callTarget
        } catch (error: TypeError) {
          throw TypeCheckError(error, source.createSection(0, source.length))
        }
      }
    }
  }

  companion object {
    private val REFERENCE = LanguageReference.create(Language::class.java)
    private val CONTEXT = ContextReference.create(Language::class.java)
    fun currentContext(node: com.oracle.truffle.api.nodes.Node? = null): Context = CONTEXT.get(node)
    fun currentLanguage(node: com.oracle.truffle.api.nodes.Node? = null): Language = REFERENCE.get(node)

  }
}
