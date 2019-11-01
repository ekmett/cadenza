package cadenza.data

import org.intelligence.asm.*
import cadenza.panic
import com.oracle.truffle.api.frame.FrameSlotTypeException
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.Type
import java.lang.IndexOutOfBoundsException

// manufacture cadenza.data.IIO, OIO, etc.

typealias Slot = Int

interface DataFrame {
  fun getValue(slot: Slot): Any
  fun getSize() : Int

  @Throws(FrameSlotTypeException::class, NeutralException::class)
  fun getBoolean(slot: Slot): Boolean // B
  fun isBoolean(slot: Slot): Boolean

  @Throws(FrameSlotTypeException::class, NeutralException::class)
  fun getDouble(slot: Slot): Double // D
  fun isDouble(slot: Slot): Boolean

  @Throws(FrameSlotTypeException::class, NeutralException::class)
  fun getFloat(slot: Slot): Float // F
  fun isFloat(slot: Slot): Boolean

  @Throws(FrameSlotTypeException::class, NeutralException::class)
  fun getInteger(slot: Slot): Int // I
  fun isInteger(slot: Slot): Boolean

  @Throws(FrameSlotTypeException::class, NeutralException::class)
  fun getLong(slot: Slot): Long // L
  fun isLong(slot: Slot): Boolean

  @Throws(FrameSlotTypeException::class, NeutralException::class)
  fun getObject(slot: Slot): Any // O
  fun isObject(slot: Slot): Boolean
}

private sealed class FieldInfo(val sig: Char, val type: Type) {
  open val signature: String get() = sig.toString()
  abstract fun load(asm: Block, slot: Slot)
  abstract fun box(asm: Block)
  abstract fun ret(asm: Block)
  open val isInteger: Boolean get() = false
  open val isBoolean: Boolean get() = false
  open val isLong: Boolean get() = false
  open val isFloat: Boolean get() = false
  open val isDouble: Boolean get() = false
  open val isObject: Boolean get() = false
  companion object {
    fun of(c: Char) = when (c) {
      'B' -> booleanFieldInfo
      'D' -> doubleFieldInfo
      'F' -> floatFieldInfo
      'I' -> intFieldInfo
      'L' -> longFieldInfo
      'O' -> objectFieldInfo
      else -> panic("unknown field type")
    }
  }
}

private object booleanFieldInfo : FieldInfo('B', boolean) {
  override fun load(asm: Block, slot: Slot) = asm.iload(slot)
  override fun box(asm: Block) = asm.invokestatic(+Boolean::class, +Boolean::class, "valueOf", boolean)
  override fun ret(asm: Block) = asm.ireturn
  override val isBoolean: Boolean get() = true
}

private object doubleFieldInfo : FieldInfo('D', double) {
  override fun load(asm: Block, slot: Slot) = asm.fload(slot)
  override fun box(asm: Block) = asm.invokestatic(+Double::class, +Double::class, "valueOf", double)
  override fun ret(asm: Block) = asm.dreturn
  override val isDouble: Boolean get() = true
}

private object floatFieldInfo : FieldInfo('F', float) {
  override fun load(asm: Block, slot: Slot) = asm.fload(slot)
  override fun box(asm: Block) = asm.invokestatic(+Float::class, +Float::class, "valueOf", float)
  override fun ret(asm: Block) = asm.freturn
  override val isFloat: Boolean get() = true
}

private object intFieldInfo : FieldInfo('I', int) {
  override fun load(asm: Block, slot: Slot) = asm.iload(slot)
  override fun box(asm: Block) = asm.invokestatic(+Integer::class, +Integer::class, "valueOf", int)
  override fun ret(asm: Block) = asm.ireturn
  override val isInteger: Boolean get() = true
}

private object longFieldInfo : FieldInfo('L', long) {
  override fun load(asm: Block, slot: Slot) = asm.lload(slot)
  override fun box(asm: Block) = asm.invokestatic(+Long::class, +Long::class, "valueOf", long)
  override fun ret(asm: Block) = asm.lreturn
  override val isLong: Boolean get() = true
}

private object objectFieldInfo : FieldInfo('O', `object`) {
  override fun load(asm: Block, slot: Slot) = asm.aload(slot)
  override fun box(@Suppress("UNUSED_PARAMETER") asm: Block) {}
  override fun ret(asm: Block) = asm.areturn
  override val signature: String get() = "Ljava/lang/Object;"
  override val isObject: Boolean get() = true
}

// throw an exception type that has a default constructor
private fun assembleThrow(asm: Block, exceptionType: Type) = asm.run {
  new(exceptionType)
  dup
  invokespecial(exceptionType, void, "<init>")
  athrow
}

// we should also build a fallback version for holding neutrals as a subclass?
fun dataFrame(signature: String) : ByteArray = `class`(public,"cadenza/data/$signature") {
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
            lookupswitch(no, *types.withIndex().filter { predicate(it.value) }.mapIndexed { i, _ -> i to yes }.toTypedArray())
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
        val (good, bad) = types.withIndex().partition { predicate(it.value) }
        if (good.isNotEmpty()) {
          val defaultLabel = LabelNode()
          val labels = types.indices.map { LabelNode() }.toTypedArray()
          tableswitch(0,N-1,defaultLabel,*labels)
          good.forEach {
            add(labels[it.index])
            aload_0
            getfield(type, members[it.index], it.value.type)
            it.value.ret(this)
          }
          bad.forEach { add(labels[it.index]) }
          add(defaultLabel)
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
        types[i].load(this, i)
        putfield(type, members[i], types[i].type)
      }
    }
  }

  isMethod("isInteger") { it.isInteger }
  isMethod("isBoolean") { it.isBoolean }
  isMethod("isLong") { it.isLong }
  isMethod("isFloat") { it.isFloat }
  isMethod("isDouble") { it.isDouble }

  getMethod(boolean, "getBoolean") { it.isBoolean }
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