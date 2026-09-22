# Expose fixed-point self binding to partial evaluation

This compares `3c7ee1740641c46040e6870c0d0a45462dc79740` with the fixed-point
self-binding change accompanying this report. Each matched pair used identical
benchmark source and runtime settings.

The direct AST path previously constructed its recursive self closure through the
general partial-application method's `TruffleBoundary`. A narrow helper now binds
just that private fixed-point token without crossing the boundary. It copies every
existing partial argument and preserves the original target type, environment and
call target. Ordinary partial applications, unary/non-AST fixed-point fallbacks,
node caches, the frame ABI and the deliberate neutral exception protocol are
unchanged.

The hypothesis was that exposing construction would let Graal see the recursive
closure and its fixed-point cell together. The measured allocation falls by 168
bytes per invocation in both captured fixed-point workloads. No allocation-stack
or compiler-graph attribution was collected for this change, so the measurements
do not assign those bytes to individual object classes.

## Matched results

Apple M3 ARM64 macOS, GraalVM 25.3.4.1, JDK 25.0.4.1, Kotlin 2.4.20, Gradle 9.7.1
and JMH 1.37. Each pair ran serially with two forks, ten one-second warmups and
five one-second measurements per fork, one worker, GC profiling, 128–768 MiB fork
heaps and `-Xss32m`. No project builds ran concurrently. Timing intervals are JMH's
99.9% confidence intervals. Inputs vary and results escape.

| AST workload | Before | After | Before bytes/op | After bytes/op |
|---|---:|---:|---:|---:|
| Fixed-point modular accumulator, 1000–1015 | 5.144 ± 0.041 µs | 4.495 ± 0.011 µs | 224.3 | 56.2 |
| Fixed-point counter, 1000–1015 | 2263.366 ± 10.343 ns | 7.418 ± 0.069 ns | 224.1 | 56.0 |
| Fixed-point counter, 100–115 | 492.604 ± 0.994 ns | 6.433 ± 0.235 ns | 192.0 | 24.0 |
| Fibonacci, 15–18 | 10.876 ± 0.073 µs | 10.849 ± 0.071 µs | 50,172.6 | 50,172.6 |
| Self-tail modular accumulator, 1000–1015 | 2.974 ± 0.041 µs | 2.964 ± 0.009 µs | 56.1 | 56.2 |
| Alternating-root modular accumulator, 1000–1015 | 2.963 ± 0.011 µs | 2.961 ± 0.003 µs | 56.2 | 56.1 |
| Escaping partial application | 26.997 ± 0.329 ns | 26.862 ± 0.415 ns | 224.0 | 224.0 |
| Escaping closure, captures 1000–1015 | 7.722 ± 0.073 ns | 7.656 ± 0.051 ns | 104.0 | 104.0 |

The fixed-point modular sum takes **12.6% less time** (about **1.14× throughput**)
and allocates **75% fewer bytes**. Both ordinary accumulators, Fibonacci, escaping
partial applications and escaping closures retain their allocation and have
overlapping timing intervals. The small counter uses arguments/results within
the Integer cache and allocates 24 rather than 56 bytes per call.

The simple counter's nanosecond timing is consistent with eliminating its loop,
now that recursive construction is visible to partial evaluation. This is a useful
optimization result, but its timing ratio is not a general interpreter speedup.
The counter benchmark checks all 16 varying inputs during setup. The modular-sum
workload performs arithmetic every iteration and independently checks its result
against the closed-form triangular sum; it supplies the useful-work comparison.

## Correctness gate

All **372 tests passed with zero skips**, plus the **10,000-program independent
semantic soak** and generated/compiled histories. Four new regressions cover a
captured physical-arity-four function with a mixed Nat/Bool partial prefix,
original type/environment preservation, source-prefix reuse, and retained native,
large-integer and neutral captures after the shared fixed-point cache saturates.
One verifies installed last-tier code for a guest driver that constructs the
capture and mixed partial prefix, then checks large-integer promotion and concrete
recovery against an independent weighted triangular-sum oracle.
Existing retained-self identity, equality/hash, cloned-root, fallback, concurrency,
compiled-transition and residual-oracle tests also pass.

## Reproduction

Build isolated baseline and candidate revisions with the same current benchmark
harness, then run JMH directly from each runtime classpath without a competing
Gradle daemon. Select each workload separately with `-p backend=ast`, its
`size`/`base` parameter, and:

```text
-wi 10 -i 5 -w 1s -r 1s -f 2 -t 1 -prof gc -foe true
-jvmArgsAppend '-Xms128m -Xmx768m' -rf json -rff results.json
```

[Raw samples and profiler results](fixed-self-binding-data.json) contain every
matched pair. Bytecode performance was not measured; its fixed-point fallback is
unchanged.
