# AST execution after the tooling changes

This compares `8d1c320262e4b20dbc430bbad90e86a05240eacb` with the implementation
accompanying this report: correct invocation-level RootTag wrappers, statement
units at program and closure-body entry, and precise type-error locations.
No execution listeners or statement limits were installed during measurement.
This checks ordinary execution cost, not the overhead of active instrumentation.

Same Apple M3 ARM64 macOS host, GraalVM 25.3.4.1, JDK 25.0.4.1, Kotlin 2.4.20,
Gradle 9.7.1 and JMH 1.37. Each before/after pair ran serially with two JVM forks,
ten one-second warmup iterations and five one-second measurements per fork,
one worker, GC profiling, 128–768 MiB fork heaps, and `-Xss32m`. No project builds
ran concurrently. Inputs vary at runtime and results escape to JMH.

| AST workload | Before | After | Before bytes/op | After bytes/op |
|---|---:|---:|---:|---:|
| `fixNatF` counter, 1000–1015 | 3.441 ± 0.027 µs | 3.431 ± 0.036 µs | 16,360 | 16,360 |
| Fibonacci, 15–18 | 10.834 ± 0.045 µs | 10.717 ± 0.233 µs | 50,172 | 50,172 |
| Modular accumulator, 1000–1015 | 3.132 ± 0.200 µs | 2.989 ± 0.052 µs | 56 | 56 |
| Escaping closure, captures 1000–1015 | 7.780 ± 0.222 ns | 7.976 ± 0.471 ns | 104 | 104 |

These measurements resolve no ordinary-execution regression. Allocation is
unchanged, and the 99.9% confidence intervals overlap for every timing pair.
[Raw samples](tooling-ast-data.json) include all profiler results.

Absolute timings are faster than some earlier sessions even for the unchanged
baseline, illustrating this host's load variation. Compare matched pairs within
this table; do not attribute differences from earlier reports to this change.
Bytecode performance and active instrumentation overhead were not measured here.

Functional validation comprised 349 passing tests, including installed-code
transition checks, real instrumentation/unwind tests and statement-budget
cancellation on both backends, plus a 10,000-program independent-oracle soak.

## Reproduction

Build isolated copies of the baseline and candidate and run the same benchmark
runtime classpath directly, avoiding a competing Gradle daemon. Select each
workload separately with `-p backend=ast`, its size/base parameter, and:

```text
-wi 10 -i 5 -w 1s -r 1s -f 2 -t 1 -prof gc -foe true
-jvmArgsAppend '-Xms128m -Xmx768m' -rf json -rff results.json
```
