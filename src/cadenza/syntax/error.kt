package cadenza.syntax

import com.oracle.truffle.api.TruffleException

data class SyntaxError(val failure: Failure): RuntimeException(failure.message), TruffleException {
  internal constructor(failure: Failure, cause: Throwable?): this(failure) {
    initCause(cause)
  }
  companion object { const val serialVersionUID : Long = 1L }
  override fun toString(): String = failure.toString()
  override fun getLocation() = null
  override fun getSourceLocation() = failure.sourceSection
  override fun isSyntaxError() = true
}