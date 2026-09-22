package cadenza.data

import cadenza.frame.DataFrame
import cadenza.jit.CallUtils
import cadenza.jit.InteropDispatch
import cadenza.jit.InteropDispatchNodeGen
import com.oracle.truffle.api.dsl.Cached
import cadenza.jit.ClosureRootNode
import cadenza.semantics.Type
import cadenza.semantics.Type.Arr
import cadenza.semantics.after
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.frame.MaterializedFrame
import com.oracle.truffle.api.interop.ArityException
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnsupportedTypeException
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.nodes.ExplodeLoop


// TODO: consider storing env in papArgs, to make indirect calls faster
// (don't need to branch on env + pap, just pap)
// & store flag if it has an env & read it from papArgs?
@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary::class)
class Closure (
  @JvmField val env: DataFrame? = null,
  @JvmField @CompilerDirectives.CompilationFinal(dimensions = 1) val papArgs: Array<Any?>,
  @JvmField val arity: Int,
  private val targetType: Type,
  @JvmField val callTarget: RootCallTarget
) : TruffleObject {
  val type get() = targetType.after(papArgs.size)

  init {
    // TODO: disabling for now, to support other calltargets
    // should we have a different Lam or like expectCallTarget for them?
//    assert(callTarget.rootNode is ClosureRootNode) { "not a function body" }
    if (callTarget.rootNode is ClosureRootNode) {
      assert(env != null == (callTarget.rootNode as ClosureRootNode).hasEnvironment()) { "calling convention mismatch" }
      assert(arity + papArgs.size == (callTarget.rootNode as ClosureRootNode).arity)
    } else {
      assert(env == null)
    }
    assert(arity <= targetType.arity - papArgs.size)
  }

  override fun equals(other: Any?): Boolean {
    return (
      (other is Closure) &&
      (callTarget == other.callTarget) &&
      (arity == other.arity) &&
      (papArgs.contentEquals(other.papArgs)) &&
      (env == other.env)
    )
  }

  @ExportMessage
  fun isExecutable() = true

  @ExportMessage
  @CompilerDirectives.TruffleBoundary
  fun toDisplayString(@Suppress("UNUSED_PARAMETER") allowSideEffects: Boolean): String = "<function/$arity>"

  // allow the use of our closures from other polyglot languages
  @ExportMessage
  @ExplodeLoop
  @Throws(ArityException::class, UnsupportedTypeException::class)
  fun execute(arguments: Array<Any?>,
              @Cached(value = "createArgumentImporter()", uncached = "getUncachedArgumentImporter()", neverDefault = true) importer: ImportArgumentsNode,
              @Cached(value = "createInteropDispatch()", uncached = "getUncachedInteropDispatch()", neverDefault = true) dispatch: InteropDispatch): Any? {
    val maxArity = type.arity
    val len = arguments.size
    if (len > maxArity) throw ArityException.create(0, maxArity, len)
    return dispatch.execute(this, importer.execute(type, arguments))
  }

  companion object {
    @JvmStatic fun createArgumentImporter(): ImportArgumentsNode = ImportArgumentsNodeGen.create()
    @JvmStatic fun getUncachedArgumentImporter(): ImportArgumentsNode = ImportArgumentsNodeGen.getUncached()
    @JvmStatic fun createInteropDispatch(): InteropDispatch = InteropDispatchNodeGen.create()
    @JvmStatic fun getUncachedInteropDispatch(): InteropDispatch = InteropDispatchNodeGen.getUncached()
  }

  override fun hashCode(): Int =
    31 * (31 * (31 * callTarget.hashCode() + arity) + papArgs.contentHashCode()) + (env?.hashCode() ?: 0)

  fun pap(arguments: Array<out Any?>): Closure = pap(arguments, 0, arguments.size)

  /** Bind the retained fixed-point token without the general escaping-PAP boundary. */
  internal fun papFixedSelf(token: Any): Closure {
    assert(arity == 2)
    val prefixSize = papArgs.size
    val combined = arrayOfNulls<Any>(prefixSize + 1)
    System.arraycopy(papArgs, 0, combined, 0, prefixSize)
    combined[prefixSize] = token
    return Closure(env, combined, arity - 1, targetType, callTarget)
  }

  /** Copy a checked argument range directly into the escaping partial application. */
  @CompilerDirectives.TruffleBoundary
  fun pap(arguments: Array<out Any?>, offset: Int, length: Int): Closure {
    val combined = arrayOfNulls<Any>(papArgs.size + length)
    System.arraycopy(papArgs, 0, combined, 0, papArgs.size)
    System.arraycopy(arguments, offset, combined, papArgs.size, length)
    return Closure(env, combined, arity - length, targetType, callTarget)
  }
}

fun append(xs: Array<out Any?>, ys: Array<out Any?>): Array<Any?> = appendL(xs, xs.size, ys, ys.size)
fun consAppend(x: Any, xs: Array<out Any?>, ys: Array<out Any?>): Array<Any?> = consAppendL(x, xs, xs.size, ys, ys.size)
private fun cons(x: Any, xs: Array<out Any?>): Array<Any?> = consL(x, xs, xs.size)

// kotlin emits null checks in fn preamble for all nullable args
// here it effects dispatch fast path, so xs & ys need to be nullable
fun appendL(xs: Array<out Any?>?, xsSize: Int, ys: Array<out Any?>?, ysSize: Int): Array<Any?> {
  val zs = arrayOfNulls<Any>(xsSize + ysSize)
  System.arraycopy(xs, 0, zs, 0, xsSize)
  System.arraycopy(ys, 0, zs, xsSize, ysSize)
  return zs
}

fun consAppendL(x: Any, xs: Array<out Any?>?, xsSize: Int, ys: Array<out Any?>?, ysSize: Int): Array<Any?> {
  val zs = appendLSkip(1, xs, xsSize, ys, ysSize)
  zs[0] = x
  return zs
}

fun appendLSkip(skip: Int, xs: Array<out Any?>?, xsSize: Int, ys: Array<out Any?>?, ysSize: Int): Array<Any?> {
  val zs = arrayOfNulls<Any>(skip + xsSize + ysSize)
  System.arraycopy(xs, 0, zs, skip, xsSize)
  System.arraycopy(ys, 0, zs, skip + xsSize, ysSize)
  return zs
}

fun consL(x: Any, xs: Array<out Any?>, xsSize: Int): Array<Any?> {
  val ys = arrayOfNulls<Any>(xsSize + 1)
  ys[0] = x
  System.arraycopy(xs, 0, ys, 1, xsSize)
  return ys
}


private fun consTake(x: Any, n: Int, xs: Array<out Any?>): Array<Any?> {
  val ys = arrayOfNulls<Any>(n + 1)
  ys[0] = x
  System.arraycopy(xs, 0, ys, 1, n)
  return ys
}

fun drop(k: Int, xs: Array<out Any?>): Array<Any?> {
  val ys = arrayOfNulls<Any>(xs.size - k)
  System.arraycopy(xs, k, ys, 0, xs.size - k)
  return ys
}

inline fun<reified T> take(k: Int, xs: Array<out T>): Array<T> {
  val ys = arrayOfNulls<T>(k)
  System.arraycopy(xs, 0, ys, 0, k)
  return ys as Array<T>
}


inline fun<S, reified T> map(xs: Array<out S>, f: (x: S) -> T): Array<T> {
  val ys = arrayOfNulls<T>(xs.size)
  xs.forEachIndexed { ix, x -> ys[ix] = f(x) }
  return ys as Array<T>
}
