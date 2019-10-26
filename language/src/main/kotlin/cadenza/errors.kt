package cadenza

import com.oracle.truffle.api.CompilerDirectives

private inline fun <reified T> Array<T>.trim(i : Int = 1): Array<T> = this.drop(i).toTypedArray<T>()

// we've reached an illegal state. logic error:

fun panic(msg: String, base: Exception?): Nothing {
  CompilerDirectives.transferToInterpreter();
  val e = RuntimeException(msg, base)
  e.stackTrace = e.stackTrace.trim()
  throw e;
}

fun panic(msg: String): Nothing {
  CompilerDirectives.transferToInterpreter();
  val e = RuntimeException(msg, null)
  e.stackTrace = e.stackTrace.trim()
  throw e;
}

// i need to finish something

fun todo(msg: String, base: Exception?): Nothing {
  CompilerDirectives.transferToInterpreter();
  val e = RuntimeException(msg, base)
  e.stackTrace = e.stackTrace.trim()
  throw e;
}

fun todo(msg: String): Nothing {
  CompilerDirectives.transferToInterpreter();
  val e = RuntimeException(msg, null)
  e.stackTrace = e.stackTrace.trim()
  throw e;
}