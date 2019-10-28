package util
class EphemeralClassLoader(private val classBuffer: ByteArray) : ClassLoader() {
  override fun findClass(name: String?): Class<*> {
    return defineClass(name, classBuffer, 0, classBuffer.size)
  }
}