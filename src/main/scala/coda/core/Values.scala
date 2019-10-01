package coda.core

import com.oracle.truffle.api.CompilerDirectives.{ CompilationFinal }
import com.oracle.truffle.api.dsl.{ Fallback, ImplicitCast, Specialization, TypeSystem, TypeSystemReference }
import com.oracle.truffle.api.frame.{ VirtualFrame }
import com.oracle.truffle.api.nodes.{ Node, NodeInfo }
import com.oracle.truffle.api.source.{ SourceSection }
import java.math.BigInteger
import java.lang.Math

case class Symbol(name: String)

@TypeSystem(
  Array(
    classOf[Boolean],
    //classOf[Float],
    classOf[Double],
    //classOf[Int],
    classOf[Long],
    //classOf[Short],
    //classOf[Byte],
    //classOf[Char],
    classOf[BigInteger],
    classOf[String],
    classOf[Symbol]
  )
)
class Values {}

object Values {
  // used so we can have an unboxed long-based fast path for "small" big integer computations
  @ImplicitCast
  def asBigInteger(value: Long): BigInteger = BigInteger.valueOf(value)
}

/*
// can we use a typed intermediate core like this?
abstract class CoreNode[@specialized T] extends Node {
  @CompilationFinal
  var sourceSection: SourceSection = null
  abstract def execute(frame: VirtualFrame): T
}
*/

/*
case class CoreFun[A,B](arguments: FrameSlot extends CoreNode[Function[A,B]] {
  Truffle.getRuntime.createCallTarget(RootNode(arguments, bodyNodes, frameDescriptor))

}

// FunctionN? we need the right number of arguments
abstract class BuiltinNode[T] extends Node {
 

}

blah blah blah
*/

@TypeSystemReference(classOf[Values])
@NodeInfo(language = "Coda Core", description = "The abstract base node for expressions")
abstract class CoreNode (@CompilationFinal val sourceSection: SourceSection) extends Node {
  def execute(frame: VirtualFrame): Any
  //@throws(classOf[UnexpectedResultException])
  //def executeLong(frame: VirtualFrame): Long
  //def executeBigInteger(frame: VirtualFrame): BigInteger
  //def executeDouble(frame: VirtualFrame): Double
  //def executeBoolean(frame: VirtualFrame): Boolean
  //def executeSymbol(frame: VirtualFrame): Symbol
  //def executeString(frame: VirtualFrame): String
}

abstract class BigAdd { // extends CoreNode[BigInteger] { // extends BuiltinNode {
  @Specialization(rewriteOn = Array(classOf[ArithmeticException]))
  def add(a: Long, b: Long): Long = Math.addExact(a,b)
  @Specialization
  def add(a: BigInteger, b: BigInteger) = a.add(b)
}

abstract class BigSub { // extendfs CoreNode[BigInteger] {
  @Specialization(rewriteOn = Array(classOf[ArithmeticException]))
  def subtract(a: Long, b: Long) = Math.subtractExact(a,b)
  @Specialization
  def subtract(a: BigInteger, b: BigInteger) = a.subtract(b)
}

abstract class BigNeg { // extends CoreNode[BigInteger] {
  @Specialization(rewriteOn = Array(classOf[ArithmeticException]))
  def negate(a: Long): Long = Math.negateExact(a)
  @Specialization
  def negate(a: BigInteger): BigInteger = a.negate
}

abstract class BigDiv { // extends CoreNode[BigInteger] {
  @Specialization(guards = Array("b == 2"), rewriteOn = Array(classOf[ArithmeticException]))
  def div2long(a: Long, b: Long): Long = a >> 1 // ignore b
  @Specialization(replaces = Array("div2long"))
  def divlong(a: Long, b: Long): Long = a/b
  @Specialization(guards = Array("b == 2"), rewriteOn = Array(classOf[ArithmeticException]))
  def div2(a: BigInteger, b: BigInteger): BigInteger = a.shiftRight(1) // ignore b
  @Fallback
  def div(a: BigInteger, b: BigInteger): BigInteger = a.divide(b)
}
