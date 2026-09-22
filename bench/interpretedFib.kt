package cadenza.bench

/** Warm JVM execution of the guest interpreter, with Truffle guest compilation disabled. */
open class GuestInterpretedFib : Fib() {
  override val guestCompilationEnabled = false
}
