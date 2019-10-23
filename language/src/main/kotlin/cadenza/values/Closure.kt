package cadenza.values

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
import cadenza.nodes.*
import jdk.jshell.spi.ExecutionControl

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
  fun pap(arguments: Array<Any?>): Closure? {
    return null // TODO
  }

  @ExplodeLoop
  private fun cons(x: Any?, xs: Array<Any?>): Array<Any?> {
    val ys = arrayOfNulls<Any>(xs.size + 1)
    ys[0] = x
    System.arraycopy(xs, 0, ys, 1, xs.size)
    return ys
  }

  @ExplodeLoop
  private fun consTake(x: Any?, n: Int, xs: Array<Any?>): Array<Any?> {
    val ys = arrayOfNulls<Any>(n + 1)
    ys[0] = x
    System.arraycopy(xs, 0, ys, 1, n)
    return ys
  }

  @ExplodeLoop
  private fun drop(k: Int, xs: Array<Any?>): Array<Any?> {
    return Arrays.copyOfRange<Any?>(xs, k, xs.size)
  }

}