package cadenza.frame

// cadenza.aot?

import cadenza.data.map
import cadenza.jit.Code
import cadenza.todo
import com.oracle.truffle.api.dsl.Cached
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.frame.FrameSlotTypeException
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.NodeCost
import com.oracle.truffle.api.nodes.NodeInfo
import org.intelligence.asm.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.LabelNode
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Constructor


// manufacture cadenza.frame.dynamic.IIO, OIO, etc.

// an immutable dataframe modeling the different backing storage types allowed by the jvm

// throw an exception type that has a default constructor
private fun assembleThrow(asm: Block, exceptionType: Type) = asm.run {
  new(exceptionType)
  dup
  invokespecial(exceptionType, void, "<init>")
  athrow
}

// we should also build a fallback version for holding neutrals as a subclass?
fun frame(signature: String) : ByteArray = `class`(public,"cadenza/frame/dynamic/$signature") {
  interfaces = mutableListOf(type(DataFrame::class).internalName)
  val types = signature.map { FieldInfo.of(it) }.toTypedArray()
  val N = types.size
  val members = types.indices.map { "_$it" }.toTypedArray()

  fun isMethod(name: String, predicate: (FieldInfo) -> Boolean) {
    method(public and final, boolean, name, +Slot::class) {
      asm {
        when {
          types.all(predicate) -> { iconst_1; ireturn }
          !types.any(predicate) -> { iconst_0; ireturn }
          else -> {
            val no = LabelNode()
            val yes = LabelNode()
            iload_1
            lookupswitch(no, *types.mapIndexedNotNull { i, v -> if (predicate(v)) i to yes else null }.toTypedArray())
            add(no)
            iconst_0
            ireturn
            add(yes)
            iconst_1
            ireturn
          }
        }
      }
    }
  }

  fun getMethod(resultType: Type, name: String, predicate: (FieldInfo) -> Boolean) {
    method(public and final, resultType, name, +Slot::class) {
      throws(+FrameSlotTypeException::class)
      asm {
        types.mapIndexedNotNull { i, v -> if(predicate(v)) i to LabelNode() else null }.toTypedArray().let {
          if (it.isNotEmpty()) {
            val defaultLabel = LabelNode()
            aload_0
            iload_1
            lookupswitch(defaultLabel, *it)
            it.forEach { (i, label) ->
              add(label)
              val t = types[i]
              getfield(type, members[i], t.type)
              t.ret(this)
            }
            add(defaultLabel)
          }
        }
        assembleThrow(this, +FrameSlotTypeException::class)
      }
    }
  }

  for (i in types.indices)
    field(public and final,types[i].type,members[i])

  constructor(public, parameterTypes = *types.map{it.type}.toTypedArray()) {
    asm.`return` {
      aload_0
      invokespecial(type(Object::class), void, "<init>")
      for (i in types.indices) {
        aload_0
        types[i].load(this, i+1)
        putfield(type, members[i], types[i].type)
      }
    }
  }

  constructor(public, parameterTypes = *arrayOf(`object`.array)) {
    asm.`return` {
      aload_0
      invokespecial(type(Object::class), void, "<init>")
      iconst_0
      istore_2

      for (i in types.indices) {
        aload_0
        aload_1
        iload_2
        types[i].aload(this)
        putfield(type, members[i], types[i].type)
        iinc(2)
      }
    }
  }

  isMethod("isInteger") { it.isInteger }
  isMethod("isLong") { it.isLong }
  isMethod("isFloat") { it.isFloat }
  isMethod("isDouble") { it.isDouble }

  getMethod(int, "getInteger") { it.isInteger }
  getMethod(long, "getLong") { it.isLong }
  getMethod(float, "getFloat") { it.isFloat }
  getMethod(double, "getDouble") { it.isDouble }
  getMethod(`object`, "getObject") { it.isObject }

  method(public and final, `object`, "getValue", +Slot::class) {
    asm {
      val defaultLabel = LabelNode()
      val labels = members.map { LabelNode() }.toTypedArray()
      aload_0
      iload_1
      tableswitch(0,N-1,defaultLabel,*labels)
      for (i in labels.indices) {
        add(labels[i])
        getfield(type,members[i],types[i].type)
        types[i].box(this)
        areturn
      }
      add(defaultLabel)
      assembleThrow(this, +IndexOutOfBoundsException::class)
    }
  }

  // purely for debugging
  method(public and final, int, "getSize") {
    asm.ireturn {
      push(types.size)
    }
  }
}

fun nodeInfo(
  shortName: String = "",
  cost: NodeCost = NodeCost.MONOMORPHIC,
  description: String = "",
  language: String = ""
)= annotationNode(+NodeInfo::class, shortName, cost, description, language)

val child: AnnotationNode get() = annotationNode(+Node.Child::class)

val code = +Code::class

fun builder(signature: String) : ByteArray = `class`(
  public and final and `super`,"cadenza/jit/dynamic/${signature}_Builder", superName = code.internalName
) {
  val types = signature.map { FieldInfo.of(it) }.toTypedArray()
  val members = types.indices.map { "_$it" }.toTypedArray()

  visibleAnnotations = listOf(nodeInfo(shortName = "${signature}_Builder"))

  members.forEach {
    field(public, code,it).apply { visibleAnnotations = listOf(child) }
  }

  constructor(public,*types.map { it.type }.toTypedArray()) {
    asm {
      members.forEachIndexed { i, member ->
        aload_0
        aload(i+1)
        putfield(type,member, code)
      }
      `return`
    }
  }
  method(public and final, `object`, "execute", +VirtualFrame::class) {
    todo
  }
}

fun ByteArray.loadClass(className: String) : Class<*> {
  val classBuffer = this
  return object : ClassLoader(Int::class.java.classLoader) {
    override fun findClass(name: String): Class<*> = when {
      name == "cadenza.frame.DataFrame" -> DataFrame::class.java
      name == "com.oracle.truffle.api.frame.FrameSlotTypeException" -> FrameSlotTypeException::class.java
      name == className -> defineClass(name, classBuffer, 0, classBuffer.size)
      else -> TODO()
    }
  }.loadClass(className)
}

val frameCache: HashMap<String, Class<DataFrame>> = HashMap()

abstract class BuildFrame : Node() {
  var lookup: MethodHandles.Lookup = MethodHandles.publicLookup()

  abstract fun execute(fields: Array<Any>): DataFrame

  @Specialization(guards = ["matchesSig(fields, sig)"], limit = "1000000")
  fun build(
    fields: Array<Any>,
    @Cached("getSignature(fields)", dimensions = 1) sig: Array<FieldInfo>,
    @Cached("assembleSig(sig)") cstr: MethodHandle
  ): DataFrame {
    return cstr.invokeExact(*fields) as DataFrame
  }

  fun assembleSig(sigArr: Array<FieldInfo>): MethodHandle {
    val sig = sigArr.map { it.sig }.joinToString("")
    var klass = frameCache[sig]
    if (klass === null) {
      klass = frame(sig).loadClass("cadenza.frame.dynamic.$sig") as Class<DataFrame>
      frameCache[sig] = klass
    }
    val arrayClass = arrayOf<Any>().javaClass
    val ctor = klass.getConstructor(arrayClass)
    val ctorH = lookup.unreflectConstructor(ctor)
    return ctorH.asType(ctorH.type().changeReturnType(Object::class.java))
  }

  @ExplodeLoop
  fun matchesSig(fields: Array<Any>, sig: Array<FieldInfo>): Boolean {
    sig.forEachIndexed { ix, v ->
      if (!v.matches(fields[ix])) {
        return false
      }
    }
    return true
  }

  fun getSignature(fields: Array<Any>): Array<FieldInfo> = map(fields) { FieldInfo.from(it) }
  fun equalsArray(x: ByteArray, y: ByteArray): Boolean = x.contentEquals(y)
}