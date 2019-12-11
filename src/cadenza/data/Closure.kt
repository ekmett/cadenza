package cadenza.data

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


// TODO: consider splitting Closure(callTarget, arity, type) from env & pap & statically allocate that part
@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary::class)
class Closure (
  @field:CompilerDirectives.CompilationFinal val env: MaterializedFrame? = null,
  val papArgs: Array<Any?>,
  val arity: Int,
  private val targetType: Type,
  val callTarget: RootCallTarget
) : TruffleObject {
  val type get() = targetType.after(papArgs.size)

  init {
    // TODO: disabling for now, to support other calltargets
    // should we have a different Lam or like expectCallTarget for them?
//    assert(callTarget.rootNode is ClosureRootNode) { "not a function body" }
    if (callTarget.rootNode is ClosureRootNode) {
      assert(env != null == (callTarget.rootNode as ClosureRootNode).isSuperCombinator()) { "calling convention mismatch" }
      assert(arity + papArgs.size == (callTarget.rootNode as ClosureRootNode).arity)
    } else {
      assert(env == null)
    }
    assert(arity <= type.arity)
  }

  @ExportMessage
  fun isExecutable() = true

  // allow the use of our closures from other polyglot languages
  @ExportMessage
  @ExplodeLoop
  @Throws(ArityException::class, UnsupportedTypeException::class)
  fun execute(vararg arguments: Any?): Any? {
    val maxArity = type.arity
    val len = arguments.size
    if (len > maxArity) throw ArityException.create(maxArity, len)
    arguments.fold(type) { t, it -> (t as Arr).apply { argument.validate(it) }.result }
    @Suppress("UNCHECKED_CAST")
    return call(arguments)
  }

  // only used for InteropLibrary execute
  fun call(arguments: Array<out Any?>): Any? {
    val args = append(papArgs, arguments)
    val len = arguments.size
    return when {
      len < arity -> Closure(env, args, arity - len, targetType, callTarget)
      len == arity ->
        if (env != null) callTarget.call(env, *args)
        else callTarget.call(*args)
      else -> {
        val g =
          if (env != null) callTarget.call(*consTake(env, arity, args))
          else callTarget.call(*(args.take(arity).toTypedArray()))
        (g as Closure).call(drop(arity, args))
      }
    }
  }

  // construct a partial application node, which should check that it is a PAP itself
//  @ExplodeLoop
  @CompilerDirectives.TruffleBoundary
  fun pap(@Suppress("UNUSED_PARAMETER") arguments: Array<out Any?>): Closure {
    val len = arguments.size
    return Closure(env, append(papArgs, arguments), arity - len, targetType, callTarget)
  }
}

@ExplodeLoop
private fun append(xs: Array<Any?>, ys: Array<out Any?>): Array<Any?> {
  val zs = xs.copyOf(xs.size + ys.size)
  System.arraycopy(ys, 0, zs, xs.size, ys.size)
  return zs
}

// TODO: incompatible, pick one
@ExplodeLoop
private fun cons(x: Any, xs: Array<out Any?>): Array<Any?> {
  val ys = arrayOfNulls<Any>(xs.size + 1)
  ys[0] = x
  System.arraycopy(xs, 0, ys, 1, xs.size)
  @Suppress("UNCHECKED_CAST")
  return ys
}

@ExplodeLoop
private fun consTake(x: Any, n: Int, xs: Array<out Any?>): Array<Any?> {
  val ys = arrayOfNulls<Any>(n + 1)
  ys[0] = x
  System.arraycopy(xs, 0, ys, 1, n)
  @Suppress("UNCHECKED_CAST")
  return ys
}

@ExplodeLoop
private fun drop(k: Int, xs: Array<out Any?>): Array<Any?> {
  @Suppress("UNCHECKED_CAST")
  return (xs as Array<Any?>).copyOfRange(k, xs.size) // kotlin operator requires an Array<T> not an Array<out T>, grr.
}