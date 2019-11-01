package cadenza.asm

import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode

// construct a class file for a custom java enum. enum("org/intelligence/MyEnum","foo","bar","baz")
fun enum(access: Mod = public, name: String, vararg members: String): ByteArray = enumNode(access, name, *members).assemble

fun enumNode(access: Mod = public, name: String, vararg members: String): ClassNode = classNode(access = access, name = name) {
  val type = this.type
  val typeArray = type.array
  val values = "\$VALUES"
  for (member in members) field(public and static and final, type, member)
  field(private and static and final, typeArray, values)
  method(public and static, typeArray, "values") {
    asm {
      getstatic(type, values, typeArray)
      invokevirtual(type.array, +Object::class, "clone")
      checkcast(type.array)
      areturn
    }
  }
  method(public and static, type, "valueOf", +String::class) {
    asm {
      ldc(type)
      aload_0
      invokestatic(+Enum::class, +Enum::class, "valueOf", +Class::class, +String::class)
      checkcast(type)
      areturn
    }
  }
  method(private, void, "<init>", +String::class, +Int::class) {
    asm {
      aload_0
      aload_1
      iload_2
      invokespecial(+Enum::class, void, "<init>", +String::class, +Int::class)
      `return`
    }
  }
  method(static, void, "<clinit>") {
    asm {
      for (i in members.indices) {
        new(type)
        dup
        ldc(members[i])
        push(i)
        putstatic(type, members[i], type)
      }
      push(members.size)
      anewarray(type)
      for (i in members.indices) {
        dup
        push(i)
        getstatic(type, members[i], type)
        aastore
      }
      putstatic(type, values, typeArray)
      `return`
    }
  }
}
