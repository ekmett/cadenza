package org.intelligence.asm

import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*

fun <A> MethodNode.asm(routine: InsnList.() -> A): A = routine(instructions)

val InsnList.swap: Unit get() = add(InsnNode(SWAP))
val InsnList.nop: Unit get() = add(InsnNode(NOP))
// math
val InsnList.iadd: Unit get() = add(InsnNode(IADD))
val InsnList.ladd: Unit get() = add(InsnNode(LADD))
val InsnList.fadd: Unit get() = add(InsnNode(FADD))
val InsnList.dadd: Unit get() = add(InsnNode(DADD))
val InsnList.isub: Unit get() = add(InsnNode(ISUB))
val InsnList.lsub: Unit get() = add(InsnNode(LSUB))
val InsnList.fsub: Unit get() = add(InsnNode(FSUB))
val InsnList.dsub: Unit get() = add(InsnNode(DSUB))
val InsnList.imul: Unit get() = add(InsnNode(IMUL))
val InsnList.lmul: Unit get() = add(InsnNode(LMUL))
val InsnList.fmul: Unit get() = add(InsnNode(FMUL))
val InsnList.dmul: Unit get() = add(InsnNode(DMUL))
val InsnList.idiv: Unit get() = add(InsnNode(IDIV))
val InsnList.ldiv: Unit get() = add(InsnNode(LDIV))
val InsnList.fdiv: Unit get() = add(InsnNode(FDIV))
val InsnList.ddiv: Unit get() = add(InsnNode(DDIV))
val InsnList.irem: Unit get() = add(InsnNode(IREM))
val InsnList.lrem: Unit get() = add(InsnNode(LREM))
val InsnList.frem: Unit get() = add(InsnNode(FREM))
val InsnList.drem: Unit get() = add(InsnNode(DREM))
val InsnList.ineg: Unit get() = add(InsnNode(INEG))
val InsnList.lneg: Unit get() = add(InsnNode(LNEG))
val InsnList.fneg: Unit get() = add(InsnNode(FNEG))
val InsnList.dneg: Unit get() = add(InsnNode(DNEG))
val InsnList.ishl: Unit get() = add(InsnNode(ISHL))
val InsnList.lshl: Unit get() = add(InsnNode(LSHL))
val InsnList.ishr: Unit get() = add(InsnNode(ISHR))
val InsnList.lshr: Unit get() = add(InsnNode(LSHR))
val InsnList.iushr: Unit get() = add(InsnNode(IUSHR))
val InsnList.lushr: Unit get() = add(InsnNode(LUSHR))
val InsnList.iand: Unit get() = add(InsnNode(IAND))
val InsnList.land: Unit get() = add(InsnNode(LAND))
val InsnList.ior: Unit get() = add(InsnNode(IOR))
val InsnList.lor: Unit get() = add(InsnNode(LOR))
val InsnList.ixor: Unit get() = add(InsnNode(IXOR))
val InsnList.lxor: Unit get() = add(InsnNode(LXOR))
fun InsnList.iinc(slot: Int) = add(IincInsnNode(slot, 1))
fun InsnList.iinc(slot: Int, amount: Int) = add(IincInsnNode(slot, amount))
val InsnList.i2l: Unit get() = add(InsnNode(I2L))
val InsnList.i2f: Unit get() = add(InsnNode(I2F))
val InsnList.i2d: Unit get() = add(InsnNode(I2D))
val InsnList.l2i: Unit get() = add(InsnNode(L2I))
val InsnList.l2f: Unit get() = add(InsnNode(L2F))
val InsnList.l2d: Unit get() = add(InsnNode(L2D))
val InsnList.f2i: Unit get() = add(InsnNode(F2I))
val InsnList.f2l: Unit get() = add(InsnNode(F2L))
val InsnList.f2d: Unit get() = add(InsnNode(F2D))
val InsnList.d2i: Unit get() = add(InsnNode(D2I))
val InsnList.d2l: Unit get() = add(InsnNode(D2L))
val InsnList.d2f: Unit get() = add(InsnNode(D2F))
val InsnList.i2b: Unit get() = add(InsnNode(I2B))
val InsnList.i2c: Unit get() = add(InsnNode(I2C))
val InsnList.i2s: Unit get() = add(InsnNode(I2S))
val InsnList.iaload: Unit get() = add(InsnNode(IALOAD))
val InsnList.laload: Unit get() = add(InsnNode(LALOAD))
val InsnList.faload: Unit get() = add(InsnNode(FALOAD))
val InsnList.daload: Unit get() = add(InsnNode(DALOAD))
val InsnList.aaload: Unit get() = add(InsnNode(AALOAD))
val InsnList.baload: Unit get() = add(InsnNode(BALOAD))
val InsnList.caload: Unit get() = add(InsnNode(CALOAD))
val InsnList.saload: Unit get() = add(InsnNode(SALOAD))
val InsnList.iastore: Unit get() = add(InsnNode(IASTORE))
val InsnList.lastore: Unit get() = add(InsnNode(LASTORE))
val InsnList.fastore: Unit get() = add(InsnNode(FASTORE))
val InsnList.dastore: Unit get() = add(InsnNode(DASTORE))
val InsnList.aastore: Unit get() = add(InsnNode(AASTORE))
val InsnList.bastore: Unit get() = add(InsnNode(BASTORE))
val InsnList.castore: Unit get() = add(InsnNode(CASTORE))
val InsnList.sastore: Unit get() = add(InsnNode(SASTORE))
val InsnList.arraylength: Unit get() = add(InsnNode(ARRAYLENGTH))
fun InsnList.anewarray(type: Type) = add(TypeInsnNode(ANEWARRAY, type.internalName))
fun InsnList.multianewarray(type: Type, dimensions: Int) = add(MultiANewArrayInsnNode(type.descriptor, dimensions))
fun InsnList.newarray(type: Type) {
  add(IntInsnNode(NEWARRAY, when (type.sort) {
    Type.BOOLEAN -> T_BOOLEAN
    Type.CHAR -> T_CHAR
    Type.BYTE -> T_BYTE
    Type.SHORT -> T_SHORT
    Type.INT -> T_INT
    Type.FLOAT -> T_FLOAT
    Type.LONG -> T_LONG
    Type.DOUBLE -> T_DOUBLE
    else -> error("Invalid type for primitive array creation")
  }))
}

// fields
fun InsnList.getstatic(owner: Type, name: String, type: Type) = add(FieldInsnNode(GETSTATIC, owner.internalName, name, type.descriptor))
fun InsnList.getfield(owner: Type, name: String, type: Type) = add(FieldInsnNode(GETFIELD, owner.internalName, name, type.descriptor))
fun InsnList.putstatic(owner: Type, name: String, type: Type) = add(FieldInsnNode(PUTSTATIC, owner.internalName, name, type.descriptor))
fun InsnList.putfield(owner: Type, name: String, type: Type) = add(FieldInsnNode(PUTFIELD, owner.internalName, name, type.descriptor))

// object management
fun InsnList.new(type: Type) = add(TypeInsnNode(NEW, type.internalName))
fun InsnList.checkcast(type: Type) = add(TypeInsnNode(CHECKCAST, type.internalName))
fun InsnList.instanceof(type: Type) = add(TypeInsnNode(INSTANCEOF, type.internalName))

// stack
val InsnList.pop: Unit get() = add(InsnNode(POP))
val InsnList.pop2: Unit get() = add(InsnNode(POP2))
val InsnList.dup: Unit get() = add(InsnNode(DUP))
val InsnList.dup_x1: Unit get() = add(InsnNode(DUP_X1))
val InsnList.dup_x2: Unit get() = add(InsnNode(DUP_X2))
val InsnList.dup2: Unit get() = add(InsnNode(DUP2))
val InsnList.dup2_x1: Unit get() = add(InsnNode(DUP2_X1))
val InsnList.dup2_x2: Unit get() = add(InsnNode(DUP2_X2))

fun InsnList.tableSwitch(min: Int, max: Int, defaultLabel: LabelNode, vararg labels: LabelNode) =
  add(TableSwitchInsnNode(min, max, defaultLabel, *labels))

fun InsnList.lookupSwitch(defaultLabel: LabelNode, vararg branches: Pair<Int, LabelNode>) =
  add(LookupSwitchInsnNode(defaultLabel,
    IntArray(branches.size) { branches[it].first },
    Array(branches.size) { branches[it].second }))

val InsnList.aconst_null: Unit get() = add(InsnNode(ACONST_NULL))
val InsnList.iconst_m1: Unit get() = add(InsnNode(ICONST_M1))
val InsnList.iconst_0: Unit get() = add(InsnNode(ICONST_0))
val InsnList.iconst_1: Unit get() = add(InsnNode(ICONST_1))
val InsnList.iconst_2: Unit get() = add(InsnNode(ICONST_2))
val InsnList.iconst_3: Unit get() = add(InsnNode(ICONST_3))
val InsnList.iconst_4: Unit get() = add(InsnNode(ICONST_4))
val InsnList.iconst_5: Unit get() = add(InsnNode(ICONST_5))
val InsnList.lconst_0: Unit get() = add(InsnNode(LCONST_0))
val InsnList.lconst_1: Unit get() = add(InsnNode(LCONST_1))
val InsnList.fconst_0: Unit get() = add(InsnNode(FCONST_0))
val InsnList.fconst_1: Unit get() = add(InsnNode(FCONST_1))
val InsnList.fconst_2: Unit get() = add(InsnNode(FCONST_2))
val InsnList.dconst_0: Unit get() = add(InsnNode(DCONST_0))
val InsnList.dconst_1: Unit get() = add(InsnNode(DCONST_1))
fun InsnList.bipush(v: Int) = add(IntInsnNode(BIPUSH, v))
fun InsnList.sipush(v: Int) = add(IntInsnNode(SIPUSH, v))
fun InsnList.ldc(v: Any) = add(LdcInsnNode(v))

fun InsnList.invokevirtual(owner: Type, name: String, returnType: Type, vararg parameterTypes: Type) =
  add(MethodInsnNode(INVOKEVIRTUAL, owner.internalName, name, Type.getMethodDescriptor(returnType, *parameterTypes)))
fun InsnList.invokespecial(owner: Type, name: String, returnType: Type, vararg parameterTypes: Type) =
  add(MethodInsnNode(INVOKESPECIAL, owner.internalName, name, Type.getMethodDescriptor(returnType, *parameterTypes)))
fun InsnList.invokestatic(owner: Type, name: String, returnType: Type, vararg parameterTypes: Type) =
  add(MethodInsnNode(INVOKESTATIC, owner.internalName, name, Type.getMethodDescriptor(returnType, *parameterTypes)))
fun InsnList.invokeinterface(owner: Type, name: String, returnType: Type, vararg parameterTypes: Type) =
  add(MethodInsnNode(INVOKEINTERFACE, owner.internalName, name, Type.getMethodDescriptor(returnType, *parameterTypes)))

val InsnList.ireturn: Unit get() = add(InsnNode(IRETURN))
val InsnList.lreturn: Unit get() = add(InsnNode(LRETURN))
val InsnList.freturn: Unit get() = add(InsnNode(FRETURN))
val InsnList.dreturn: Unit get() = add(InsnNode(DRETURN))
val InsnList.areturn: Unit get() = add(InsnNode(ARETURN))
val InsnList.`return`: Unit get() = add(InsnNode(RETURN))
val InsnList.lcmp: Unit get() = add(InsnNode(LCMP))
val InsnList.fcmpl: Unit get() = add(InsnNode(FCMPL))
val InsnList.fcmpg: Unit get() = add(InsnNode(FCMPG))
val InsnList.dcmpl: Unit get() = add(InsnNode(DCMPL))
val InsnList.dcmpg: Unit get() = add(InsnNode(DCMPG))
fun InsnList.ifeq(label: LabelNode) = add(JumpInsnNode(IFEQ, label))
fun InsnList.ifne(label: LabelNode) = add(JumpInsnNode(IFNE, label))
fun InsnList.iflt(label: LabelNode) = add(JumpInsnNode(IFLT, label))
fun InsnList.ifge(label: LabelNode) = add(JumpInsnNode(IFGE, label))
fun InsnList.ifgt(label: LabelNode) = add(JumpInsnNode(IFGT, label))
fun InsnList.ifle(label: LabelNode) = add(JumpInsnNode(IFLE, label))
fun InsnList.if_icmpeq(label: LabelNode) = add(JumpInsnNode(IF_ICMPEQ, label))
fun InsnList.if_icmpne(label: LabelNode) = add(JumpInsnNode(IF_ICMPNE, label))
fun InsnList.if_icmplt(label: LabelNode) = add(JumpInsnNode(IF_ICMPLT, label))
fun InsnList.if_icmpge(label: LabelNode) = add(JumpInsnNode(IF_ICMPGE, label))
fun InsnList.if_icmpgt(label: LabelNode) = add(JumpInsnNode(IF_ICMPGT, label))
fun InsnList.if_icmple(label: LabelNode) = add(JumpInsnNode(IF_ICMPLE, label))
fun InsnList.if_acmpeq(label: LabelNode) = add(JumpInsnNode(IF_ACMPEQ, label))
fun InsnList.if_acmpne(label: LabelNode) = add(JumpInsnNode(IF_ACMPNE, label))
fun InsnList.goto(label: LabelNode) = add(JumpInsnNode(GOTO, label))
fun InsnList.ifnull(label: LabelNode) = add(JumpInsnNode(IFNULL, label))
fun InsnList.ifnonnull(label: LabelNode) = add(JumpInsnNode(IFNONNULL, label))
fun InsnList.jsr(label: LabelNode) = add(JumpInsnNode(JSR, label))
fun InsnList.ret(slot: Int) = add(VarInsnNode(RET, slot))
val InsnList.athrow: Unit get() = add(InsnNode(ATHROW))
