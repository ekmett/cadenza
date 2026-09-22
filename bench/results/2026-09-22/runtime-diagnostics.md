# Runtime diagnostics: ordinary AST execution cost

This compares `d1cdd30f57c9829f94cde068917e37f97cc3440a` with the runtime
implementation accompanying this report. Runtime errors now retain precise source
locations, including first-class builtin calls across a tail-call transfer.
The common tail-call exception keeps its existing fields; a specialized builtin
exception carries the source fallback. The trampoline retains the exception
until the dispatched call completes.

The added `MutualAccumulate` workload alternates between two recursive roots,
exercising the general tail-call trampoline. It performs a modular sum at every
step and checks results against a closed-form oracle during setup. This covers
the trampoline change beyond the existing self-tail workloads.

Both revisions used the identical updated harness on the same Apple M3 ARM64
macOS host, GraalVM 25.3.4.1, JDK 25.0.4.1, Kotlin 2.4.20, Gradle 9.7.1 and
JMH 1.37. Each pair ran serially, with two forks, ten one-second warmups and five
one-second measurements per fork, one worker, GC profiling, 128–768 MiB fork
heaps and `-Xss32m`. No project builds ran concurrently. Timing intervals are
JMH's 99.9% confidence intervals. Results escape and inputs vary at runtime.

| AST workload | Before | After | Before bytes/op | After bytes/op |
|---|---:|---:|---:|---:|
| Alternating-root modular accumulator, 1000–1015 | 2.962 ± 0.007 µs | 2.960 ± 0.003 µs | 56.1 | 56.1 |
| `fixNatF` counter, 1000–1015 | 3.494 ± 0.078 µs | 3.427 ± 0.020 µs | 16,360.2 | 16,360.2 |
| Fibonacci, 15–18 | 10.866 ± 0.048 µs | 10.859 ± 0.054 µs | 50,172.6 | 50,172.5 |
| Self-tail modular accumulator, 1000–1015 | 2.961 ± 0.004 µs | 2.969 ± 0.019 µs | 56.1 | 56.2 |
| Escaping closure, captures 1000–1015 | 7.680 ± 0.046 ns | 7.644 ± 0.065 ns | 104.0 | 104.0 |

No ordinary-execution regression is resolved by these measurements. Every timing
pair has overlapping confidence intervals, and allocation differs by at most
0.2 bytes/op, within profiler measurement noise.

[Raw samples and profiler results](runtime-diagnostics-data.json) retain every
measurement. These are successful guest executions: they measure the ordinary
runtime cost of the diagnostic changes, not error construction or reporting cost.
Bytecode execution performance was not measured here.

Validation included 361 passing tests, zero skips, and the 10,000-program oracle
soak. The final error-only BytecodeLocation source-replay adjustment passed the
12 focused runtime-diagnostic and bytecode-instrumentation tests afterward.

## Reproduction

Build isolated revisions with the same benchmark harness. Invoke JMH from their
runtime classpaths, avoiding a competing Gradle daemon. Select each workload
separately with `-p backend=ast`, its `size`/`base` parameter, and:

```text
-wi 10 -i 5 -w 1s -r 1s -f 2 -t 1 -prof gc -foe true
-jvmArgsAppend '-Xms128m -Xmx768m' -rf json -rff results.json
```
