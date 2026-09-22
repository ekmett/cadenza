# Fill application arguments directly

This compares `c56daa580da773fb5b49dddc787c0ad5b0d1f768` with the change
accompanying this report. `Code.App.executeRands` now allocates its result array
once and fills it in left-to-right order. This removes the temporary collection
and conversion used by Kotlin `map(...).toTypedArray()`.

The benchmark harness is identical in both revisions. The new
`GuestInterpretedFib.cadenza` explicitly sets `engine.Compilation=false`: the JVM
warms normally, but Truffle guest compilation is disabled. Ordinary `Fib.cadenza`
retains its existing engine settings. These are separate execution modes.

## Matched results

Apple M3 ARM64 macOS, GraalVM 25.3.4.1 / JDK 25.0.4.1, Kotlin 2.4.20,
Gradle 9.7.1, JMH 1.37. Runs were serial with no concurrent project builds, two
forks, ten one-second warmups and five one-second measurements per fork, one
worker, GC profiling, 128–768 MiB fork heaps and `-Xss32m`. Intervals are JMH's
99.9% confidence intervals. ColdFixedPoint instead uses single-shot time with
ten warmup and twenty measurement invocations per fork; each invocation creates
a context, parses an uncached source, runs and checks the result, and closes the
context. The surrounding JVM is warm.

| AST workload | Before | After | Before B/op | After B/op |
|---|---:|---:|---:|---:|
| Guest-interpreted Fibonacci, 15–18 | 711.597 ± 14.634 us | 720.608 ± 25.397 us | 1,524,180.7 | 1,328,103.0 |
| Guest-interpreted Fibonacci, 10–13 | 67.351 ± 1.571 us | 65.885 ± 1.415 us | 129,837.8 | 119,768.9 |
| Compiled Fibonacci, 15–18 | 11.162 ± 0.237 us | 11.310 ± 0.626 us | 50,172.6 | 50,172.5 |
| Compiled Fibonacci, 10–13 | 1.026 ± 0.028 us | 1.035 ± 0.037 us | 4,480.1 | 4,480.1 |
| Fixed-point modular accumulator, 1000–1015 | 4.541 ± 0.135 us | 4.507 ± 0.017 us | 56.2 | 56.2 |
| Fixed-point counter, 1000–1015 | 7.554 ± 0.340 ns | 7.502 ± 0.081 ns | 56.0 | 56.0 |
| Escaping partial application | 27.351 ± 1.038 ns | 27.288 ± 0.468 ns | 224.0 | 224.0 |
| Escaping capturing closure | 7.830 ± 0.236 ns | 7.914 ± 0.222 ns | 104.0 | 104.0 |
| Self-tail modular accumulator, 1000–1015 | 3.383 ± 0.514 us | 3.238 ± 0.017 us | 56.2 | 56.2 |
| Alternating-root modular accumulator, 1000–1015 | 3.397 ± 0.386 us | 3.205 ± 0.011 us | 56.2 | 56.2 |
| Fresh-context fixed-point parse/run, 5–8 | 1.120 ± 0.242 ms | 1.120 ± 0.224 ms | 98,067.8 | 95,759.0 |

The larger guest-interpreted Fibonacci workload allocates **12.9% less**, and
the smaller one **7.8% less**. Their timing intervals overlap, so these runs do
not establish an interpreter speedup. Compiled Fibonacci keeps its existing
allocation; this change does not remove the packed recursive-call arrays
identified in [the allocation analysis](fib-allocation.md).
All compiled controls retain their allocation and have overlapping timing
intervals. The fresh-context timing intervals also overlap; its allocation
point estimates are included without claiming a resolved cold-path improvement.

## Correctness and scope

All **379 regular tests pass with zero skips**, along with the independent
**10,000-program semantic soak** (both backends). Existing application-effect
tests check all lambda/application groupings, argument and body failure order,
retained captures, large-integer histories and recovery. Neutral residual
oracles, actual compiled transitions, instrumentation and installed-launcher
tests also pass. The loop retains argument order and exception behavior; no
calling convention or neutral representation changes.

The benchmark's existing independent iterative BigInteger oracle checks every
Fibonacci input before timing. The fixed-point and closure controls retain their
existing setup checks. Bytecode runtime code is unchanged and its performance was not gated
by these AST measurements.

## Reproduction

Use the same current benchmark source on both isolated revisions. Select
`GuestInterpretedFib.cadenza` or `Fib.cadenza` with `-p backend=ast -p size=15`
(or `size=10`), then run:

```text
-wi 10 -w 1s -i 5 -r 1s -f 2 -t 1 -prof gc -foe true
-jvmArgsAppend '-Xms128m -Xmx768m' -rf json -rff results.json
```

For `ColdFixedPoint.parseAndRun`, select `size=5` and `-i 20`; the class supplies
single-shot mode. [Raw samples and profiler results](argument-array-data.json)
preserve every matched pair, including all allocation and timing intervals.
