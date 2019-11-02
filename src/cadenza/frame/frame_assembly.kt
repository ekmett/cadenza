package cadenza.frame

// cadenza.aot?

import cadenza.jit.Code
import cadenza.todo
import com.oracle.truffle.api.frame.FrameSlotTypeException
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.NodeCost
import com.oracle.truffle.api.nodes.NodeInfo
import org.intelligence.asm.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.LabelNode

// manufacture cadenza.frame.frame.IIO, OIO, etc.

// an immutable dataframe modeling the different backing storage types allowed by the jvm

// throw an exception type that has a default constructor
private fun assembleThrow(asm: Block, exceptionType: Type) = asm.run {
  new(exceptionType)
  dup
  invokespecial(exceptionType, void, "<init>")
  athrow
}

// we should also build a fallback version for holding neutrals as a subclass?
fun frame(signature: String) : ByteArray = `class`(public,"cadenza/data/frame/$signature") {
  interfaces = mutableListOf(type(DataFrame::class).internalName)
  val types = signature.map { FieldInfo.of(it) }.toTypedArray()
  val N = types.size
  val members = types.indices.map { "_$it" }.toTypedArray()

  fun isMethod(name: String, predicate: (FieldInfo) -> Boolean) {
    method(public and final, boolean, name, +Slot::class) {
      asm {
        when {
          types.all(predicate) -> { iconst_1; ireturn }
          !types.any(predicate) -> { iconst_0; ireturn }
          else -> {
            val no = LabelNode()
            val yes = LabelNode()
            lookupswitch(no, *types.mapIndexedNotNull { i, v -> if (predicate(v)) i to yes else null }.toTypedArray())
            add(no)
            iconst_0
            ireturn
            add(yes)
            iconst_1
            ireturn
          }
        }
      }
    }
  }

  fun getMethod(resultType: Type, name: String, predicate: (FieldInfo) -> Boolean) {
    method(public and final, resultType, name, +Slot::class) {
      throws(+FrameSlotTypeException::class)
      asm {
        types.mapIndexedNotNull { i, v -> if(predicate(v)) i to LabelNode() else null }.toTypedArray().let {
          if (it.isNotEmpty()) {
            val defaultLabel = LabelNode()
            lookupswitch(defaultLabel, *it)
            it.forEach { (i, label) ->
              add(label)
              aload_0
              val t = types[i]
              getfield(type, members[i], t.type)
              t.ret(this)
            }
            add(defaultLabel)
          }
        }
        assembleThrow(this, +FrameSlotTypeException::class)
      }
    }
  }

  for (i in types.indices)
    field(public and final,types[i].type,members[i])

  constructor(public and final, parameterTypes = *types.map{it.type}.toTypedArray()) {
    asm {
      for (i in types.indices) {
        types[i].load(this, i+1)
        putfield(type, members[i], types[i].type)
      }
    }
  }

  isMethod("isInteger") { it.isInteger }
  isMethod("isLong") { it.isLong }
  isMethod("isFloat") { it.isFloat }
  isMethod("isDouble") { it.isDouble }

  getMethod(int, "getInteger") { it.isInteger }
  getMethod(long, "getLong") { it.isLong }
  getMethod(float, "getFloat") { it.isFloat }
  getMethod(double, "getDouble") { it.isDouble }
  getMethod(`object`, "getObject") { it.isObject }

  method(public and final, `object`, "getValue", +Slot::class) {
    asm {
      val defaultLabel = LabelNode()
      val labels = members.map { LabelNode() }.toTypedArray()
      iload_1
      tableswitch(0,N-1,defaultLabel,*labels)
      for (i in labels.indices) {
        add(labels[i])
        aload_0
        getfield(type,members[i],types[i].type)
        types[i].box(this)
        areturn
      }
      add(defaultLabel)
      assembleThrow(this, +IndexOutOfBoundsException::class)
    }
  }

  // purely for debugging
  method(public and final, int, "getSize") {
    asm {
      push(types.size)
      ireturn
    }
  }
}

fun nodeInfo(
  shortName: String = "",
  cost: NodeCost = NodeCost.MONOMORPHIC,
  description: String = "",
  language: String = ""
)= annotationNode(+NodeInfo::class, shortName, cost, description, language)

val child: AnnotationNode get() = annotationNode(+Node.Child::class)

val code = +Code::class

fun builder(signature: String) : ByteArray = `class`(
  public and final and `super`,"cadenza/jit/${signature}_Builder", superName = code.internalName
) {
  val types = signature.map { FieldInfo.of(it) }.toTypedArray()
  val members = types.indices.map { "_$it" }.toTypedArray()

  visibleAnnotations = listOf(nodeInfo(shortName = "${signature}_Builder"))

  members.forEach {
    field(public, code,it).apply { visibleAnnotations = listOf(child) }
  }

  constructor(public,*types.map { it.type }.toTypedArray()) {
    asm {
      members.forEachIndexed { i, member ->
        aload_0
        aload(i+1)
        putfield(type,member, code)
      }
      `return`
    }
  }
  method(public and final, `object`, "execute", +VirtualFrame::class) {
    todo
  }
}