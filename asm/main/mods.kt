package org.intelligence.asm

import org.objectweb.asm.Opcodes

// access modifiers, if inline classes break or get removed, make this a data class
inline class Mod(val access: Int) {
  infix fun and(other: Mod) = Mod(this.access or other.access)
}

val public get() = Mod(Opcodes.ACC_PUBLIC)
val private get() = Mod(Opcodes.ACC_PRIVATE)
val static get() = Mod(Opcodes.ACC_STATIC)
val abstract get() = Mod(Opcodes.ACC_ABSTRACT)
val module get() = Mod(Opcodes.ACC_MODULE)
val none get() = Mod(0)
val open get() = Mod(Opcodes.ACC_OPEN)
val `interface` get() = Mod(Opcodes.ACC_INTERFACE)
val enum get() = Mod(Opcodes.ACC_ENUM)

