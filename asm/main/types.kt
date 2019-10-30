package org.intelligence.asm

import org.objectweb.asm.Type
import kotlin.reflect.KClass

val void: Type get() = Type.VOID_TYPE
val char: Type get() = Type.CHAR_TYPE
val byte: Type get() = Type.BYTE_TYPE
val int: Type get() = Type.INT_TYPE
val float: Type get() = Type.FLOAT_TYPE
val long: Type get() = Type.LONG_TYPE
val double: Type get() = Type.DOUBLE_TYPE
val boolean: Type get() = Type.BOOLEAN_TYPE
fun type(k : KClass<*>): Type = Type.getType(k.java)
fun type(t : String): Type = Type.getObjectType(t)