package cadenza.data // cadenza.aot?

import cadenza.jit.Code
import cadenza.panic
import cadenza.todo
import com.oracle.truffle.api.dsl.TypeSystemReference
import com.oracle.truffle.api.frame.FrameSlotTypeException
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.NodeCost
import com.oracle.truffle.api.nodes.NodeInfo
import org.intelligence.asm.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.LabelNode

// manufacture cadenza.data.frame.IIO, OIO, etc.

typealias Slot = Int

// an immutable dataframe modeling the different backing storage types allowed by the jvm

// these are all the distinctions the JVM cares about
abstract class DataFrame {
  abstract fun getValue(slot: Slot): Any?
  abstract fun getSize() : Int

  @Throws(FrameSlotTypeException::class)
  abstract fun getDouble(slot: Slot): Double // D
  abstract fun isDouble(slot: Slot): Boolean

  @Throws(FrameSlotTypeException::class)
  abstract fun getFloat(slot: Slot): Float // F
  abstract fun isFloat(slot: Slot): Boolean

  @Throws(FrameSlotTypeException::class)
  abstract fun getInteger(slot: Slot): Int // I
  abstract fun isInteger(slot: Slot): Boolean

  @Throws(FrameSlotTypeException::class)
  abstract fun getLong(slot: Slot): Long // L
  abstract fun isLong(slot: Slot): Boolean

  @Throws(FrameSlotTypeException::class)
  abstract fun getObject(slot: Slot): Any? // O
  abstract fun isObject(slot: Slot): Boolean
}

class SimpleDataFrame(vararg val data: Any?) {
  fun getValue(slot: Slot) = data[slot]
  fun getSize() = data.size
  @Throws(FrameSlotTypeException::class)
  fun getDouble(slot: Slot) = data[slot] as? Double ?: throw FrameSlotTypeException()
  fun isDouble(slot: Slot) = data[slot] is Double
  @Throws(FrameSlotTypeException::class)
  fun getFloat(slot: Slot) = data[slot] as? Float ?: throw FrameSlotTypeException()
  fun isFloat(slot: Slot) = data[slot] is Float
  @Throws(FrameSlotTypeException::class)
  fun getInteger(slot: Slot) = data[slot] as? Int ?: throw FrameSlotTypeException()
  fun isInteger(slot: Slot) = data[slot] is Int
  @Throws(FrameSlotTypeException::class)
  fun getLong(slot: Slot) = data[slot] as? Long ?: throw FrameSlotTypeException()
  @Throws(FrameSlotTypeException::class)
  fun getObject(slot: Slot) = data[slot]?.takeIf(::isSimpleObject) ?: throw FrameSlotTypeException()
  fun isObject(slot: Slot) = data[slot].let { it != null && isSimpleObject(it) }
  companion object {
    private fun isSimpleObject(it: Any): Boolean = it !is Double && it !is Long && it !is Float && it !is Int
  }
}

private sealed class FieldInfo(val sig: Char, val type: Type) {
  open val signature: String get() = sig.toString()
  abstract fun load(asm: Block, slot: Slot)
  abstract fun box(asm: Block)
  abstract fun ret(asm: Block)
  open val isInteger: Boolean get() = false
  open val isLong: Boolean get() = false
  open val isFloat: Boolean get() = false
  open val isDouble: Boolean get() = false
  open val isObject: Boolean get() = false
  companion object {
    fun of(c: Char) = when (c) {
      'I' -> intFieldInfo
      'F' -> floatFieldInfo
      'O' -> objectFieldInfo
      'L' -> longFieldInfo
      'D' -> doubleFieldInfo
      else -> panic("unknown field type")
    }
  }
}

private object intFieldInfo : FieldInfo('I', int) {
  override fun load(asm: Block, slot: Slot) = asm.iload(slot)
  override fun box(asm: Block) = asm.invokestatic(+Integer::class, +Integer::class, "valueOf", int)
  override fun ret(asm: Block) = asm.ireturn
  override val isInteger: Boolean get() = true
}

private object floatFieldInfo : FieldInfo('F', float) {
  override fun load(asm: Block, slot: Slot) = asm.fload(slot)
  override fun box(asm: Block) = asm.invokestatic(+Float::class, +Float::class, "valueOf", float)
  override fun ret(asm: Block) = asm.freturn
  override val isFloat: Boolean get() = true
}

private object objectFieldInfo : FieldInfo('O', `object`) {
  override fun load(asm: Block, slot: Slot) = asm.aload(slot)
  override fun box(@Suppress("UNUSED_PARAMETER") asm: Block) {}
  override fun ret(asm: Block) = asm.areturn
  override val signature: String get() = "Ljava/lang/Object;"
  override val isObject: Boolean get() = true
}

private object longFieldInfo : FieldInfo('L', long) {
  override fun load(asm: Block, slot: Slot) = asm.lload(slot)
  override fun box(asm: Block) = asm.invokestatic(+Long::class, +Long::class, "valueOf", long)
  override fun ret(asm: Block) = asm.lreturn
  override val isLong: Boolean get() = true
}


private object doubleFieldInfo : FieldInfo('D', double) {
  override fun load(asm: Block, slot: Slot) = asm.fload(slot)
  override fun box(asm: Block) = asm.invokestatic(+Double::class, +Double::class, "valueOf", double)
  override fun ret(asm: Block) = asm.dreturn
  override val isDouble: Boolean get() = true
}

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

@TypeSystemReference(DataTypes::class)
@NodeInfo(shortName = "DataFrameBuilder")
abstract class DataFrameBuilder : Node() {
  abstract fun execute(frame: VirtualFrame)
}

fun builder(signature: String) : ByteArray = `class`(
  public and final and `super`,"cadenza/data/frame/${signature}_Builder", superName = code.internalName
) {
  val types = signature.map { FieldInfo.of(it) }.toTypedArray()
  val members = types.indices.map { "_$it" }.toTypedArray()

  visibleAnnotations = listOf(nodeInfo(shortName="${signature}_Builder"))

  members.forEach {
    field(public,code,it).apply { visibleAnnotations = listOf(child) }
  }

  constructor(public,*types.map { it.type }.toTypedArray()) {
    asm {
      members.forEachIndexed { i, member ->
        aload_0
        aload(i+1)
        putfield(type,member,code)
      }
      `return`
    }
  }
  method(public and final, `object`, "execute", +VirtualFrame::class) {
    todo
  }
}