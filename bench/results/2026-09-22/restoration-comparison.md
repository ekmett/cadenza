# Restored runtime versus the latest committed improvements

This comparison starts at `26887c4b6871dd827e865cb90417c2d5c3c4f1e6`, immediately
after restoration and the Kotlin/Gradle upgrades, before runtime improvements.
The endpoint is `f0a954defec85eca7b51e3681fcc12207b1d1984`. Pending frame-storage,
numeric-import, and source-entry changes are excluded.

Both revisions were extracted into separate directories and built with the same
current benchmark harness. Only the unsupported backend option was removed from
the restored harness, and its backend parameter restricted to AST. Guest programs
and varying inputs are identical. Kotlin 2.4.20, Gradle 9.7.1, GraalVM 25.3.4.1,
JDK 25.0.4.1, JMH 1.37, Apple M3 ARM64 macOS, 16 GiB RAM.

Each configuration used two fresh JVM forks, ten one-second warmup iterations,
five one-second measurements, one worker, and the GC profiler. Fork heaps were
bounded to 128–768 MiB; the JMH runner used 64–128 MiB. Both used `-Xss32m` and
`--enable-native-access=ALL-UNNAMED`. Restored/latest pairs ran serially, with no
concurrent project builds. Other user applications remained running.

| Workload | Restored | Latest committed | Restored bytes/op | Latest bytes/op |
|---|---:|---:|---:|---:|
| Modular accumulator, 1000–1015 iterations | 18.650 ± 3.027 µs | 4.290 ± 0.299 µs | 110,777 | 56 |
| Escaping closure, captures 1000–1015 | 22.412 ± 5.818 ns | 14.308 ± 0.411 ns | 104 | 104 |

The accumulator is **4.35× faster in this matched run**, with **99.95% less
allocation**. Capturing closure allocation matches restoration; the temporary
regression introduced during modernization has been removed. Closure creation
measured faster here, but earlier matched runs found similar old/new times.
Treat that timing improvement cautiously: this host has shown substantial load
and memory-pressure variation. The allocation results are more repeatable.

The ± values are JMH's 99.9% confidence-interval half-widths.
[Raw samples and profiler results](restoration-comparison-data.json) preserve
every measurement. These timings should not be combined with earlier sessions
to calculate additional speedups: heap settings and host load differed.

Two additional restored-runtime smoke runs failed, so they have no valid speedup:

* The identical `fixNatF` counter (`Add`, size 1000) fails with
  `FrameSlotTypeException: Frame slot kind Long expected, but got Object at frame
  slot index 0`.
* The identical partially applied Fibonacci workload (`Fib`, size 15) fails
  parsing/type-directed elaboration at the end of the expression. An earlier
  saturated-wrapper experiment also failed with the frame-slot exception.

Restoration therefore gives a valid quantitative baseline for the accumulator
and escaping captures, plus evidence of newly working workloads. It does not
support an overall Fibonacci or counter speedup claim.

## Reproduction

Use the isolated revisions and shared harness described above. Run each
configuration with the following JMH options, using a separate result file:

```text
cadenza.bench.Accumulate.cadenza -p backend=ast -p size=1000
cadenza.bench.CapturedClosure.allocate -p backend=ast -p base=1000

-wi 10 -i 5 -w 1s -r 1s -f 2 -t 1 -prof gc -foe true
-jvmArgsAppend '-Xms128m -Xmx768m' -rf json -rff results.json
```

Run JMH directly from the benchmark runtime classpath to avoid a competing
Gradle daemon. Check failed forks as well as the launcher exit status.
