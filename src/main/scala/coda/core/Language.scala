package coda.core

import com.oracle.truffle.api.{ CallTarget, Scope, Truffle, TruffleFile, TruffleLanguage, Assumption }
import com.oracle.truffle.api.TruffleLanguage.{ ContextPolicy, Env, InlineParsingRequest, ParsingRequest, Registration }
import com.oracle.truffle.api.frame.Frame
import com.oracle.truffle.api.nodes.{ ExecutableNode, Node }
import com.oracle.truffle.api.source.{ Source, SourceSection } 
import org.graalvm.options.{ OptionValues }
import java.io.{ InputStream, IOException }
import java.nio.charset.Charset
import java.nio.{ ByteOrder, ByteBuffer }
import java.nio.file.StandardOpenOption
// import org.graalvm.ployglot.{ Context => PolyglotContext }
import scala.annotation.meta.field
import scala.collection.mutable.ArrayBuffer
import scala.Function0
import scala.util.{ Try, Using }

class Context(val language: Language, var env: Env) {
  val singleThreadedAssumption = Truffle.getRuntime.createAssumption("context is single threaded")
  // val shutdownHooks = ArrayBuffer.empty[Function0[Unit]]
  // def addShutdownHook(hook: Function0[Unit]) = shutdownHooks.addOne(hook)
  // def shutdown = shutdownHooks.foreach( (f) => f() )
  def shutdown: Unit = {}
}

@Registration(
  id = Language.ID,
  name = Language.NAME,
  version = "0.0.0", // Language.VERSION,
  defaultMimeType = Language.MIME_TYPE,
  characterMimeTypes = Array(Language.MIME_TYPE),
  byteMimeTypes = Array(Language.BYTECODE_MIME_TYPE),
  contextPolicy = ContextPolicy.SHARED,
  fileTypeDetectors = Array(classOf[Language.Detector]),
  interactive = true, // eventually this will be false
  internal = false // eventually this will be true
)
class Language extends TruffleLanguage[Context] {
  override def createContext(env: Env): Context = new Context(this, env) {} // cheap and easy
  override def initializeContext(ctx: Context): Unit = {} // TODO: any expensive init here, like stdlib loading
  override def finalizeContext(ctx: Context): Unit =  { ctx.shutdown } // TODO: any expensive shutdown here

  override def parse(request: InlineParsingRequest): ExecutableNode = ???
  override def parse(request: ParsingRequest): CallTarget = ???

  override def isObjectOfLanguage(obj: Any): Boolean = false

  def singleContextAssumption: Assumption = Truffle.getRuntime.createAssumption("Only a single context is active")
  override def initializeMultipleContexts: Unit = { singleContextAssumption.invalidate }
  override def areOptionsCompatible(a: OptionValues, b: OptionValues): Boolean = true // no options!

  override def initializeMultiThreading(ctx: Context): Unit = { ctx.singleThreadedAssumption.invalidate }
  override def isThreadAccessAllowed(thread: Thread, singleThreaded: Boolean) = true

  @throws(classOf[Exception])
  override def initializeThread(ctx: Context, thread: Thread): Unit = {}
  override def disposeThread(ctx: Context, thread: Thread): Unit = {}

  override def findMetaObject(ctx: Context, value: Any): Any = null
  override def findSourceLocation(ctx: Context, value: Any): SourceSection = null

  // if we return false, we have to use env.out, env.in, env.err to handle "printing" this in the repl.
  override def isVisible(ctx: Context, value: Any): Boolean = true

  // how core should print things
  override def toString(ctx: Context, value: Any) = value.toString

  // overload once we have some clearer plan for trimming frames a la stg?
  override def findLocalScopes(ctx: Context, node: Node, frame: Frame) = super.findLocalScopes(ctx,node,frame)
  override def findTopScopes(ctx: Context) = super.findTopScopes(ctx)

  override def patchContext(ctx: Context, env: Env): Boolean = { ctx.env = env; true }
}


object Language {
  final val ID = "coda-core"
  final val NAME = "Coda Core"
  final val VERSION_MAJOR = 0
  final val VERSION_MINOR = 0
  final val VERSION_MICRO = 0
  // val VERSION = VERSION_MAJOR + "." + VERSION_MINOR + "." + VERSION_MICRO
  final val MIME_TYPE = "application/x-coda-core"
  final val EXTENSION = "core"
  final val BYTECODE_MIME_TYPE = "application/x-coda-bytecode"
  final val BYTECODE_EXTENSION = "cb"
  final val BYTECODE_MAGIC_WORD = 0xC0DAC0DEL
  type child = Node.Child @field

  class Detector extends TruffleFile.FileTypeDetector {
    @throws(classOf[IOException])
    override def findMimeType(file: TruffleFile): String = {
      val fileName = file.getName
      if (fileName == null) null
      else if (fileName.endsWith(Language.EXTENSION)) { Language.MIME_TYPE }
      else if ((fileName.endsWith(Language.BYTECODE_EXTENSION) && (readMagicWord(file) == Language.BYTECODE_MAGIC_WORD))) Language.BYTECODE_MIME_TYPE
      else null
    }

    def readMagicWord(file: TruffleFile): Long = {
      val result : Try[Long] = Using (file.newInputStream(StandardOpenOption.READ)) { is => 
        val buffer = Array.fill[Byte](4)(0)
        if (is.read(buffer) != buffer.length) 0L
        else Integer.toUnsignedLong(ByteBuffer.wrap(buffer).order(ByteOrder.nativeOrder).getInt)
      }
      result.recover({
        case (_ : IOException) | (_ : SecurityException) => 0L
      }).get
    }

    @throws(classOf[IOException])
    override def findEncoding(file: TruffleFile): Charset = null
  }
}

import Language.child
