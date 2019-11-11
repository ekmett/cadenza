package org.intelligence.asm

import org.junit.jupiter.api.Test
import java.io.PrintStream
import org.intelligence.without

class Hello {
  @Test fun world() {
    val output = without {
      `class`(public, "test/HelloWorld") {
        method(public and static, void, "main", +Array<String>::class) {
          asm {
            getstatic(+System::class, "out", +PrintStream::class)
            ldc("Hello, world!")
            invokevirtual(+PrintStream::class, void, "println", +String::class)
            `return`
          }
        }
      }.loadClass("test.HelloWorld").declaredMethods.first().invoke(null, emptyArray<String>())
    }
    assert("Hello, world!\n" == output)
  }
}