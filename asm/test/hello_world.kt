import org.intelligence.asm.*
import org.junit.jupiter.api.Test
import java.io.PrintStream

class Hello {
  @Test fun works() {
    val output = without {
      `class`(public, "org/intelligence/HelloWorld") {
        method(public and static, "main", void, +Array<String>::class) {
          asm {
            getstatic(+System::class, "out", +PrintStream::class)
            ldc("Hello, world!")
            invokevirtual(+PrintStream::class, "println", void, +String::class)
            `return`
          }
        }
      }.loadClass("org.intelligence.HelloWorld").declaredMethods.first().invoke(null, emptyArray<String>())
    }
    assert("Hello, world!\n" == output)
  }
}