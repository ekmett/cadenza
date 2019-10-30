import org.intelligence.asm.*
import org.junit.jupiter.api.Test
import java.io.PrintStream

class HelloWorldTests {
  @Test fun works() {
    val output = without {
      EphemeralClassLoader(`class`(public, "HelloWorld") {
        method(public and static, "main", void, +Array<String>::class) {
          asm {
            getstatic(+System::class, "out", +PrintStream::class)
            ldc("Hello, world!")
            invokevirtual(+PrintStream::class, "println", void, +String::class)
            `return`
          }
        }
      }).loadClass("HelloWorld").declaredMethods.first().invoke(null, emptyArray<String>())
    }
    assert("Hello, world!\n" == output)
  }
}