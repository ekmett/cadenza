package cadenza.bench

import cadenza.Language
import cadenza.data.Closure
import cadenza.data.Neutral
import cadenza.data.NeutralValue
import cadenza.interpreter.Callable
import cadenza.interpreter.Const
import cadenza.interpreter.eval
import cadenza.interpreter.initialEnv
import cadenza.interpreter.subst
import cadenza.jit.CadenzaRootNode
import cadenza.jit.DispatchNodeGen
import cadenza.jit.PlusNodeGen
import cadenza.jit.FrameLayout
import cadenza.semantics.Type
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.nodes.Node.Child
import com.oracle.truffle.api.source.Source
import org.graalvm.polyglot.Context
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

/** Read benchmark arguments from a call frame, so PE cannot fold a closed program. */
private class BenchmarkApplyRoot(language: Language, private val function: Closure) :
  CadenzaRootNode(language, FrameLayout().build()) {
  @Child private var dispatch = DispatchNodeGen.create(1, false)
  override fun execute(frame: VirtualFrame): Any? =
    dispatch.executeDispatch(frame, function, frame.arguments)
}

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5)
@Measurement(iterations = 5)
@Fork(2)
@State(Scope.Thread)
abstract class GuestBenchmark {
  abstract val text: String
  protected open val selectedBackend = "ast"
  private lateinit var context: Context
  protected lateinit var target: CallTarget
  protected lateinit var source: Source
  private var cursor = 0

  @Setup(Level.Trial)
  fun openContext() {
    context = Context.newBuilder("cadenza").allowExperimentalOptions(true)
      .option("cadenza.Backend", selectedBackend).build()
    context.enter()
    try {
      context.initialize("cadenza")
      source = Source.newBuilder("cadenza", text, "bench.za").build()
      val language = Language.currentLanguage()
      val function = language.parse(source).call() as Closure
      target = BenchmarkApplyRoot(language, function).callTarget
      prepareBaseline()
    } catch (failure: Throwable) {
      context.leave()
      context.close()
      throw failure
    }
  }

  protected open fun prepareBaseline() {}

  @TearDown(Level.Trial)
  fun closeContext() {
    try { context.leave() } finally { context.close() }
  }

  protected fun nextInput(base: Int): Int {
    val offset = cursor
    cursor = (cursor + 1) and 15
    return base + offset
  }
}

abstract class BackendBenchmark : GuestBenchmark() {
  @Param("ast", "bytecode") @JvmField var backend: String = "ast"
  override val selectedBackend get() = backend
}

abstract class ComparedBenchmark : BackendBenchmark() {
  protected lateinit var interpreted: Callable

  override fun prepareBaseline() {
    interpreted = cadenza.interpreter.parse(source)
      .subst { Const(initialEnv[it]) }.eval(emptyArray()) as Callable
  }
}

open class Add : ComparedBenchmark() {
  @Param("100", "1000") @JvmField var size: Int = 0
  override val text = "\\(limit : Nat) -> fixNatF (\\(f : Nat -> Nat) (x : Nat) -> if le limit x then x else f (plus x 1)) 0"

  @Benchmark fun cadenza(): Any? = target.call(nextInput(size))
  @Benchmark fun interpreter(): Any = interpreted.call(arrayOf(nextInput(size)))
  @Benchmark fun kotlin(): Int {
    val limit = nextInput(size)
    var x = 0
    while (x < limit) x = Math.addExact(x, 1)
    return x
  }
}

/** The small reference interpreter does not implement recursive let. */
open class AddLet : BackendBenchmark() {
  @Param("100", "1000") @JvmField var size: Int = 0
  override val text = "\\(limit : Nat) -> let add : Nat -> Nat = \\(x : Nat) -> if le limit x then x else add (plus x 1) in add 0"
  @Benchmark fun cadenza(): Any? = target.call(nextInput(size))
}

/** A loop with useful work in each step, preventing counter-to-limit simplification. */
open class Accumulate : BackendBenchmark() {
  @Param("1000") @JvmField var size: Int = 0
  override val text = "\\(limit : Nat) -> let go : Nat -> Nat -> Nat = \\(x : Nat) (sum : Nat) -> if le limit x then sum else go (plus x 1) (mod (plus sum x) 65521) in go 0 0"
  @Benchmark fun cadenza(): Any? = target.call(nextInput(size))
}

private fun fib(x: Int): Int = if (x <= 1) x else fib(x - 1) + fib(x - 2)

open class Fib : ComparedBenchmark() {
  @Param("10", "15") @JvmField var size: Int = 0
  override val text = "fixNatF (\\(f : Nat -> Nat) (x : Nat) -> if le x 1 then x else plus (f (minus x 1)) (f (minus x 2)))"

  // Keep the varying range small: each extra Fibonacci level nearly doubles the work.
  @Benchmark fun cadenza(): Any? = target.call(size + (nextInput(0) and 3))
  @Benchmark fun interpreter(): Any = interpreted.call(arrayOf(size + (nextInput(0) and 3)))
  @Benchmark fun kotlin(): Int = fib(size + (nextInput(0) and 3))
}

/** Returned closures escape to JMH's result consumer; use -prof gc for allocation. */
open class CapturedClosure : BackendBenchmark() {
  @Param("100", "1000") @JvmField var base: Int = 0
  override val text = "\\(x : Nat) -> \\(y : Nat) -> plus x y"
  @Benchmark fun allocate(): Any? = target.call(nextInput(base))
}

/** Runtime selection prevents the entire operation from folding to one constant result. */
open class NoncapturingClosure : BackendBenchmark() {
  @Param("100", "1000") @JvmField var base: Int = 0
  override val text = "\\(selector : Nat) -> if eq (mod selector 2) 0 then (\\(x : Nat) -> plus x 1) else (\\(x : Nat) -> plus x 2)"
  @Benchmark fun select(): Any? = target.call(nextInput(base))
}

/** Four targets force the shared call site to handle overapplication generically. */
open class PartialApplication : BackendBenchmark() {
  @Param("1000") @JvmField var base: Int = 0
  override val text = """
    \(x : Nat) ->
      let selected : Nat -> Nat -> Nat -> Nat -> Nat -> Nat =
        if eq (mod x 4) 0 then
          (\(a : Nat) -> \(b : Nat) (c : Nat) (d : Nat) (e : Nat) -> plus a (plus b (plus c (plus d e))))
        else if eq (mod x 4) 1 then
          (\(a : Nat) (b : Nat) -> \(c : Nat) (d : Nat) (e : Nat) -> plus 1 (plus a (plus b (plus c (plus d e)))))
        else if eq (mod x 4) 2 then
          (\(a : Nat) -> \(b : Nat) (c : Nat) (d : Nat) (e : Nat) -> plus 2 (plus a (plus b (plus c (plus d e)))))
        else
          (\(a : Nat) (b : Nat) -> \(c : Nat) (d : Nat) (e : Nat) -> plus 3 (plus a (plus b (plus c (plus d e)))))
      in selected x 1 2
  """.trimIndent()

  override fun prepareBaseline() {
    // Complete escaping partials outside measurement, checking both argument ranges
    // and populating the four-target cache before JMH warmup starts.
    val interop = InteropLibrary.getUncached()
    repeat(16) { offset ->
      val input = base + offset
      val partial = target.call(input)
      check(interop.execute(partial, 3, 4) == input + 10 + input % 4)
    }
  }

  @Benchmark fun allocate(): Any? = target.call(nextInput(base))
}

/** Measure the intentional exceptional neutral path separately from ordinary evaluation. */
open class NeutralNormalization : GuestBenchmark() {
  override val text = "\\(x : Nat) -> plus x 1"
  private val neutral = NeutralValue(Type.Nat,
    Neutral.NCallBuiltin(PlusNodeGen.create(), arrayOf(null, null)))
  @Benchmark fun normalize(): Any? = target.call(neutral)
}

/** Includes context construction, parsing, execution, and context shutdown. */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2)
@Measurement(iterations = 5)
@Fork(2)
@State(Scope.Thread)
open class ColdStart {
  @Param("ast", "bytecode") @JvmField var backend: String = "ast"
  @Param("100", "1000") @JvmField var size: Int = 0
  private var invocation = 0

  @Benchmark fun parseAndRun(): Int = Context.newBuilder("cadenza")
    .allowExperimentalOptions(true).option("cadenza.Backend", backend).build().use { context ->
    val argument = size + (invocation++ and 15)
    val source = org.graalvm.polyglot.Source.newBuilder("cadenza",
      "plus $argument 1", "cold.za").cached(false).build()
    context.eval(source).asInt()
  }
}
