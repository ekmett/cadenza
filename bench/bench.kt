package cadenza.bench

import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit
import cadenza.Language
import cadenza.interpreter.Const
import cadenza.interpreter.eval
import cadenza.interpreter.initialEnv
import cadenza.interpreter.subst
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.source.Source
import org.graalvm.polyglot.Context


@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
abstract class SourceBenchmark {
  abstract val text: CharSequence

  val source by lazy { Source.newBuilder("cadenza", text, "bench.za").build() }

  val cadenzaTarget by lazy {
    val ctx = Context.create()
    ctx.enter()
    ctx.initialize("cadenza")
    Language.currentLanguage().parse(source)
  }
  @Benchmark
  // some extra warmup
  @Warmup(iterations=30)
  fun cadenza() {
    cadenzaTarget.call()
  }

  val interpExpr by lazy {cadenza.interpreter.parse(source).subst { Const(initialEnv[it]) } }
  @Benchmark
  fun interpreter() {
    interpExpr.eval(arrayOf())
  }
}

open class Add : SourceBenchmark() {
  override val text = "fixNatF (\\(f : Nat -> Nat) (x : Nat) -> if le 1000 x then x else f (plus x 1)) 0"

  @Benchmark
  fun kotlin(bh: Blackhole) {
    var x = 0
    while (x <= 1000) { x++ }
    bh.consume(x)
  }
}

fun fib(x: Int): Int = if (x <= 1) x else fib (x - 1) + fib (x - 2)

open class Fib : SourceBenchmark() {
  override val text = "fixNatF (\\(f : Nat -> Nat) (x : Nat) -> if le x 1 then x else plus (f (minus x 1)) (f (minus x 2))) 15"

  @Benchmark
  fun kotlin(bh: Blackhole) {
    bh.consume(fib(15))
  }
}


