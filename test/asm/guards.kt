package org.intelligence.asm

import org.junit.jupiter.api.Test
import java.io.PrintStream
import java.lang.NullPointerException
import java.lang.reflect.InvocationTargetException
import org.intelligence.without

class Guards {
  @Test
  fun work() {
    var caught = false
    val output = without {
      try {
        `class`(public, "test/Guards") {
          method(public and static, void, "main", +Array<String>::class) {
            throws(+NullPointerException::class) // unchecked anyways, meh
            asm {
              guard {
                new(+RuntimeException::class)
                dup
                invokespecial(+RuntimeException::class, void, "<init>")
                athrow
              }.handle(+RuntimeException::class, true) {
                pop
                getstatic(+System::class, "out", +PrintStream::class)
                ldc("Caught!")
                invokevirtual(+PrintStream::class, void, "println", +String::class)
              }
              getstatic(+System::class, "out", +PrintStream::class)
              aconst_null
              invokevirtual(+Object::class, +String::class, "toString")
              invokevirtual(+PrintStream::class, void, "println", +String::class)
              `return`
            }
          }
        }.loadClass("test.Guards").declaredMethods.first().invoke(null, emptyArray<String>())
      } catch (e: InvocationTargetException) {
        assert(e.cause is NullPointerException) { "expected NullPointerException" }
        caught = true
      }
    }
    assert(output == "Caught!\n") { "unexpected output: $output" }
    assert(caught) { "expected exception not caught" }
  }
}
