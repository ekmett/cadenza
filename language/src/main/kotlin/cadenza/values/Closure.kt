package cadenza.values

import cadenza.nodes.*
import cadenza.types.*
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
  @Suppress("NOTHING_TO_INLINE")
  private inline fun isSuperCombinator() = env != null

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
    var currentType = type
    for (argument in arguments) { // lint foreign arguments for safety
      val arr = currentType as Arr // safe by arity check
      arr.argument.validate(argument)
      currentType = arr.result
    }
    @Suppress("UNCHECKED_CAST")
    return call(arguments as Array<Any?>)
  }

  fun call(arguments: Array<Any?>): Any? {
    val len = arguments.size
    return when {
      len < arity -> pap(arguments)
      len == arity ->
        if (isSuperCombinator()) callTarget.call(cons(env, arguments))
        else callTarget.call(arguments)
      else -> (callTarget.call(consTake(env, arity, arguments)) as Closure).call(drop(arity, arguments))
    }
  }

  // construct a partial application node, which should check that it is a PAP itself
  @ExplodeLoop
  fun pap(@Suppress("UNUSED_PARAMETER") arguments: Array<Any?>): Closure? {
    return null // TODO
  }
}

// TODO: incompatible, pick one
@ExplodeLoop
@Suppress("NOTHING_TO_INLINE")
private inline fun <reified T> cons(x: T, xs: Array<T>): Array<T> {
  val ys = arrayOfNulls<T>(xs.size + 1)
  ys[0] = x
  System.arraycopy(xs, 0, ys, 1, xs.size)
  @Suppress("UNCHECKED_CAST")
  return ys as Array<T>
}

@ExplodeLoop
@Suppress("NOTHING_TO_INLINE")
private inline fun <reified T> consTake(x: T, n: Int, xs: Array<T>): Array<T> {
  val ys = arrayOfNulls<T>(n + 1)
  ys[0] = x
  System.arraycopy(xs, 0, ys, 1, n)
  @Suppress("UNCHECKED_CAST")
  return ys as Array<T>
}

@ExplodeLoop
@Suppress("NOTHING_TO_INLINE")
private inline fun <T> drop(k: Int, xs: Array<T>): Array<T> {
  return xs.copyOfRange(k, xs.size)
}