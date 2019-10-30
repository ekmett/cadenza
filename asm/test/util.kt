import org.intelligence.asm.*
import jdk.internal.org.objectweb.asm.tree.ClassNode
import java.io.ByteArrayOutputStream
import java.io.PrintStream

fun ByteArray.loadClass(name: String) : Class<*> {
  val classBuffer = this;
  return object : ClassLoader() {
    override fun findClass(name: String?): Class<*> {
      return defineClass(name, classBuffer, 0, classBuffer.size)
    }
  }.loadClass(name)
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
