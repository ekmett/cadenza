import org.intelligence.asm.*
import org.junit.jupiter.api.Test
import java.io.PrintStream

class Hello {
  @Test fun world() {
    val output = without {
      `class`(public, "test/HelloWorld") {
        method(public and static, "main", void, +Array<String>::class) {
          asm {
            getstatic(+System::class, "out", +PrintStream::class)
            ldc("Hello, world!")
            invokevirtual(+PrintStream::class, "println", void, +String::class)
            `return`
          }
        }
      }.loadClass("test.HelloWorld").declaredMethods.first().invoke(null, emptyArray<String>())
    }
    assert("Hello, world!\n" == output)
  }
}