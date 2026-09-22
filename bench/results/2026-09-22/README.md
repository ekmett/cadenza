# Runtime performance, 2026-09-22

The current AST backend wins the measured execution workloads. The experimental
bytecode backend allocates less when creating escaping closures, but pays heavily
for recursive calls. Relative to the previous AST runtime, the modular accumulator
improves substantially while escaping closure allocation gets larger.

## Method

Apple M3, ARM64 macOS, 16 GiB RAM. Oracle GraalVM 25.3.4.1+1.1, Java 25.0.4.1;
JMH 1.37. Runtime commit `89de8175b088ca4ab43493bad2abf10136b80358`, plus the
benchmark-only additions accompanying this report. Baseline runtime:
`26887c4b6871dd827e865cb90417c2d5c3c4f1e6` (same Kotlin, Gradle, and GraalVM versions).

Each configuration ran sequentially with one worker, two fresh JVM forks, five
one-second warmup iterations and five one-second measurement iterations per fork.
The GC profiler was enabled. JVM options were the project's defaults, including
`-Xss32m`; heap sizes were not overridden. Timings below are means; ± is JMH's
99.9% confidence interval half-width. This is one machine and two forks, not a
broad platform comparison. [Raw JMH results](data.json) include every sample.

All guest inputs vary at runtime; results escape to JMH's consumer. Allocation
includes benchmark call ingress and returned values, so it is not exclusively
internal interpreter allocation. The new accumulator was independently checked
against a Java modular-sum implementation for all 16 inputs on current AST,
current bytecode, and the baseline AST.

## Current backends

| Workload | AST time | Bytecode time | Bytecode / AST | AST bytes/op | Bytecode bytes/op |
|---|---:|---:|---:|---:|---:|
| `fixNatF` counter, limit 1000–1015 | 11.984 ± 0.141 µs | 16.276 ± 0.492 µs | 1.36× | 191,737 | 256,265 |
| Modular accumulator, limit 1000–1015 | 3.285 ± 0.170 µs | 8.194 ± 0.336 µs | 2.49× | 56 | 118,820 |
| Fibonacci, input 15–18 | 65.205 ± 0.409 µs | 477.254 ± 22.200 µs | 7.32× | 748,075 | 897,629 |
| Escaping closure, captures 100–115 | 7.66 ± 0.14 ns | 6.86 ± 0.10 ns | 0.90× | 120 | 80 |
| Escaping closure, captures 1000–1015 | 8.14 ± 0.10 ns | 7.52 ± 0.32 ns | 0.92× | 136 | 96 |

`AddLet`'s simple recursive counter measured 7.51 ns on AST versus 7.940 µs on
bytecode (56 versus 94,904 bytes/op). The near-empty AST timing strongly suggests
the loop optimizes almost entirely away; it is not a general interpreter-throughput
comparison. `Accumulate` instead computes `sum = (sum + x) % 65521` each iteration.

The capture benchmark deliberately tests both sides of the JVM Integer cache.
Outside that cache, bytecode uses 29% fewer bytes per escaping closure operation;
the measured time difference is only about 8%.

## Before and after the runtime changes

The old runtime was extracted to a separate directory and built with the same
variable-input benchmark harness. The unsupported backend option was removed and
its backend parameter restricted to `ast`; workload source text was unchanged
for the two successful comparisons below.

| Workload | Previous AST time | Current AST time | Previous bytes/op | Current bytes/op |
|---|---:|---:|---:|---:|
| Modular accumulator, limit 1000–1015 | 9.099 ± 0.093 µs | 3.285 ± 0.170 µs | 110,776 | 56 |
| Escaping closure, captures 1000–1015 | 8.09 ± 0.09 ns | 8.14 ± 0.10 ns | 104 | 136 |

The accumulator is **2.77× faster**, with almost all measured per-iteration
allocation eliminated. Escaping closure time is indistinguishable at this
resolution, but allocation **increased 31%**. The StaticShape implementation adds
a `CapturedFrame` wrapper around its storage object, whereas the old generated
frame directly represented the environment.

A baseline Fibonacci attempt was excluded: the old runtime cannot partially
apply `fixNatF`, and a saturated lambda wrapper then failed during warmup with
`FrameSlotTypeException: Frame slot kind Long expected, but got Object at frame
slot index 0`. There is no valid old/new Fibonacci speedup claim here. The old
closed-program benchmarks also cannot serve as a trustworthy baseline for these
varying-input measurements.

## Interpretation and next steps

These are code-informed explanations, not isolated causal experiments:

* Bytecode tail calls currently always throw into the shared trampoline because
  `TailCheck` only recognizes AST `ClosureRootNode` for its direct-call path.
  AST has a peeled self-tail loop. Bytecode-native self-tail backedges and less
  allocation in call instructions are the first optimization targets.
* `FixApplyRootNode` constructs a recursive closure and partial-argument array on
  each invocation. Retaining that `self` as immutable closure data could help both
  backends without restoring the old ownership bugs or enormous caches.
* AST capture storage can likely lose the extra wrapper by using a custom
  StaticShape storage class/factory. The current allocation regression is real;
  moving to the supported storage API alone did not make captures smaller.

No neutral-term exception policy was changed or compared in these measurements.
Cold-start speed, large programs, and multi-thread scaling were not measured.

## Reproduction

With the documented GraalVM `JAVA_HOME`, run each selection separately:

```sh
./gradlew bench --args='cadenza.bench.(Add|AddLet).cadenza -p size=1000 -wi 5 -i 5 -w 1s -r 1s -f 2 -t 1 -prof gc -rf json -rff loops.json'
./gradlew bench --args='cadenza.bench.Fib.cadenza -p size=15 -wi 5 -i 5 -w 1s -r 1s -f 2 -t 1 -prof gc -rf json -rff fib.json'
./gradlew bench --args='cadenza.bench.Accumulate.cadenza -p size=1000 -wi 5 -i 5 -w 1s -r 1s -f 2 -t 1 -prof gc -rf json -rff accumulate.json'
./gradlew bench --args='cadenza.bench.CapturedClosure.allocate -wi 5 -i 5 -w 1s -r 1s -f 2 -t 1 -prof gc -rf json -rff capture.json'
```

For the baseline, extract commit `26887c4`, replace its `bench/bench.kt` with the
current harness, remove `.option("cadenza.Backend", selectedBackend)` from guest
setup, and change backend `@Param("ast", "bytecode")` declarations to
`@Param("ast")`. Run the accumulator command and the capture command with
`-p base=1000`. Check JMH output for failed forks as well as Gradle's exit code.
