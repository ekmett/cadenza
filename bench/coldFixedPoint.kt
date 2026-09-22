package cadenza.bench

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

/** Fresh context, uncached source, fixed-point construction and first execution. */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 10)
@Measurement(iterations = 20)
@Fork(2)
@State(Scope.Thread)
open class ColdFixedPoint {
  @Param("ast", "bytecode") @JvmField var backend: String = "ast"
  @Param("5") @JvmField var size: Int = 0
  private var invocation = 0
  private lateinit var expected: IntArray

  @Setup(Level.Trial)
  fun prepareOracle() {
    expected = IntArray(4) { offset ->
      var previous = 0
      var current = 1
      repeat(size + offset) {
        val next = Math.addExact(previous, current)
        previous = current
        current = next
      }
      previous
    }
  }

  @Benchmark fun parseAndRun(): Int {
    val offset = invocation++ and 3
    val input = size + offset
    val result = Context.newBuilder("cadenza").allowExperimentalOptions(true)
      .option("cadenza.Backend", backend).build().use { context ->
        val source = Source.newBuilder("cadenza", """
          fixNatF (\(self : Nat -> Nat) (n : Nat) ->
            if le n 1 then n else plus (self (minus n 1)) (self (minus n 2))) $input
        """.trimIndent(), "cold-fixed.za").cached(false).build()
        context.eval(source).asInt()
      }
    check(result == expected[offset]) { "Fibonacci($input): expected ${expected[offset]}, got $result" }
    return result
  }
}
