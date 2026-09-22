# Measuring Cadenza

Run with the documented GraalVM `JAVA_HOME`, using the checked-in Gradle wrapper:

```sh
./gradlew bench --args='-l'
./gradlew bench --args='cadenza.bench.(Add|Fib)\..* -wi 5 -i 5 -f 2'
./gradlew bench --args='cadenza.bench.AddLet.* -wi 5 -i 5 -f 2'
./gradlew bench --args='cadenza.bench.Accumulate.* -wi 5 -i 5 -f 2'
./gradlew bench --args='cadenza.bench.CapturedClosure.* -prof gc'
./gradlew bench --args='cadenza.bench.NeutralNormalization.*'
./gradlew bench --args='cadenza.bench.ColdStart.*'
```

The `backend` parameter runs the AST and experimental Bytecode DSL backends
separately; use `-p backend=ast` or `-p backend=bytecode` to select one. The Kotlin
and reference interpreter baselines do not depend on the selected backend.
Neutral normalization stays on the AST backend.

`Add` and `Fib` compare warm guest execution, the small reference interpreter,
and Kotlin with the same inputs and results. Each trial owns a thread-local
context, enters it during setup, and leaves/closes it during teardown. Parsing
and initial closure construction occur in setup. The benchmark returns every
result to JMH. Inputs cycle through a small range from a mutable benchmark-state
field and enter the guest through frame arguments, preventing constant folding
of an entire closed benchmark program. `Add` and Kotlin both stop at the limit;
the old Kotlin baseline performed one extra increment. `AddLet` measures
recursive let independently because the reference interpreter does not support
that construct. Both simple counters can be optimized nearly to returning the
limit; do not interpret those workloads as general interpreter throughput. `Add`
checks every varying input during setup. `Accumulate`
performs a modular sum on every recursive step to measure a loop with useful work.
`FixedAccumulate` performs a modular sum through `fixNatF`, whose current type
accepts one natural-number state. It encodes the index and accumulator in that
state, keeping the captured loop bound outside the Integer cache. This supplies
a fixed-point workload with useful work at every step, beyond the simple counter.
`MutualAccumulate` performs the same sum through two alternating recursive roots,
exercising the general tail-call trampoline rather than only frame reuse in a
self-tail loop. Its setup checks the result against a closed-form sum.

`CapturedClosure` measures escaping closure creation; use JMH's GC profiler to
compare bytes allocated per operation as well as throughput. Its `base` parameter
selects captures inside (`100`) and outside (`1000`) the JVM Integer cache; this
matters when comparing primitive and boxed environments. `NeutralNormalization`
measures the deliberately exceptional, temporary neutral-term path separately
from ordinary execution. `ColdStart` includes fresh context creation, uncached
source parsing, execution, and shutdown. Its single-shot timings are distinct
from the warm steady-state timings; they are JVM-process-warm, not operating
system process startup measurements.

`ColdFixedPoint` adds uncached parsing, construction and first execution of a
small noncapturing recursive function in a fresh context. It cycles through four
inputs and checks each result against an iterative host oracle. Use it to check
the startup cost of fixed-point optimizations separately from warmed recursion;
the default inputs are deliberately small. Context shutdown and the result check
are included, and the JVM process itself has already warmed up.

`NoncapturingClosure` selects between two escaping functions using a runtime
argument. `PartialApplication` selects among four differently curried functions,
saturates the direct-call cache, and returns an escaping partial application;
setup checks each input variant by completing that function.

Small `-wi 0 -i 1 -w 100ms -r 100ms -f 0` runs are smoke tests, not performance
measurements. For comparisons record the GraalVM build, CPU, OS, commit, JMH
arguments, and GC profiler output, and use multiple forks. Context/host-entry
costs are intentionally excluded from the warm guest benchmark (it uses a
rooted guest dispatch); cold timings include them. The Kotlin baseline may
optimize the addition loop more aggressively; it is a reference computation,
not a promise that the two implementations execute identical machine code.

[Measured AST/bytecode and before/after results (2026-09-22)](results/2026-09-22/README.md)
include raw JMH samples, allocation data, and reproducible settings.

[First AST allocation follow-up](results/2026-09-22/ast-followup.md) records the
capture regression fix and fixed-point allocation reductions.

[AST calling follow-up](results/2026-09-22/ast-calls.md) measures direct fixed-point
calls, split-root self loops, and partial-application allocation with matched,
bounded-heap runs.

[Earlier restoration comparison](results/2026-09-22/restoration-comparison.md)
measures the restored old runtime against `f0a954d`, with identical workloads and
bounded heaps, and distinguishes failed baseline workloads from speedups.

[Final three-checkpoint comparison](results/2026-09-22/final-comparison.md)
separates restoration, the six-hour goal's starting revision, and its final runtime
using one identical checked harness, with raw results and source retained.
The [six-hour impact report](results/2026-09-22/six-hour-impact.md) collects the
runtime changes, correctness work, rejected experiments and remaining limitations.

[Rejected frame-recovery experiment](results/2026-09-22/frame-recovery.md) records
why switching to per-frame slot kinds did not improve the warmed accumulator.

[AST tooling performance gate](results/2026-09-22/tooling-ast.md) checks ordinary
execution after adding invocation wrappers and statement tags.

[Runtime diagnostic performance gate](results/2026-09-22/runtime-diagnostics.md)
includes an alternating-root accumulator to check the general tail trampoline,
alongside the existing AST workloads, after preserving runtime error locations.

[Primitive capture restoration](results/2026-09-22/capture-restore.md) measures the
removal of per-iteration boxing in fixed-point loops, including a modular-sum
workload and controls for the Integer cache, other loops, and escaping closures.

[Fixed-point self binding](results/2026-09-22/fixed-self-binding.md) measures the
remaining construction allocation after exposing the private recursive binding
to partial evaluation, with a useful-work loop and ordinary calling controls.

[Fibonacci allocation attribution](results/2026-09-22/fib-allocation.md) combines
warmed JFR sampling, an explicit recording-start weight correction, and an isolated
array-size probe. It identifies surviving call arrays before further optimization;
Fibonacci setup also checks its varying inputs against an independent host oracle.

`GuestInterpretedFib.cadenza` runs the same checked Fibonacci workload with
`engine.Compilation=false`. This measures a warm JVM running the guest interpreter;
it does not disable the JVM's own JIT. Compare it separately from `Fib.cadenza`,
which retains the normal Graal guest-compilation settings. The inherited `kotlin`
and reference `interpreter` methods do not measure the Truffle guest interpreter.

`NeutralTracing.evaluate` is AST-only, with three bounded workloads (`arithmetic`,
`conditional`, `higherOrder`) and five input histories (`concrete`, `neutral`,
`mixed`, `neutralThenConcrete`, `compiledThenNeutralThenConcrete`). Sixteen inputs
vary through the same parsed call site; mixed mode supplies one symbolic input per sixteen calls, rotating the
symbolic identity between blocks. Residuals escape to JMH. A separate setup AST
checks concrete answers, residual shapes and four independent host substitutions,
so validation does not expose the measured concrete body to neutral values.
The measured body then receives only its selected history; neutralThenConcrete
supplies one symbolic call before concrete setup checks and measurement. The
conditional includes useful non-tail branch continuations, and higherOrder checks
both flat and nested applications of a symbolic binary function.

`compiledThenNeutralThenConcrete` first warms the measured target with 64 concrete
calls and explicitly verifies installed last-tier code. It then runs 32 symbolic
calls across all sixteen inputs before returning to concrete setup checks and the
ordinary JMH warmup. This mode requires the optimizing Graal runtime; a compilation
failure fails setup. Both history modes time steady concrete execution after
recovery, rather than the first neutral call's transition or compilation latency.
The independent oracle still runs on a separate AST.

Prioritize `concrete` and both concrete-recovery histories alongside any symbolic
speedup; neutral handling must not impose a hidden ordinary-execution cost. For a
diagnostic runtime ablation that cannot evaluate neutrals, the setup-only JVM
property `-Dcadenza.bench.concreteOnly=true` skips symbolic oracle calls and is
rejected unless `mode=concrete`. Apply it identically to every runtime in that
comparison. It does not change timed code; default full validation remains
mandatory for neutral, mixed and history measurements.

[Neutral-path experiments](results/2026-09-22/neutral-tracing.md) compare symbolic
speedups against ordinary evaluator overhead and preserve the rejected candidates.

[Direct argument-array filling](results/2026-09-22/argument-array.md) reduces
guest-interpreter allocation, with matched compiled and fresh-context controls.

[Rejected fixed-self identity profiling](results/2026-09-22/fixed-self-profile.md)
records an experiment that preserved allocation and slowed small Fibonacci calls;
the candidate patch is retained as evidence but is not applied to the runtime.

[Rejected unary fixed-self entry](results/2026-09-22/bound-self-root.md) records a
smaller call-array convention that reduced allocation but substantially slowed
recursion, together with its checked cold-start comparison and unapplied patch.
