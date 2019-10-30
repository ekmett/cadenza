import org.intelligence.asm.*
import java.io.PrintStream
import util.EphemeralClassLoader

fun main() {
  val classBuffer = `class`(public,"test/HelloWorld") {
    method(public and static, "main", type(Array<String>::class)) {
      asm {
        getstatic(type(System::class), "out", type(PrintStream::class))
        ldc("Hello, world!")
        invokevirtual(type(PrintStream::class), "println", void, type(String::class))
        `return`
      }
    }
  }
  val helloWorldClass: Class<*> = EphemeralClassLoader(classBuffer).loadClass("HelloWorld")
  helloWorldClass.declaredMethods.first().invoke(null, emptyArray<String>())
}