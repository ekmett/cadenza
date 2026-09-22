# Restore primitive captures without an intermediate object merge

This compares `2e4d5fcb66386f79980b3de4b60ac85a5b05c0ed` with the capture
restoration change accompanying this report. Each matched pair used the same
benchmark source and runtime settings.

A closure previously read a captured value as `Any?`, merging the primitive and
object alternatives, then wrote that merged value into its frame. The new
`CaptureLayout.restore` writes to the frame inside each alternative. This lets
partial evaluation see the primitive type at its frame write. The public capture
readers, frame widening policy, calling convention, partial-application boundary,
and deliberate neutral exception path are unchanged.

The hypothesis was one boxed captured loop bound per body entry in the fixed-point
counter. Its mean input is 1007.5, with one extra terminating body entry:
`16 × 1008.5 = 16,136` bytes. Moving only the restoration writes eliminated exactly
that allocation in both the counter and a fixed-point loop doing a modular sum.
This supports the hypothesis; no allocation-stack or compiler-graph attribution
was collected. The small counter uses bounds within the JVM Integer cache and
retains its original allocation.

## Matched results

Same Apple M3 ARM64 macOS host, GraalVM 25.3.4.1, JDK 25.0.4.1, Kotlin 2.4.20,
Gradle 9.7.1 and JMH 1.37. Each pair ran serially with two forks, ten one-second
warmups and five one-second measurements per fork, one worker, GC profiling,
128–768 MiB fork heaps and `-Xss32m`. No project builds ran concurrently. Timing
intervals are JMH's 99.9% confidence intervals. Inputs vary and results escape.

| AST workload | Before | After | Before bytes/op | After bytes/op |
|---|---:|---:|---:|---:|
| Fixed-point counter, 1000–1015 | 3.526 ± 0.091 µs | 2.262 ± 0.006 µs | 16,360.2 | 224.1 |
| Fixed-point counter, 100–115 | 0.542 ± 0.002 µs | 0.490 ± 0.001 µs | 192.0 | 192.0 |
| Fixed-point modular accumulator, 1000–1015 | 6.780 ± 0.072 µs | 5.185 ± 0.005 µs | 16,360.3 | 224.3 |
| Fibonacci, 15–18 | 10.882 ± 0.096 µs | 10.861 ± 0.077 µs | 50,172.5 | 50,172.5 |
| Self-tail modular accumulator, 1000–1015 | 2.969 ± 0.014 µs | 2.966 ± 0.007 µs | 56.1 | 56.1 |
| Alternating-root modular accumulator, 1000–1015 | 2.963 ± 0.005 µs | 2.963 ± 0.003 µs | 56.1 | 56.1 |
| Escaping closure, captures 1000–1015 | 7.647 ± 0.031 ns | 7.679 ± 0.028 ns | 104.0 | 104.0 |

The large counter is about **1.56× faster**, and the fixed-point modular sum is
about **1.31× faster** in these matched runs. Both allocate **98.6% fewer bytes**.
Fibonacci, both ordinary recursive accumulators, and escaping closures have
overlapping timing intervals and unchanged allocation.
The small counter also improves in time, while its allocation remains unchanged.

`FixedAccumulate` encodes the index and running sum in one natural-number state
because `fixNatF` currently accepts a unary natural-number function. It captures
the varying limit, performs arithmetic every step, and checks the result against
a closed-form sum during setup. It provides evidence beyond the simple counter;
these measurements are not a claim of a universal interpreter speedup.

[Raw samples and profiler results](capture-restore-data.json) include all seven
pairs. Bytecode execution performance was not measured; this change affects the
AST closure environment setup.

## Correctness gate

All **365 tests passed with zero skips**, plus the **10,000-program independent
semantic soak** and its generated-function/compiled execution checks. Four direct
restoration tests cover primitive and object snapshots, materialized frames,
widened destinations and older frame tags, unforced neutral and recursive values,
and cloned roots using different captured environments. Existing concurrent,
split-root, compiled-transition and capture tests also pass.

## Reproduction

Build isolated baseline and candidate revisions with the same current benchmark
harness, then run JMH directly from each runtime classpath without a competing
Gradle daemon. Select each workload separately with `-p backend=ast`, its
`size`/`base` parameter, and:

```text
-wi 10 -i 5 -w 1s -r 1s -f 2 -t 1 -prof gc -foe true
-jvmArgsAppend '-Xms128m -Xmx768m' -rf json -rff results.json
```
