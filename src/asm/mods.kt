package cadenza.asm

import org.objectweb.asm.Opcodes.*

// access modifiers, if inline classes break or get removed, make this a data class
inline class Mod(val access: Int) {
  infix fun and(other: Mod) = Mod(this.access or other.access)
}

val public get() = Mod(ACC_PUBLIC)
val private get() = Mod(ACC_PRIVATE)
val static get() = Mod(ACC_STATIC)
val abstract get() = Mod(ACC_ABSTRACT)
val module get() = Mod(ACC_MODULE)
val none get() = Mod(0)
val open get() = Mod(ACC_OPEN)
val `interface` get() = Mod(ACC_INTERFACE)
val enum get() = Mod(ACC_ENUM)
val final get() = Mod(ACC_FINAL)