import java.io.ByteArrayOutputStream
import java.io.PrintStream

fun ByteArray.loadClass(className: String) : Class<*> {
  val classBuffer = this;
  return object : ClassLoader(Guards::class.java.classLoader) {
    override fun findClass(name: String): Class<*> =
      defineClass(name, classBuffer, 0, classBuffer.size)
  }.loadClass(className)
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
