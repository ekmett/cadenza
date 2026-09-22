# Final matched comparison: restoration, goal start, and endpoint

Three isolated archives use an identical, independently checked guest benchmark
harness. This separates gains already present when the six-hour goal began from
gains made during it:

* **Restored:** `26887c4b6871dd827e865cb90417c2d5c3c4f1e6`, after toolchain
  restoration/upgrades and before runtime improvements.
* **Goal start:** `dd7c47b7b8afca92b1c3d59c420fbb8ae95c6c2b` (runtime
  `89de817`), the last commit before 2026-09-22 04:43:54 UTC.
* **Final runtime:** `2d3b1f339ab62df589ceed045747b97ac8f03203`.

All three use their default AST backend, with no backend option. The exact same
guest sources, rooted dispatch entry, setup checks, varying arguments, benchmark
methods and JMH configuration are used wherever a workload runs. Capture results
escape to JMH; setup retains all sixteen closures before invoking them in reverse
and checking each captured value. Accumulator setup checks all sixteen inputs
against the closed-form triangular sum modulo 65521. Fibonacci setup checks all
four inputs against iterative host BigInteger arithmetic. Both forks passed every
setup check. Timed regions exclude parsing, context setup and result validation.

Apple M3 ARM64 macOS, GraalVM 25.3.4.1 / JDK 25.0.4.1, Kotlin 2.4.20,
Gradle 9.7.1 and JMH 1.37. Each revision used two forks, ten one-second warmups,
five one-second measurements per fork, one worker and GC profiling, with
128–768 MiB fork heaps and `-Xss32m`. All eight runs were serial, with no
concurrent project builds. Other user applications remained running. Timing
intervals below are JMH's 99.9% confidence intervals.

## Time per operation

| Workload | Restored | Goal start | Final runtime |
|---|---:|---:|---:|
| Self-tail modular accumulator, 1000–1015 | 9.098 ± 0.069 µs | 3.198 ± 0.014 µs | 3.184 ± 0.027 µs |
| Escaping capture, 1000–1015 | 8.081 ± 0.055 ns | 8.252 ± 0.111 ns | 7.843 ± 0.485 ns |
| Fibonacci, 15–18 | Not measured: restored workload fails | 69.380 ± 6.996 µs | 15.096 ± 4.829 µs |

## Allocated bytes per operation

| Workload | Restored | Goal start | Final runtime |
|---|---:|---:|---:|
| Self-tail modular accumulator, 1000–1015 | 110,776.5 | 56.2 | 56.2 |
| Escaping capture, 1000–1015 | 104.0 | 136.0 | 104.0 |
| Fibonacci, 15–18 | — | 748,079.6 | 50,172.8 |

## Interpretation

During the six-hour goal, Fibonacci takes **78.2% less time** (**4.60× measured
throughput**) in this matched run and allocates **93.3% fewer bytes**. Final-runtime
timing is noisy
(15.096 ± 4.829 µs), so the exact timing ratio should not be generalized; its
allocation result is much more repeatable. The independent setup oracle passed
on both revisions.

Escaping capture allocation falls **136 → 104 B/op**, restoring the original
allocation level. The final closure timing interval overlaps both baselines,
so no final-runtime closure speedup is established here.

The ordinary self-tail accumulator was already efficient at goal start: both
goal-start and final revisions allocate about **56 B/op**, and their timing
intervals overlap. Relative to restoration, the final runtime takes **65.0% less
time** (**2.86× measured throughput**) and allocates **99.95% fewer bytes**. This
gain was already present at goal start and must not be credited to the six-hour
work window.

Restored Fibonacci and the fixed-point counter failed in the earlier
[restoration comparison](restoration-comparison.md); no restored Fibonacci timing
is invented or included here. This final run deliberately selects only the two
working restored workloads, plus Fibonacci on goal-start/current. It does not
claim an overall language or bytecode speedup.

## Useful fixed-point work during the goal

A final supplemental pair compares the exact `FixedAccumulate` guest program
from the main benchmark suite on goal-start and final runtime. It performs a
modular sum at each recursive step, packing index and sum into a Nat state; it is
not the trivial counter that the compiler can eliminate. Inputs vary from 1000
through 1015. The largest state stays below 67 million, so this run does not
depend on the later large-integer arithmetic fixes.

| Workload | Goal start | Final runtime | Goal-start B/op | Final B/op |
|---|---:|---:|---:|---:|
| Fixed-point modular accumulator | 21.223 ± 2.864 µs | 4.889 ± 0.100 µs | 272,081.1 | 56.2 |

That is **77.0% less time** (**4.34× measured throughput**)
and **99.979% less allocation** in this matched run. Its timing
intervals do not overlap. This gives a useful-work six-hour comparison in
addition to Fibonacci, without using the eliminated counter as a throughput claim.

The [supplemental harness](final-fixed-harness.kt.txt), SHA256
`1f811326625aba49160390d5f1145eba7f2881d112a89b7b3fc5971f6833d2fa`,
preserves the complete three-checkpoint harness and adds only this benchmark.
It is identical on both revisions. Both forks independently checked all sixteen
results against the closed-form triangular sum modulo 65521 before timing.
The two existing isolated runtime archives were reused with this shared extended
harness; both benchmark builds completed before either timing run. JMH settings
and serial execution match the table above. Select
`^cadenza.bench.FixedAccumulate.cadenza$ -p size=1000`, without a backend parameter.
[Supplemental raw results and exact invocations](final-fixed-data.json) retain
the independent pair and its setup-check counts. No restored-runtime result is
claimed for this workload.

## Reproduction and evidence

The [exact harness](final-comparison-harness.kt.txt) has SHA256
`187bf69c78c6ed53f7db8cbc36caedc1bedd06e9c3f6591a65e20d5124eef741`.
In each isolated revision archive, replace the benchmark Kotlin sources with
this file as `bench/bench.kt`, build `benchClasses`, and run JMH directly using
the benchmark runtime classpath. The `.txt` suffix keeps the retained artifact
out of the repository's normal benchmark source compilation.

Select one workload, omitting the backend option/parameter on every revision:

```text
^cadenza.bench.Accumulate.cadenza$ -p size=1000
^cadenza.bench.CapturedClosure.allocate$ -p base=1000
^cadenza.bench.Fib.cadenza$ -p size=15

-wi 10 -w 1s -i 5 -r 1s -f 2 -t 1 -prof gc -foe true
-jvmArgsAppend '-Xms128m -Xmx768m' -rf json -rff results.json
```

Use a 64–128 MiB runner heap and `--enable-native-access=ALL-UNNAMED -Xss32m`.
[Raw results and exact invocations](final-comparison-data.json) retain all
measurement samples, allocation intervals, JVM settings, revisions, setup-check
counts and the common source hash. The narrow harness omits Kotlin and reference
interpreter benchmarks on every side. Do not splice these new timings into
earlier sessions to calculate additional speedups.
