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

[Restoration-to-current comparison](results/2026-09-22/restoration-comparison.md)
measures the total change since the restored old runtime, with identical workloads
and bounded heaps, and distinguishes failed baseline workloads from speedups.

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
