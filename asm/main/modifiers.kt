package org.intelligence.asm

import org.objectweb.asm.Opcodes

// access modifiers, if inline classes break or get removed, make this a data class
inline class Modifier(val access: Int) {
  infix fun and(other: Modifier) = Modifier(this.access or other.access)
}

val public get() = Modifier(Opcodes.ACC_PUBLIC)
val private get() = Modifier(Opcodes.ACC_PRIVATE)
val static get() = Modifier(Opcodes.ACC_STATIC)
val abstract get() = Modifier(Opcodes.ACC_ABSTRACT)
val module get() = Modifier(Opcodes.ACC_MODULE)
val none get() = Modifier(0)
val open get() = Modifier(Opcodes.ACC_OPEN)
val `interface` get() = Modifier(Opcodes.ACC_INTERFACE)
val enum get() = Modifier(Opcodes.ACC_ENUM)

