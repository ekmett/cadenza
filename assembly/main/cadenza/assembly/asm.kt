package cadenza.assembly

// dsl-based java assembler

import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.Type.*
import org.objectweb.asm.tree.*
import kotlin.reflect.KClass

// access modifiers, if inline classes breakor get removed, make this a data class
inline class Modifier(val access: Int) {
  infix fun or(other: Modifier) = Modifier(this.access or other.access)
}

val public get() = Modifier(ACC_PUBLIC)
val private get() = Modifier(ACC_PRIVATE)
val static get() = Modifier(ACC_STATIC)
val abstract get() = Modifier(ACC_ABSTRACT)
val module get() = Modifier(ACC_MODULE)
val none get() = Modifier(0)
val open get() = Modifier(ACC_OPEN)
val `interface` get() = Modifier(ACC_INTERFACE)
val enum get() = Modifier(ACC_ENUM)

// java types
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

// allows us to emit assembly instructions in a straight line
interface Instructions {
  val instructions: InsnList
  fun add(node: AbstractInsnNode) = instructions.add(node)
  fun add(list: InsnList) = instructions.add(list)
}

// start to deal with labels

interface LabelNodeWrapper {
  val labelNode: LabelNode
}

interface TryCatchBlocksWrapper {
  val tryCatchBlocks: MutableList<TryCatchBlockNode>
}

fun LabelNode.wrap() : LabelNodeWrapper {
  val labelNode = this
  return object : LabelNodeWrapper {
    override val labelNode = labelNode
  }
}

class AssemblyLabel(private val instructions: InsnList, override val labelNode: LabelNode) : LabelNodeWrapper {
  //operator fun unaryPlus { instructions.add(labelNode) }
}

class LabelRegistry(private val instructions: InsnList) {
  private var labels = mutableMapOf<String, LabelNode>()
  fun copy(instructions: InsnList) =  LabelRegistry(instructions).also { it.labels = this.labels }
  fun scope(instructions: InsnList) = LabelRegistry(instructions).also { it.labels.putAll(this.labels) }
  //operator fun get(index: Int) = this["label_$index"]
  //operator fun get(name: String) = AssemblyLabel(instructions, labels.getOrPut(name, LabelNode))
}

interface LabelScope { val labelRegistry: LabelRegistry } // HasLabelRegistry?
interface LabelledAssembly : Instructions, LabelScope

interface Assembly : LabelledAssembly, TryCatchBlocksWrapper

fun Assembly.scope(routine: LabelScope.() -> Unit) {
  val self = this
  routine(object : LabelScope {
    override val labelRegistry = self.labelRegistry.scope(instructions)
  })
}

fun Assembly.mergeFrom(asm: Assembly, label: LabelNodeWrapper) {
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
    override val labelRegistry = LabelRegistry(instructions)
  })

  return Pair(instructions, tryCatchBlocks)
}

abstract class AbstractAssembly(
  override val instructions: InsnList,
  override val tryCatchBlocks: MutableList<TryCatchBlockNode>
) : Assembly

class MethodAssemblyContext(node: MethodNode) : AbstractAssembly(node.instructions, node.tryCatchBlocks) {
  override val labelRegistry = LabelRegistry(instructions)
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
  ASM7,
  access.access,
  name,
  getMethodDescriptor(returnType, *parameterTypes),
  signature,
  exceptions?.map { it.internalName }?.toTypedArray()
).also {
  routine(MethodAssemblyContext(it))
}

// class assembly

class ClassAssemblyContext {
  private val node: ClassNode = ClassNode(ASM7).also {
    it.version = 49
    it.superName = "java/lang/Object"
  }

  var access: Modifier
    get() = Modifier(node.access)
    set(value) { node.access = value.access }

  private var name: String
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
    getMethodDescriptor(returnType, *argumentTypes),
    signature,
    exceptions?.map { it.internalName }?.toTypedArray()
  ).also {
    routine(MethodAssemblyContext(it))
    node.methods.add(it)
  }
}

val Instructions.nop: Unit get() = add(InsnNode(NOP))
// math
val Instructions.iadd: Unit get() = add(InsnNode(IADD))
val Instructions.ladd: Unit get() = add(InsnNode(LADD))
val Instructions.fadd: Unit get() = add(InsnNode(FADD))
val Instructions.dadd: Unit get() = add(InsnNode(DADD))
val Instructions.isub: Unit get() = add(InsnNode(ISUB))
val Instructions.lsub: Unit get() = add(InsnNode(LSUB))
val Instructions.fsub: Unit get() = add(InsnNode(FSUB))
val Instructions.dsub: Unit get() = add(InsnNode(DSUB))
val Instructions.imul: Unit get() = add(InsnNode(IMUL))
val Instructions.lmul: Unit get() = add(InsnNode(LMUL))
val Instructions.fmul: Unit get() = add(InsnNode(FMUL))
val Instructions.dmul: Unit get() = add(InsnNode(DMUL))
val Instructions.idiv: Unit get() = add(InsnNode(IDIV))
val Instructions.ldiv: Unit get() = add(InsnNode(LDIV))
val Instructions.fdiv: Unit get() = add(InsnNode(FDIV))
val Instructions.ddiv: Unit get() = add(InsnNode(DDIV))
val Instructions.irem: Unit get() = add(InsnNode(IREM))
val Instructions.lrem: Unit get() = add(InsnNode(LREM))
val Instructions.frem: Unit get() = add(InsnNode(FREM))
val Instructions.drem: Unit get() = add(InsnNode(DREM))
val Instructions.ineg: Unit get() = add(InsnNode(INEG))
val Instructions.lneg: Unit get() = add(InsnNode(LNEG))
val Instructions.fneg: Unit get() = add(InsnNode(FNEG))
val Instructions.dneg: Unit get() = add(InsnNode(DNEG))
val Instructions.ishl: Unit get() = add(InsnNode(ISHL))
val Instructions.lshl: Unit get() = add(InsnNode(LSHL))
val Instructions.ishr: Unit get() = add(InsnNode(ISHR))
val Instructions.lshr: Unit get() = add(InsnNode(LSHR))
val Instructions.iushr: Unit get() = add(InsnNode(IUSHR))
val Instructions.lushr: Unit get() = add(InsnNode(LUSHR))
val Instructions.iand: Unit get() = add(InsnNode(IAND))
val Instructions.land: Unit get() = add(InsnNode(LAND))
val Instructions.ior: Unit get() = add(InsnNode(IOR))
val Instructions.lor: Unit get() = add(InsnNode(LOR))
val Instructions.ixor: Unit get() = add(InsnNode(IXOR))
val Instructions.lxor: Unit get() = add(InsnNode(LXOR))
fun Instructions.iinc(slot: Int) = add(IincInsnNode(slot, 1))
fun Instructions.iinc(slot: Int, amount: Int) = add(IincInsnNode(slot, amount))
val Instructions.i2l: Unit get() = add(InsnNode(I2L))
val Instructions.i2f: Unit get() = add(InsnNode(I2F))
val Instructions.i2d: Unit get() = add(InsnNode(I2D))
val Instructions.l2i: Unit get() = add(InsnNode(L2I))
val Instructions.l2f: Unit get() = add(InsnNode(L2F))
val Instructions.l2d: Unit get() = add(InsnNode(L2D))
val Instructions.f2i: Unit get() = add(InsnNode(F2I))
val Instructions.f2l: Unit get() = add(InsnNode(F2L))
val Instructions.f2d: Unit get() = add(InsnNode(F2D))
val Instructions.d2i: Unit get() = add(InsnNode(D2I))
val Instructions.d2l: Unit get() = add(InsnNode(D2L))
val Instructions.d2f: Unit get() = add(InsnNode(D2F))
val Instructions.i2b: Unit get() = add(InsnNode(I2B))
val Instructions.i2c: Unit get() = add(InsnNode(I2C))
val Instructions.i2s: Unit get() = add(InsnNode(I2S))

// array ops
val Instructions.iaload: Unit get() = add(InsnNode(IALOAD))
val Instructions.laload: Unit get() = add(InsnNode(LALOAD))
val Instructions.faload: Unit get() = add(InsnNode(FALOAD))
val Instructions.daload: Unit get() = add(InsnNode(DALOAD))
val Instructions.aaload: Unit get() = add(InsnNode(AALOAD))
val Instructions.baload: Unit get() = add(InsnNode(BALOAD))
val Instructions.caload: Unit get() = add(InsnNode(CALOAD))
val Instructions.saload: Unit get() = add(InsnNode(SALOAD))
val Instructions.iastore: Unit get() = add(InsnNode(IASTORE))
val Instructions.lastore: Unit get() = add(InsnNode(LASTORE))
val Instructions.fastore: Unit get() = add(InsnNode(FASTORE))
val Instructions.dastore: Unit get() = add(InsnNode(DASTORE))
val Instructions.aastore: Unit get() = add(InsnNode(AASTORE))
val Instructions.bastore: Unit get() = add(InsnNode(BASTORE))
val Instructions.castore: Unit get() = add(InsnNode(CASTORE))
val Instructions.sastore: Unit get() = add(InsnNode(SASTORE))
val Instructions.arraylength: Unit get() = add(InsnNode(ARRAYLENGTH))
fun Instructions.anewarray(type: Type) = add(TypeInsnNode(ANEWARRAY, type.internalName))
fun Instructions.multianewarray(type: Type, dimensions: Int) = add(MultiANewArrayInsnNode(type.descriptor, dimensions))
fun Instructions.newarray(type: Type) {
  add(IntInsnNode(NEWARRAY, when (type.sort) {
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
fun Instructions.getstatic(owner: Type, name: String, type: Type) =
  add(FieldInsnNode(GETSTATIC, owner.internalName, name, type.descriptor))
fun Instructions.getfield(owner: Type, name: String, type: Type) =
  add(FieldInsnNode(GETFIELD, owner.internalName, name, type.descriptor))
fun Instructions.putstatic(owner: Type, name: String, type: Type) =
  add(FieldInsnNode(PUTSTATIC, owner.internalName, name, type.descriptor))
fun Instructions.putfield(owner: Type, name: String, type: Type) =
  add(FieldInsnNode(PUTFIELD, owner.internalName, name, type.descriptor))


// object management
fun Instructions.new(type: Type) = add(TypeInsnNode(NEW, type.internalName))
fun Instructions.checkcast(type: Type) = add(TypeInsnNode(CHECKCAST, type.internalName))
fun Instructions.instanceof(type: Type) = add(TypeInsnNode(INSTANCEOF, type.internalName))

// stack
val Instructions.pop: Unit get() = add(InsnNode(POP))
val Instructions.pop2: Unit get() = add(InsnNode(POP2))
val Instructions.dup: Unit get() = add(InsnNode(DUP))
val Instructions.dup_x1: Unit get() = add(InsnNode(DUP_X1))
val Instructions.dup_x2: Unit get() = add(InsnNode(DUP_X2))
val Instructions.dup2: Unit get() = add(InsnNode(DUP2))
val Instructions.dup2_x1: Unit get() = add(InsnNode(DUP2_X1))
val Instructions.dup2_x2: Unit get() = add(InsnNode(DUP2_X2))
val Instructions.swap: Unit get() = add(InsnNode(SWAP))

fun Instructions.tableswitch(min: Int, max: Int, defaultLabel: LabelNodeWrapper, vararg labels: LabelNodeWrapper) =
  add(TableSwitchInsnNode(min, max, defaultLabel.labelNode, *Array(labels.size) { labels[it].labelNode }))

fun Instructions.lookupswitch(defaultLabel: LabelNodeWrapper, vararg branches: Pair<Int, LabelNodeWrapper>) =
  add(LookupSwitchInsnNode(defaultLabel.labelNode,
    IntArray(branches.size) { branches[it].first },
    Array(branches.size) { branches[it].second.labelNode }))