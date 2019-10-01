package coda

import com.oracle.truffle.api.{ CallTarget, Truffle, TruffleLanguage, Assumption }
import com.oracle.truffle.api.TruffleLanguage.{ ContextPolicy, Env, InlineParsingRequest, ParsingRequest, Registration }
import com.oracle.truffle.api.frame.Frame
import com.oracle.truffle.api.nodes.{ ExecutableNode, Node }
import com.oracle.truffle.api.source.SourceSection
import org.graalvm.options.OptionValues
import scala.annotation.meta.field

@Registration(
  id = CoreLanguage.ID,
  name = CoreLanguage.NAME,
  version = CoreLanguage.VERSION,
  defaultMimeType = CoreLanguage.MIME_TYPE,
  characterMimeTypes = Array(CoreLanguage.MIME_TYPE),
  byteMimeTypes = Array(CoreLanguage.BYTECODE_MIME_TYPE),
  contextPolicy = ContextPolicy.SHARED,
  fileTypeDetectors = Array(classOf[CoreDetector]),
  interactive = true, // eventually this will be false
  internal = false // eventually this will be true
)
class CoreLanguage extends TruffleLanguage[CoreContext] {
  val singleContextAssumption: Assumption = Truffle.getRuntime.createAssumption("Only a single context is active")
  override def createContext(env: Env): CoreContext = new CoreContext(this, env) {} // cheap and easy
  override def initializeContext(ctx: CoreContext): Unit = {} // TODO: any expensive init here, like stdlib loading
  override def finalizeContext(ctx: CoreContext): Unit = ctx.shutdown // TODO: any expensive shutdown here
  override def parse(request: InlineParsingRequest): ExecutableNode = ???
  override def parse(request: ParsingRequest): CallTarget = ???
  override def isObjectOfLanguage(obj: Any): Boolean = false
  override def initializeMultipleContexts: Unit = singleContextAssumption.invalidate
  override def areOptionsCompatible(a: OptionValues, b: OptionValues): Boolean = true // no options!
  override def initializeMultiThreading(ctx: CoreContext): Unit = ctx.singleThreadedAssumption.invalidate
  override def isThreadAccessAllowed(thread: Thread, singleThreaded: Boolean) = true

  @throws(classOf[Exception])
  override def initializeThread(ctx: CoreContext, thread: Thread): Unit = {}
  override def disposeThread(ctx: CoreContext, thread: Thread): Unit = {}
  override def findMetaObject(ctx: CoreContext, value: Any): Any = null
  override def findSourceLocation(ctx: CoreContext, value: Any): SourceSection = null
  override def isVisible(ctx: CoreContext, value: Any): Boolean = true
  override def toString(ctx: CoreContext, value: Any) = value.toString
  override def findLocalScopes(ctx: CoreContext, node: Node, frame: Frame) = super.findLocalScopes(ctx,node,frame)
  override def findTopScopes(ctx: CoreContext) = super.findTopScopes(ctx)
  override def patchContext(ctx: CoreContext, env: Env): Boolean = { ctx.env = env; true }
}

object CoreLanguage {
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

