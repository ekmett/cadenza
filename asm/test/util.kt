import java.io.ByteArrayOutputStream
import java.io.PrintStream

class EphemeralClassLoader(private val classBuffer: ByteArray) : ClassLoader() {
  override fun findClass(name: String?): Class<*> {
    return defineClass(name, classBuffer, 0, classBuffer.size)
  }
}
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
