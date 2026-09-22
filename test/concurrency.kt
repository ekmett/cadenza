import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ConcurrencyTests {
  private val huge = BigInteger.TEN.pow(30)

  private fun integer(value: Int): BigInteger = BigInteger.valueOf(value.toLong())

  private fun triangular(value: Int): BigInteger =
    integer(value).multiply(integer(value + 1)).divide(BigInteger.TWO)

  /** Keep every worker entered together: these are simultaneous users of one context. */
  private fun concurrentCalls(backend: String, prepare: (Context) -> (Int, CyclicBarrier) -> Unit) {
    val context = Context.newBuilder("cadenza").allowExperimentalOptions(true)
      .option("cadenza.Backend", backend).build()
    val workers = Executors.newFixedThreadPool(4) { action ->
      Thread(action, "cadenza-concurrent-call-test").apply { isDaemon = true }
    }
    var completed = false
    try {
      // Parse once, but leave the callable roots cold until all workers have entered.
      val run = prepare(context)
      val phase = CyclicBarrier(4)
      val results = (0 until 4).map { worker -> workers.submit {
        context.enter()
        try {
          phase.await(10, TimeUnit.SECONDS)
          run(worker, phase)
        } finally {
          context.leave()
        }
      } }
      results.forEach { it.get(30, TimeUnit.SECONDS) }
      completed = true
    } finally {
      workers.shutdownNow()
      if (completed) context.close()
      else {
        // A broken guest loop/cancellation path must fail the test, not hang its JVM.
        Thread({ context.close(true) }, "cadenza-concurrent-call-cleanup").apply {
          isDaemon = true
          start()
        }
      }
    }
  }

  @TestFactory fun simultaneousCallsKeepTheirCapturedValuesAndPartialArguments() =
    listOf("ast", "bytecode").map { backend -> DynamicTest.dynamicTest("$backend concurrent captures and partials") {
      concurrentCalls(backend) { context ->
        val make = context.eval("cadenza", """
          \(seed : Nat) ->
            let captured : Nat = if eq (mod seed 2) 0 then plus seed $huge else plus seed 7 in
            \(a : Nat) (b : Nat) -> plus captured (plus (mult 1009 a) (mult 17 b))
        """.trimIndent())
        data class Saved(val closure: Value, val partial: Value, val captured: BigInteger, val a: Int, val b: Int)
        val run: (Int, CyclicBarrier) -> Unit = { worker, phase ->
          val saved = mutableListOf<Saved>()
          repeat(12) { round ->
            // In each phase, primitive and promoted captures are produced concurrently.
            val seed = worker * 101 + round
            val a = worker * 29 + round + 1
            val b = round * 11 + worker
            val captured = integer(seed).add(if (seed % 2 == 0) huge else integer(7))
            phase.await(10, TimeUnit.SECONDS)
            val closure = make.execute(seed)
            val partial = closure.execute(a)
            saved += Saved(closure, partial, captured, a, b)
            val expected = captured.add(integer(1009 * a + 17 * b))
            assertEquals(expected, partial.execute(b).asBigInteger(), "$backend worker $worker round $round partial")
            assertEquals(expected, make.execute(seed, a, b).asBigInteger(), "$backend worker $worker round $round overapplication")
          }
          // All later invocations have completed before old environments/PAP arrays are replayed.
          phase.await(10, TimeUnit.SECONDS)
          for ((closure, partial, captured, a, b) in saved.reversed()) {
            assertEquals(captured.add(integer(1009 * a + 17 * (b + 1))),
              closure.execute(a, b + 1).asBigInteger(), "$backend worker $worker retained capture")
            assertEquals(captured.add(integer(1009 * a + 17 * (b + 2))),
              partial.execute(b + 2).asBigInteger(), "$backend worker $worker retained partial")
          }
        }
        run
      }
    } }

  @TestFactory fun concurrentRecursionKeepsFixedPointCachesAndTailFramesIndependent() =
    listOf("ast", "bytecode").map { backend -> DynamicTest.dynamicTest("$backend concurrent recursive calls") {
      concurrentCalls(backend) { context ->
        val makeFixed = context.eval("cadenza", """
          \(step : Nat) -> fixNatF (\(self : Nat -> Nat) (n : Nat) ->
            if le n 0 then step else plus (mult step n) (self (minus n 1)))
        """.trimIndent())
        val tailSum = context.eval("cadenza", """
          \(seed : Nat) (count : Nat) ->
            let loop : Nat -> Nat -> Nat = \(n : Nat) (acc : Nat) ->
              if le n 0 then acc else loop (minus n 1) (plus acc n)
            in loop count seed
        """.trimIndent())
        data class Saved(val fixed: Value, val tail: Value, val step: Int, val count: Int)
        val run: (Int, CyclicBarrier) -> Unit = { worker, phase ->
          val saved = mutableListOf<Saved>()
          repeat(8) { round ->
            val step = 1 + worker * 13 + round
            val count = 12 + worker * 7 + round
            phase.await(10, TimeUnit.SECONDS)
            // More than three distinct captured functions replace FixNatF's bounded cache.
            val fixed = makeFixed.execute(step)
            val tail = tailSum.execute(step)
            saved += Saved(fixed, tail, step, count)
            assertEquals(integer(step).multiply(BigInteger.ONE.add(triangular(count))),
              fixed.execute(count).asBigInteger(), "$backend worker $worker round $round fixed point")
            assertEquals(integer(step).add(triangular(count * 11)),
              tail.execute(count * 11).asBigInteger(), "$backend worker $worker round $round tail loop")
          }
          phase.await(10, TimeUnit.SECONDS)
          for ((fixed, tail, step, count) in saved.reversed()) {
            assertEquals(integer(step).multiply(BigInteger.ONE.add(triangular(count + 1))),
              fixed.execute(count + 1).asBigInteger(), "$backend worker $worker retained fixed point")
            assertEquals(integer(step).add(triangular(count * 11 + 1)),
              tail.execute(count * 11 + 1).asBigInteger(), "$backend worker $worker retained tail partial")
          }
        }
        run
      }
    } }
}
