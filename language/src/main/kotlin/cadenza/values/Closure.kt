package cadenza.values

import cadenza.*
import cadenza.nodes.*
import cadenza.types.Type
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.frame.MaterializedFrame
import com.oracle.truffle.api.interop.ArityException
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnsupportedTypeException
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.nodes.ExplodeLoop

import java.util.Arrays

@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary::class)
class Closure// invariant: target should have been constructed from a FunctionBody
// also assumes that env matches the shape expected by the function body
(val env: MaterializedFrame?, val arity: Int // local minimum arity to do anything, below this construct PAPs, above this pump arguments.
 , val type: Type // tells us the maximum number of arguments this can take and how to lint foreign arguments.
 , val callTarget: RootCallTarget) : TruffleObject {
  val isSuperCombinator: Boolean
    get() = env != null

  val isExecutable: Boolean
    @ExportMessage
    get() = true

  init {
    assert(callTarget.rootNode is ClosureRootNode) { "not a function body" }
    assert(env != null == (callTarget.rootNode as ClosureRootNode).isSuperCombinator) { "calling convention mismatch" }
    assert(arity <= type.arity)
  }

  // combinator
  constructor(arity: Int, type: Type, callTarget: RootCallTarget) : this(null, arity, type, callTarget) {}

  // allow the use of our closures from other polyglot languages
  @ExportMessage
  @ExplodeLoop
  @Throws(ArityException::class, UnsupportedTypeException::class)
  fun execute(vararg arguments: Any?): Any? {
    val maxArity = type.arity.toInt()
    val len = arguments.size
    if (len > maxArity) throw ArityException.create(maxArity, len)
    var currentType = type
    for (argument in arguments) { // lint foreign arguments for safety
      val arr = currentType as Type.Arr // safe by arity check
      arr.argument.validate(argument)
      currentType = arr.result
    }
    @Suppress("UNCHECKED_CAST")
    return call(arguments as Array<Any?>)
  }

  // this logic needs to be copied into Code.App
  fun call(arguments: Array<Any?>): Any? {
    val len = arguments.size
    if (len < arity) {
      return pap(arguments)
    } else if (len == arity) {
      return if (isSuperCombinator)
        callTarget.call(cons(env, arguments))
      else
        callTarget.call(arguments)
    } else {
      // the result _must_ be a closure.
      val next = callTarget.call(consTake(env, arity, arguments)) as Closure
      return next.call(drop(arity, arguments))
    }
  }


  // construct a partial application node, which should check that it is a PAP itself
  @ExplodeLoop
  fun pap(@Suppress("UNUSED_PARAMETER") arguments: Array<Any?>): Closure? {
    return null // TODO
  }

  @ExplodeLoop
  private inline fun <reified T> cons(x: T, xs: Array<T>): Array<T> {
    val ys = arrayOfNulls<T>(xs.size + 1)
    ys[0] = x
    System.arraycopy(xs, 0, ys, 1, xs.size)
    @Suppress("UNCHECKED_CAST")
    return ys as Array<T>
  }

  @ExplodeLoop
  private inline fun <reified T> consTake(x: T, n: Int, xs: Array<T>): Array<T> {
    val ys = arrayOfNulls<T>(n + 1)
    ys[0] = x
    System.arraycopy(xs, 0, ys, 1, n)
    @Suppress("UNCHECKED_CAST")
    return ys as Array<T>
  }

  @ExplodeLoop
  private fun <T> drop(k: Int, xs: Array<T>): Array<T> {
    return xs.copyOfRange<T>(k, xs.size)
  }

}