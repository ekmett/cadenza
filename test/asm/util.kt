package org.intelligence.asm

fun ByteArray.loadClass(className: String) : Class<*> {
  val classBuffer = this
  return object : ClassLoader(Guards::class.java.classLoader) {
    override fun findClass(name: String): Class<*> =
      defineClass(name, classBuffer, 0, classBuffer.size)
  }.loadClass(className)
}
