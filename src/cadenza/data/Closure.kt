package cadenza.data

import cadenza.jit.ClosureRootNode
import cadenza.semantics.Type
import cadenza.semantics.Type.Arr
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

@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary::class)
class Closure (
  private val env: MaterializedFrame? = null,
  val arity: Int,
  val type: Type,
  val callTarget: RootCallTarget
) : TruffleObject {

  init {
    assert(callTarget.rootNode is ClosureRootNode) { "not a function body" }
    assert(env != null == (callTarget.rootNode as ClosureRootNode).isSuperCombinator()) { "calling convention mismatch" }
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

  fun call(arguments: Array<out Any?>): Any? {
    val len = arguments.size
    return when {
      len < arity -> pap(arguments)
      len == arity ->
        if (env != null) callTarget.call(cons(env, arguments))
        else callTarget.call(arguments)
      else -> {
        val g =
          if (env != null) callTarget.call(consTake(env, arity, arguments))
          else callTarget.call(arguments.take(arity))
        (g as Closure).call(drop(arity, arguments))
      }
    }
  }

  // construct a partial application node, which should check that it is a PAP itself
  @ExplodeLoop
  fun pap(@Suppress("UNUSED_PARAMETER") arguments: Array<out Any?>): Closure? {
    return null // TODO
  }
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