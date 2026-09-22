# Frame recovery experiment: rejected

We tested replacing shared descriptor-kind widening with per-frame tags using
Truffle's `FrameDescriptor.Builder.useSlotKinds(false)`. The candidate selected
Int/Boolean/Object storage on each write and explicitly cleared old object
references before primitive writes. Correctness tests passed, but the change
did not improve the measured workload and increased allocation after exceptional
values. The production change was therefore reverted before committing.
The [candidate patch](frame-recovery-candidate.patch) against `f0a954d` is retained
for reproduction; it is not applied to the runtime.

`PoisonedFrame` uses the same two-argument recursive accumulator and call site
throughout each trial. Setup optionally supplies a BigInt or typed neutral with
zero iterations, which changes frame-storage history without running arithmetic
on that value. It then verifies all 16 concrete inputs against an independent
Kotlin loop. Measurement varies the remaining count from 1000 to 1015.

The baseline is `f0a954d`; the candidate additionally has the pending boundary
and source-entry fixes, which the direct guest benchmark does not exercise.
Both runs use GraalVM 25.3.4.1 on Apple M3 ARM64 macOS, two fresh JVM forks,
ten one-second warmup iterations, five one-second measurements, one worker,
GC profiling, 128–768 MiB fork heaps, and `-Xss32m`. All runs were serialized.

| Prior frame history | Baseline time | Candidate time | Baseline bytes/op | Candidate bytes/op |
|---|---:|---:|---:|---:|
| Concrete Int only | 3.554 ± 0.101 µs | 3.524 ± 0.011 µs | 72 | 72 |
| One BigInt return | 3.509 ± 0.008 µs | 3.526 ± 0.014 µs | 72 | 88 |
| One neutral return | 3.522 ± 0.047 µs | 3.541 ± 0.034 µs | 72 | 88 |

± is JMH's 99.9% confidence-interval half-width.
[Raw measurements](frame-recovery-data.json) include all samples.

Shared Object storage did not impose per-iteration allocation in this optimized
workload: Graal eliminated it. The candidate's additional 16 bytes per invocation
are consistent with one additional escaping Integer box, but that allocation site
was not directly profiled. Primitive recovery and immutable descriptor metadata
alone do not justify this measured regression. Interpreter-only execution and
instrumented/materialized-frame workloads were not measured.

The benchmark remains available to assess future changes:

```sh
./gradlew bench --args='cadenza.bench.PoisonedFrame.accumulate -p size=1000 -p history=clean,bigint,neutral -wi 10 -i 5 -w 1s -r 1s -f 2 -t 1 -prof gc -foe true -jvmArgsAppend "-Xms128m -Xmx768m"'
```
