package cadenza.frame

import cadenza.panic
import org.intelligence.asm.*
import org.objectweb.asm.Type

internal sealed class FieldInfo(val sig: Char, val type: Type) {
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
    fun from(x: Any): FieldInfo = when (x) {
      is Int -> intFieldInfo
      is Float -> floatFieldInfo
      is Long -> longFieldInfo
      is Double -> doubleFieldInfo
      else -> objectFieldInfo
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