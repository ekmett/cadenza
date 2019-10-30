import org.intelligence.asm.*
import org.junit.jupiter.api.Test
import java.io.PrintStream

class HelloWorldTests {
  @Test fun works() {
    val output = without {
      EphemeralClassLoader(`class`(public, "HelloWorld") {
        method(public and static, "main", void, type(Array<String>::class)) {
          asm {
            getstatic(type(System::class), "out", type(PrintStream::class))
            ldc("Hello, world!")
            invokevirtual(type(PrintStream::class), "println", void, type(String::class))
            `return`
          }
        }
      }).loadClass("HelloWorld").declaredMethods.first().invoke(null, emptyArray<String>())
    }
    assert("Hello, world!\n" == output)
  }
}