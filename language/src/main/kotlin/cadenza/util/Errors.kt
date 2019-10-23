package cadenza.util

import com.oracle.truffle.api.CompilerDirectives

object Errors {
  fun panic(msg: String): RuntimeException {
    CompilerDirectives.transferToInterpreter()
    return RuntimeException(msg)
  }

  fun panic(msg: String, e: Exception): RuntimeException {
    CompilerDirectives.transferToInterpreter()
    return RuntimeException(msg, e)
  }
}
