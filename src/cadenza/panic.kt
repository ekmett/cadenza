package cadenza

import com.oracle.truffle.api.CompilerDirectives

private inline fun <reified T> Array<T>.trim(i : Int = 1): Array<T> = this.drop(i).toTypedArray()

internal class Panic(message: String? = null) : RuntimeException(message) {
  internal constructor(message: String? = null, cause: Throwable?): this(message) {
    initCause(cause)
  }
  companion object { const val serialVersionUID : Long = 1L }
  override fun toString(): String = if (message.isNullOrEmpty()) "panic" else "panic: $message"
}

fun panic(msg: String, base: Throwable?): Nothing {
  CompilerDirectives.transferToInterpreter()
  throw Panic(msg, base).also { it.stackTrace = it.stackTrace.trim() }
}

@Suppress("unused")
fun panic(msg: String): Nothing {
  CompilerDirectives.transferToInterpreter()
  throw Panic(msg).also { it.stackTrace = it.stackTrace.trim() }
}

internal class TODO(message: String? = null) : RuntimeException(message) {
  internal constructor(message: String? = null, cause: Throwable?): this(message) {
    initCause(cause)
  }
  companion object { const val serialVersionUID : Long = 1L }
  override fun toString(): String = if (message.isNullOrEmpty()) "TODO" else "TODO: $message"
}

@Suppress("unused")
fun todo(msg: String, base: Throwable?): Nothing {
  CompilerDirectives.transferToInterpreter()
  throw RuntimeException(msg, base).also { it.stackTrace = it.stackTrace.trim() }
}

fun todo(msg: String): Nothing {
  CompilerDirectives.transferToInterpreter()
  throw RuntimeException(msg).also { it.stackTrace = it.stackTrace.trim() }
}

