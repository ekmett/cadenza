import org.intelligence.asm.*
import org.junit.jupiter.api.Test
import java.io.PrintStream
import util.EphemeralClassLoader
import java.io.ByteArrayOutputStream

fun without(body: () -> Unit): String {
  val old = System.out;
  val out = ByteArrayOutputStream();
  System.setOut(PrintStream(out));
  try {
    body()
  } finally {
    System.setOut(old);
  }
  return out.toString().filter { it != '\r' }
}

class HelloWorldTests {
  @Test fun helloWorldWorks() {
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