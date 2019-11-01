package org.intelligence.asm

import org.objectweb.asm.tree.ClassNode

// construct a class file for a custom java enum. enum("org/intelligence/MyEnum","foo","bar","baz")
fun enum(access: Mod = public, name: String, vararg members: String): ByteArray = enumNode(access, name, *members).assemble

fun enumNode(access: Mod = public, name: String, vararg members: String): ClassNode = classNode(access = access, name = name) {
  val type = this.type
  val types = type.array
  val values = "\$VALUES"
  val enum = +Enum::class
  for (member in members) field(public and static and final, type, member)
  field(private and static and final, types, values)
  method(public and static, types, "values") {
    asm {
      areturn {
        checkcast(type.array) {
          getstatic(type, values, types)
          invokevirtual(type.array, `object`, "clone")
        }
      }
    }
  }
  method(public and static, type, "valueOf", string) {
    asm {
      areturn {
        checkcast(type) {
          ldc(type)
          aload_0
          invokestatic(enum, enum, "valueOf", `class`, string)
        }
      }
    }
  }
  constructor(private, string, int) {
    asm {
      `return` {
        aload_0
        aload_1
        iload_2
        invokespecial(enum, void, "<init>", string, int)
      }
    }
  }
  method(static, void, "<clinit>") {
    asm {
      for (i in members.indices) {
        new(type)
        dup
        ldc(members[i])
        push(i)
        invokespecial(type,void,"<init>",string, int)
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
      `return` {
        putstatic(type, values, types)
      }
    }
  }
}
