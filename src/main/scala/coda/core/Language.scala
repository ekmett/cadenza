package coda.core

import com.oracle.truffle.api.{ CallTarget, Truffle, TruffleLanguage, Assumption }
import com.oracle.truffle.api.TruffleLanguage.{ ContextPolicy, Env, InlineParsingRequest, ParsingRequest, Registration }
import com.oracle.truffle.api.frame.Frame
import com.oracle.truffle.api.nodes.{ ExecutableNode, Node }
import com.oracle.truffle.api.source.SourceSection
import org.graalvm.options.OptionValues
import scala.annotation.meta.field

@Registration(
  id = Language.ID,
  name = Language.NAME,
  version = Language.VERSION,
  defaultMimeType = Language.MIME_TYPE,
  characterMimeTypes = Array(Language.MIME_TYPE),
  byteMimeTypes = Array(Language.BYTECODE_MIME_TYPE),
  contextPolicy = ContextPolicy.SHARED,
  fileTypeDetectors = Array(classOf[Detector]),
  interactive = true, // eventually this will be false
  internal = false // eventually this will be true
)
class Language extends TruffleLanguage[Context] {
  val singleContextAssumption: Assumption = Truffle.getRuntime.createAssumption("Only a single context is active")
  override def createContext(env: Env): Context = new Context(this, env) {} // cheap and easy
  override def initializeContext(ctx: Context): Unit = {} // TODO: any expensive init here, like stdlib loading
  override def finalizeContext(ctx: Context): Unit = ctx.shutdown // TODO: any expensive shutdown here
  override def parse(request: InlineParsingRequest): ExecutableNode = ???
  override def parse(request: ParsingRequest): CallTarget = ???
  override def isObjectOfLanguage(obj: Any): Boolean = false
  override def initializeMultipleContexts: Unit = singleContextAssumption.invalidate
  override def areOptionsCompatible(a: OptionValues, b: OptionValues): Boolean = true // no options!
  override def initializeMultiThreading(ctx: Context): Unit = ctx.singleThreadedAssumption.invalidate
  override def isThreadAccessAllowed(thread: Thread, singleThreaded: Boolean) = true

  @throws(classOf[Exception])
  override def initializeThread(ctx: Context, thread: Thread): Unit = {}
  override def disposeThread(ctx: Context, thread: Thread): Unit = {}
  override def findMetaObject(ctx: Context, value: Any): Any = null
  override def findSourceLocation(ctx: Context, value: Any): SourceSection = null
  override def isVisible(ctx: Context, value: Any): Boolean = true
  override def toString(ctx: Context, value: Any) = value.toString
  override def findLocalScopes(ctx: Context, node: Node, frame: Frame) = super.findLocalScopes(ctx,node,frame)
  override def findTopScopes(ctx: Context) = super.findTopScopes(ctx)
  override def patchContext(ctx: Context, env: Env): Boolean = { ctx.env = env; true }
}

object Language {
  final val ID = "coda-core"
  final val NAME = "Coda Core"
  final val VERSION = "0"
  final val MIME_TYPE = "application/x-coda-core"
  final val EXTENSION = "core"
  final val BYTECODE_MIME_TYPE = "application/x-coda-bytecode"
  final val BYTECODE_EXTENSION = "cb"
  final val BYTECODE_MAGIC_WORD = 0xC0DAC0DEL
  type child = Node.Child @field
}

