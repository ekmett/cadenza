package cadenza.util

import com.oracle.truffle.api.CompilerDirectives

object Errors {
  fun panic(msg: String, base: Exception?): Nothing {
    CompilerDirectives.transferToInterpreter();
    val e = RuntimeException(msg, base)
    e.stackTrace = e.stackTrace.drop(1).toTypedArray()
    throw e;
  }
  fun panic(msg: String): Nothing {
    CompilerDirectives.transferToInterpreter();
    val e = RuntimeException(msg, null)
    e.stackTrace = e.stackTrace.drop(1).toTypedArray()
    throw e;
  }
}