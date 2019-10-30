package org.intelligence.asm

// dsl-based java assembler as a simple wrapper around ObjectWeb's assembler

import jdk.internal.org.objectweb.asm.ClassWriter.COMPUTE_FRAMES
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.Type.*
import org.objectweb.asm.tree.*

fun assembleMethod(
  access: Modifier,
  name: String,
  returnType: Type,
  vararg parameterTypes: Type,
  signature: String? = null,
  exceptions: Array<Type>? = null,
  routine: MethodNode.() -> Unit
) = MethodNode(
  ASM7,
  access.access,
  name,
  getMethodDescriptor(returnType, *parameterTypes),
  signature,
  exceptions?.map { it.internalName }?.toTypedArray()
).also {
  routine(it)
}

fun ClassNode.method(
  access: Modifier,
  name: String,
  returnType: Type,
  vararg parameterTypes: Type,
  signature: String? = null,
  exceptions: Array<Type>? = null,
  routine: MethodNode.() -> Unit // use -> A and change return type?
) = assembleMethod(
  access,
  name,
  returnType,
  parameterTypes = *parameterTypes,
  signature = signature,
  exceptions = exceptions,
  routine = routine
).also {
  methods.add(it)
}

fun ClassNode.field(
  access: Modifier,
  type: Type,
  name: String,
  signature: String? = null,
  value: Any? = null
) = FieldNode(
  ASM7,
  access.access,
  name,
  type.descriptor,
  signature,
  value
).also {
  fields.add(it)
}

fun assembleClass(
  access: Modifier,
  name: String,
  version: Int = 49,
  superName: String = "java/lang/Object",
  routine: ClassNode.() -> Unit
)= ClassNode(ASM7).also {
  it.name = name
  it.version = version
  it.superName = superName
  it.access = access.access
  routine(it);
}

val ClassNode.assemble: ByteArray get() = ClassWriter(COMPUTE_FRAMES).also { this.accept(it) }.toByteArray()

fun `class`(
  access: Modifier,
  name: String,
  version: Int = 49,
  superName: String = "java/lang/Object",
  routine: ClassNode.() -> Unit
) = assembleClass(access,name,version,superName,routine).assemble