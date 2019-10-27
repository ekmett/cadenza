package cadenza.asm

// dsl-based java assembler

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.Type.*
import org.objectweb.asm.tree.*
import kotlin.reflect.KClass

// access modifiers

inline class Modifier(val access: Int) {
  infix fun or(other: Modifier) = Modifier(this.access or other.access)
}

interface ModifierAssembly {
  val public get() = Modifier(ACC_PUBLIC)
  val private get() = Modifier(ACC_PRIVATE)
  val static get() = Modifier(ACC_STATIC)
  val abstract get() = Modifier(ACC_ABSTRACT)
  val module get() = Modifier(ACC_MODULE)
  val none get() = Modifier(0)
  val open get() = Modifier(ACC_OPEN)
  val `interface` get() = Modifier(ACC_INTERFACE)
  val enum get() = Modifier(ACC_ENUM)
}

// java types

interface TypeAssembly {
  val void: Type get() = VOID_TYPE
  val char: Type get() = CHAR_TYPE
  val byte: Type get() = BYTE_TYPE
  val int: Type get() = INT_TYPE
  val float: Type get() = FLOAT_TYPE
  val long: Type get() = LONG_TYPE
  val double: Type get() = DOUBLE_TYPE
  val boolean: Type get() = BOOLEAN_TYPE
  fun type(k : KClass<*>): Type = getType(k.java)
  fun type(t : String): Type = getObjectType(t)
}

// allows us to emit assembly instructions in a straight line
interface InstructionAssembly : TypeAssembly, ModifierAssembly {
  val instructions: InsnList
}

// start to deal with labels

interface HasLabelNode {
  val labelNode: LabelNode
}

interface HasTryCatchBlocks {
  val tryCatchBlocks: MutableList<TryCatchBlockNode>
}

fun LabelNode.wrap() : HasLabelNode {
  val labelNode = this
  return object : HasLabelNode {
    override val labelNode = labelNode
  }
}

class AssemblyLabel(private val instructions: InsnList, override val labelNode: LabelNode) : HasLabelNode {
  //operator fun unaryPlus { instructions.add(labelNode) }
}

class LabelRegistry(private val instructions: InsnList) {
  private var labels = mutableMapOf<String, LabelNode>()
  fun copy(instructions: InsnList) =  LabelRegistry(instructions).also { it.labels = this.labels }
  fun scope(instructions: InsnList) = LabelRegistry(instructions).also { it.labels.putAll(this.labels) }
  //operator fun get(index: Int) = this["label_$index"]
  //operator fun get(name: String) = AssemblyLabel(instructions, labels.getOrPut(name, LabelNode))
}

interface LabelScope { val L: LabelRegistry } // HasLabelRegistry?
interface LabelledAssembly : InstructionAssembly, LabelScope

interface Assembly : LabelledAssembly, HasTryCatchBlocks

fun Assembly.scope(routine: LabelScope.() -> Unit) {
  val self = this;
  routine(object : LabelScope {
    override val L = self.L.scope(instructions);
  })
}

fun Assembly.mergeFrom(asm: Assembly, label: HasLabelNode) {
  instructions.insert(label.labelNode, asm.instructions)
  tryCatchBlocks.addAll(asm.tryCatchBlocks)
}

fun assembleBlock(
  routine: Assembly.() -> Unit
): Pair<InsnList, List<TryCatchBlockNode>> {
  val instructions = InsnList()
  val tryCatchBlocks = mutableListOf<TryCatchBlockNode>()
  routine(object : Assembly {
    override val tryCatchBlocks = tryCatchBlocks
    override val instructions = instructions
    override val L = LabelRegistry(instructions)
  })

  return Pair(instructions, tryCatchBlocks)
}

abstract class AbstractAssembly(
  override val instructions: InsnList,
  override val tryCatchBlocks: MutableList<TryCatchBlockNode>
) : Assembly

class MethodAssemblyContext(node: MethodNode) : AbstractAssembly(node.instructions, node.tryCatchBlocks) {
  override val L = LabelRegistry(instructions)
}

fun assembleMethod(
  access: Modifier,
  name: String,
  returnType: Type,
  vararg parameterTypes: Type,
  signature: String? = null,
  exceptions: Array<Type>? = null,
  routine: MethodAssemblyContext.() -> Unit
) = MethodNode(
  Opcodes.ASM7,
  access.access,
  name,
  Type.getMethodDescriptor(returnType, *parameterTypes),
  signature,
  exceptions?.map { it.internalName }?.toTypedArray()
).also {
  routine(MethodAssemblyContext(it))
}

// class assembly

class ClassAssemblyContext : TypeAssembly, ModifierAssembly {
  val node: ClassNode = ClassNode(ASM7).also {
    it.version = 49
    it.superName = "java/lang/Object"
  }

  var access: Modifier
    get() = Modifier(node.access)
    set(value) { node.access = value.access }

  var name: String
    get() = node.name
    set(value) { node.name = value }

  var version: Int
    get() = node.version
    set(value)  { node.version = value }

  var superClass: Type
    get() = type(node.superName)
    set(value) { node.superName = value.internalName }

  val interfaces: MutableList<String>
    get() = node.interfaces

  val self get() = type(name)

  fun field(access: Modifier, type: Type, name: String, signature: String? = null, value: Any? = null) =
    FieldNode(ASM7,access.access, name, type.descriptor, signature, value).also { node.fields.add(it) }

  fun method(
    access: Modifier,
    name: String,
    returnType: Type,
    vararg argumentTypes: Type,
    signature: String? = null,
    exceptions: Array<Type>? = null,
    routine: MethodAssemblyContext.() -> Unit
  ) = MethodNode(
    ASM7,
    access.access,
    name,
    Type.getMethodDescriptor(returnType, *argumentTypes),
    signature,
    exceptions?.map { it.internalName }?.toTypedArray()
  ).also {
    routine(MethodAssemblyContext(it))
    node.methods.add(it)
  }
}

val InstructionAssembly.nop: Unit get() = instructions.add(InsnNode(NOP))
// math
val InstructionAssembly.iadd: Unit get() = instructions.add(InsnNode(IADD))
val InstructionAssembly.ladd: Unit get() = instructions.add(InsnNode(LADD))
val InstructionAssembly.fadd: Unit get() = instructions.add(InsnNode(FADD))
val InstructionAssembly.dadd: Unit get() = instructions.add(InsnNode(DADD))
val InstructionAssembly.isub: Unit get() = instructions.add(InsnNode(ISUB))
val InstructionAssembly.lsub: Unit get() = instructions.add(InsnNode(LSUB))
val InstructionAssembly.fsub: Unit get() = instructions.add(InsnNode(FSUB))
val InstructionAssembly.dsub: Unit get() = instructions.add(InsnNode(DSUB))
val InstructionAssembly.imul: Unit get() = instructions.add(InsnNode(IMUL))
val InstructionAssembly.lmul: Unit get() = instructions.add(InsnNode(LMUL))
val InstructionAssembly.fmul: Unit get() = instructions.add(InsnNode(FMUL))
val InstructionAssembly.dmul: Unit get() = instructions.add(InsnNode(DMUL))
val InstructionAssembly.idiv: Unit get() = instructions.add(InsnNode(IDIV))
val InstructionAssembly.ldiv: Unit get() = instructions.add(InsnNode(LDIV))
val InstructionAssembly.fdiv: Unit get() = instructions.add(InsnNode(FDIV))
val InstructionAssembly.ddiv: Unit get() = instructions.add(InsnNode(DDIV))
val InstructionAssembly.irem: Unit get() = instructions.add(InsnNode(IREM))
val InstructionAssembly.lrem: Unit get() = instructions.add(InsnNode(LREM))
val InstructionAssembly.frem: Unit get() = instructions.add(InsnNode(FREM))
val InstructionAssembly.drem: Unit get() = instructions.add(InsnNode(DREM))
val InstructionAssembly.ineg: Unit get() = instructions.add(InsnNode(INEG))
val InstructionAssembly.lneg: Unit get() = instructions.add(InsnNode(LNEG))
val InstructionAssembly.fneg: Unit get() = instructions.add(InsnNode(FNEG))
val InstructionAssembly.dneg: Unit get() = instructions.add(InsnNode(DNEG))
val InstructionAssembly.ishl: Unit get() = instructions.add(InsnNode(ISHL))
val InstructionAssembly.lshl: Unit get() = instructions.add(InsnNode(LSHL))
val InstructionAssembly.ishr: Unit get() = instructions.add(InsnNode(ISHR))
val InstructionAssembly.lshr: Unit get() = instructions.add(InsnNode(LSHR))
val InstructionAssembly.iushr: Unit get() = instructions.add(InsnNode(IUSHR))
val InstructionAssembly.lushr: Unit get() = instructions.add(InsnNode(LUSHR))
val InstructionAssembly.iand: Unit get() = instructions.add(InsnNode(IAND))
val InstructionAssembly.land: Unit get() = instructions.add(InsnNode(LAND))
val InstructionAssembly.ior: Unit get() = instructions.add(InsnNode(IOR))
val InstructionAssembly.lor: Unit get() = instructions.add(InsnNode(LOR))
val InstructionAssembly.ixor: Unit get() = instructions.add(InsnNode(IXOR))
val InstructionAssembly.lxor: Unit get() = instructions.add(InsnNode(LXOR))
fun InstructionAssembly.iinc(slot: Int) = instructions.add(IincInsnNode(slot, 1))
fun InstructionAssembly.iinc(slot: Int, amount: Int) = instructions.add(IincInsnNode(slot, amount))
val InstructionAssembly.i2l: Unit get() = instructions.add(InsnNode(I2L))
val InstructionAssembly.i2f: Unit get() = instructions.add(InsnNode(I2F))
val InstructionAssembly.i2d: Unit get() = instructions.add(InsnNode(I2D))
val InstructionAssembly.l2i: Unit get() = instructions.add(InsnNode(L2I))
val InstructionAssembly.l2f: Unit get() = instructions.add(InsnNode(L2F))
val InstructionAssembly.l2d: Unit get() = instructions.add(InsnNode(L2D))
val InstructionAssembly.f2i: Unit get() = instructions.add(InsnNode(F2I))
val InstructionAssembly.f2l: Unit get() = instructions.add(InsnNode(F2L))
val InstructionAssembly.f2d: Unit get() = instructions.add(InsnNode(F2D))
val InstructionAssembly.d2i: Unit get() = instructions.add(InsnNode(D2I))
val InstructionAssembly.d2l: Unit get() = instructions.add(InsnNode(D2L))
val InstructionAssembly.d2f: Unit get() = instructions.add(InsnNode(D2F))
val InstructionAssembly.i2b: Unit get() = instructions.add(InsnNode(I2B))
val InstructionAssembly.i2c: Unit get() = instructions.add(InsnNode(I2C))
val InstructionAssembly.i2s: Unit get() = instructions.add(InsnNode(I2S))

// array ops
val InstructionAssembly.iaload: Unit get() = instructions.add(InsnNode(IALOAD))
val InstructionAssembly.laload: Unit get() = instructions.add(InsnNode(LALOAD))
val InstructionAssembly.faload: Unit get() = instructions.add(InsnNode(FALOAD))
val InstructionAssembly.daload: Unit get() = instructions.add(InsnNode(DALOAD))
val InstructionAssembly.aaload: Unit get() = instructions.add(InsnNode(AALOAD))
val InstructionAssembly.baload: Unit get() = instructions.add(InsnNode(BALOAD))
val InstructionAssembly.caload: Unit get() = instructions.add(InsnNode(CALOAD))
val InstructionAssembly.saload: Unit get() = instructions.add(InsnNode(SALOAD))
val InstructionAssembly.iastore: Unit get() = instructions.add(InsnNode(IASTORE))
val InstructionAssembly.lastore: Unit get() = instructions.add(InsnNode(LASTORE))
val InstructionAssembly.fastore: Unit get() = instructions.add(InsnNode(FASTORE))
val InstructionAssembly.dastore: Unit get() = instructions.add(InsnNode(DASTORE))
val InstructionAssembly.aastore: Unit get() = instructions.add(InsnNode(AASTORE))
val InstructionAssembly.bastore: Unit get() = instructions.add(InsnNode(BASTORE))
val InstructionAssembly.castore: Unit get() = instructions.add(InsnNode(CASTORE))
val InstructionAssembly.sastore: Unit get() = instructions.add(InsnNode(SASTORE))
val InstructionAssembly.arraylength: Unit get() = instructions.add(InsnNode(ARRAYLENGTH))
fun InstructionAssembly.anewarray(type: Type) = instructions.add(TypeInsnNode(ANEWARRAY, type.internalName))
fun InstructionAssembly.multianewarray(type: Type, dimensions: Int) = instructions.add(MultiANewArrayInsnNode(type.descriptor, dimensions))
fun InstructionAssembly.newarray(type: Type) {
  instructions.add(IntInsnNode(NEWARRAY, when (type.sort) {
    BOOLEAN -> T_BOOLEAN
    CHAR -> T_CHAR
    BYTE -> T_BYTE
    SHORT -> T_SHORT
    INT -> T_INT
    Type.FLOAT -> T_FLOAT
    Type.LONG -> T_LONG
    Type.DOUBLE -> T_DOUBLE
    else -> error("Invalid type for primitive array creation")
  }))
}

// fields
fun InstructionAssembly.getstatic(owner: Type, name: String, type: Type) =
  instructions.add(FieldInsnNode(GETSTATIC, owner.internalName, name, type.descriptor))
fun InstructionAssembly.getfield(owner: Type, name: String, type: Type) =
  instructions.add(FieldInsnNode(GETFIELD, owner.internalName, name, type.descriptor))
fun InstructionAssembly.putstatic(owner: Type, name: String, type: Type) =
  instructions.add(FieldInsnNode(PUTSTATIC, owner.internalName, name, type.descriptor))
fun InstructionAssembly.putfield(owner: Type, name: String, type: Type) =
  instructions.add(FieldInsnNode(PUTFIELD, owner.internalName, name, type.descriptor))


// object management
fun InstructionAssembly.new(type: Type) = instructions.add(TypeInsnNode(NEW, type.internalName))
fun InstructionAssembly.checkcast(type: Type) = instructions.add(TypeInsnNode(CHECKCAST, type.internalName))
fun InstructionAssembly.instanceof(type: Type) = instructions.add(TypeInsnNode(INSTANCEOF, type.internalName))

// stack
val InstructionAssembly.pop: Unit get() = instructions.add(InsnNode(POP))
val InstructionAssembly.pop2: Unit get() = instructions.add(InsnNode(POP2))
val InstructionAssembly.dup: Unit get() = instructions.add(InsnNode(DUP))
val InstructionAssembly.dup_x1: Unit get() = instructions.add(InsnNode(DUP_X1))
val InstructionAssembly.dup_x2: Unit get() = instructions.add(InsnNode(DUP_X2))
val InstructionAssembly.dup2: Unit get() = instructions.add(InsnNode(DUP2))
val InstructionAssembly.dup2_x1: Unit get() = instructions.add(InsnNode(DUP2_X1))
val InstructionAssembly.dup2_x2: Unit get() = instructions.add(InsnNode(DUP2_X2))
val InstructionAssembly.swap: Unit get() = instructions.add(InsnNode(SWAP))


fun InstructionAssembly.tableswitch(min: Int, max: Int, defaultLabel: HasLabelNode, vararg labels: HasLabelNode) =
  instructions.add(TableSwitchInsnNode(min, max, defaultLabel.labelNode, *Array(labels.size, { labels[it].labelNode })))

fun InstructionAssembly.lookupswitch(defaultLabel: HasLabelNode, vararg branches: Pair<Int, HasLabelNode>) =
  instructions.add(LookupSwitchInsnNode(defaultLabel.labelNode,
    IntArray(branches.size, { branches[it].first }),
    Array(branches.size, { branches[it].second.labelNode })))
