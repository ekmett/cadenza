package stg.nodes

import stg._
import com.oracle.truffle.api.nodes._
import com.oracle.truffle.api.frame.{ FrameDescriptor, VirtualFrame }
import com.oracle.truffle.api.source.SourceSection
//import com.oracle.truffle.api.instrumentation._
//import com.oracle.truffle.api.dsl.ReportPolymorphism

abstract class StgExpressionNode {
  def execute(frame: VirtualFrame): Any // must be implemented by all subclasses
}

//class StgExpressionNode(language: Language) extends ExecutableNode(language) {
//  
//  override def executeVoid(frame: VirtualFrame): Unit = { executeGeneric(frame); () }
//}

/// It is a truffle requirement that the tree root extends the class of the
// {@link RootNode}. T
@NodeInfo(language="stg", description = "A root of an STG tree.")
class StgRootNode(
  language: Language, 
  frameDescriptor: FrameDescriptor, 
  val body: StgExpressionNode, // StgExprNode,
  val sourceSection: SourceSection,
  override val isCloningAllowed: Boolean
) extends RootNode(language, frameDescriptor) {
  override def execute(frame: VirtualFrame): Any = {
    assert (lookupContextReference[Context,Language](classOf[Language]).get != null)
    body.execute(frame)
  }
  override def getSourceSection = sourceSection
}
object StgRootNode {
  def apply(
    language: Language,
    frameDescriptor: FrameDescriptor,
    body: StgExpressionNode,
    sourceSection: SourceSection,
    isCloningAllowed: Boolean
  ): StgRootNode = new StgRootNode(language, frameDescriptor, body, sourceSection, isCloningAllowed) {}

  /*
  def unapply(node: StgRootNode): Option[(Language, FrameDescriptor, StgExpressionNode, SourceSection, Boolean)] = Some(
    ( node.getLanguage[Language](classOf[Language])
    , node.getFrameDescriptor
    , node.body
    , node.sourceSection
    , node.isCloningAllowed
    )
  )
  */

}