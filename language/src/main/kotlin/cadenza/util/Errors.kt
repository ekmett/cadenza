package cadenza.util

import com.oracle.truffle.api.CompilerDirectives

object Errors {
  fun panic(msg: String, base: Exception? = null): Nothing {
    CompilerDirectives.transferToInterpreter();
    val e = RuntimeException(msg, base);
    e.stackTrace = e.stackTrace.dropLast(1).toTypedArray() // drop call to panic from trace
    throw e;
  }
}