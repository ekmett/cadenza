package cadenza.asm

// dsl-based java assembler as a simple wrapper around ObjectWeb's assembler

import org.objectweb.asm.*
import org.objectweb.asm.ClassWriter.*
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type.*
import org.objectweb.asm.tree.*

// construct a raw method node
fun methodNode(
  access: Mod,
  returnType: Type,
  name: String,
  vararg parameterTypes: Type,
  f: MethodNode.() -> Unit
) = MethodNode(
  ASM7,
  access.access,
  name,
  getMethodDescriptor(returnType, *parameterTypes),
  null,
  null
).also {
  f(it)
}

fun MethodNode.throws(vararg throwable: Type) {
  exceptions = throwable.map { it.internalName }
}

// construct a method and add it to the class
fun ClassNode.method(
  access: Mod,
  returnType: Type,
  name: String,
  vararg parameterTypes: Type,
  f: MethodNode.() -> Unit // use -> A and change return type?
) = methodNode(
  access,
  returnType,
  name,
  parameterTypes = *parameterTypes,
  f = f
).also {
  methods.add(it)
}

val ClassNode.type: Type get() = Type.getType("L$name;")

// construct a field and add it to the class
fun ClassNode.field(
  access: Mod,
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

fun classNode(
  access: Mod,
  name: String,
  version: Int = 49,
  superName: String = "java/lang/Object",
  f: ClassNode.() -> Unit
) = ClassNode(ASM7).also {
  it.name = name
  it.version = version
  it.superName = superName
  it.access = access.access
  f(it);
}

val ClassNode.assemble: ByteArray get() = ClassWriter(COMPUTE_FRAMES).also { this.accept(it) }.toByteArray()

fun `class`(
  access: Mod,
  name: String,
  version: Int = 49,
  superName: String = "java/lang/Object",
  f: ClassNode.() -> Unit
) = classNode(access, name, version, superName, f).assemble

interface Block {
  val instructions: InsnList
}

fun Block.add(it: AbstractInsnNode) = instructions.add(it)
fun Block.add(many: InsnList) = instructions.add(many)

// eventually MethodNode should go to Assembly via asm
interface Assembly : Block {
  val tryCatchBlocks: MutableList<TryCatchBlockNode>
}

class SimpleAssembly(
  override val instructions: InsnList,
  override val tryCatchBlocks: MutableList<TryCatchBlockNode>
) : Assembly

class GuardedAssembly internal constructor(base: Assembly) : Assembly by base {
  val startNode: LabelNode = LabelNode()
  val endNode: LabelNode = LabelNode()
  val exitNode: LabelNode = LabelNode()
  internal fun create(f: GuardedAssembly.() -> Unit) {
    add(startNode)
    f(this)
    add(JumpInsnNode(GOTO, exitNode))
    add(endNode)
    add(exitNode)
  }

  fun handle(
    exceptionType: Type,
    fallthrough: Boolean = false,
    f: Assembly.() -> Unit
  ): GuardedAssembly {
    val handlerNode = LabelNode()
    val handler = SimpleAssembly(InsnList(), tryCatchBlocks).apply {
      add(handlerNode)
      add(FrameNode(F_SAME1, 0, null, 1, arrayOf(exceptionType.internalName)))
      f(this)
      if (!fallthrough) add(JumpInsnNode(GOTO, exitNode))
    }
    instructions.insertBefore(exitNode, handler.instructions)
    tryCatchBlocks.add(TryCatchBlockNode(startNode, endNode, handlerNode, exceptionType.internalName))
    return this;
  }
}

fun <A> MethodNode.asm(f: Assembly.() -> A) = f(SimpleAssembly(instructions, tryCatchBlocks))
fun Assembly.guard(f: GuardedAssembly.() -> Unit) = GuardedAssembly(this).also { it.create(f) }
fun MethodNode.guard(f: GuardedAssembly.() -> Unit) = asm { guard(f) }
