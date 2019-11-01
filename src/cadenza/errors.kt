package cadenza

import com.oracle.truffle.api.CompilerDirectives

private inline fun <reified T> Array<T>.trim(i : Int = 1): Array<T> = this.drop(i).toTypedArray()

fun panic(msg: String, base: Exception?): Nothing {
  CompilerDirectives.transferToInterpreter()
  throw RuntimeException(msg, base).also { it.stackTrace = it.stackTrace.trim() }
}

@Suppress("unused")
fun panic(msg: String): Nothing {
  CompilerDirectives.transferToInterpreter()
  throw RuntimeException(msg).also { it.stackTrace = it.stackTrace.trim() }
}

@Suppress("unused")
fun todo(msg: String, base: Exception?): Nothing {
  CompilerDirectives.transferToInterpreter()
  throw RuntimeException(msg, base).also { it.stackTrace = it.stackTrace.trim() }
}

fun todo(msg: String): Nothing {
  CompilerDirectives.transferToInterpreter()
  throw RuntimeException(msg).also { it.stackTrace = it.stackTrace.trim() }
}

