package cadenza.bench

import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit
import cadenza.Language
import cadenza.interpreter.eval
import cadenza.interpreter.initialEnv
//import cadenza.jit.Builtin
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.source.Source
import org.graalvm.polyglot.Context



@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
open class AddBenchmark {
  @Benchmark fun addKotlin(bh: Blackhole) {
    var x = 0
    while (x <= 10000) { x++ }
    bh.consume(x)
  }

  val addSource = Source.newBuilder("cadenza", "fixNatF (\\(f : Nat -> Nat) (x : Nat) -> if le 1000 x then x else f (plus x 1)) 0", "add.za").build()
  var addInterp = cadenza.interpreter.parse(addSource)


  @Benchmark
  fun addInterp() {
    addInterp.eval(initialEnv)
  }

  val ctx = Context.create()
  var addCadenza: CallTarget
  init {
    ctx.enter()
    ctx.initialize("cadenza")
    addCadenza = Language.currentLanguage().parse(addSource)
  }

  @Benchmark
  fun addCadenza() {
    addCadenza.call()
  }

}