# Attribute warmed Fibonacci allocation before changing its calling convention

At `acff5e7f3e4251336f838173f0711a9dc0774441`, AST Fibonacci allocates about
50,173 bytes per operation while cycling through inputs 15–18. Warm JFR recordings
and an isolated size perturbation attribute approximately **50,048 bytes (99.75%)**
to arrays constructed by `appendLSkip` for physical calls. This is attribution
evidence, not a runtime optimization. The diagnostic padding was never applied
to the production checkout.

The guest body has no lexical captures, reuses its fixed-point self closure, and
makes two non-tail recursive calls whose results are added. The physical argument
array is `[0L, selfToken, x]`; the leading value is the tail-call bloom state.

## Recording and startup correction

Same Apple M3 ARM64 macOS host, GraalVM 25.3.4.1 / JDK 25.0.4.1 and JMH 1.37 as
the preceding performance reports. Two independent one-fork runs used ten
one-second warmups and six two-second measurements, one worker, 128–768 MiB fork
heaps, `-Xss32m`, GC profiling, and JFR allocation sampling at 1000/s with stack
depth 128. JMH starts JFR immediately before its first measurement and stops it
after the last. No build or competing benchmark ran concurrently.

GC profiling reported **50,183.86 / 50,183.79 B/op**, close to the unprofiled
50,172.56 B/op. Profiled execution times are not used for a speed claim.

The first worker sample in each recording included allocation from before
recording began. Run 1 assigned **46,149,871,320 bytes** to an Object array; run 2
assigned **45,691,390,816 bytes** to a JMH control iterator. Treating the latter as
guest allocation would incorrectly attribute almost half the run to that iterator.
The corrected analysis excludes exactly the first chronological sample per thread
and preserves its class, timestamp, weight and recorded stack. Subsequent worker
weights and allocation-counter rates agree in scale, although their intervals
have different endpoints.

| Corrected worker samples | Run 1 | Run 2 |
|---|---:|---:|
| Retained events | 11,918 | 12,087 |
| Retained byte weight | 53.55 GB | 53.81 GB |
| Object array share | 98.3535% | 99.8503% |
| Integer share | 1.6465% | 0.1497% |
| Truncated-stack weight | 99.6570% | 99.7627% |
| Missing-stack weight | 0% | 0% |

The rare Integer estimate is unstable: one heavily weighted event dominates its
first-run estimate. Both runs establish array dominance; they do not establish a
precise boxing percentage. No worker samples contained tail-call exceptions,
closures, fixed-point cells, captured-frame storage, pairs, or frame objects.
Absence from a sample does not prove zero allocation.

The dominant recorded locations were `Code.Var.execute` and
`resolveFixedArgument`, neither of which creates arrays in the source. Their
stacks pass through compiled/inlined guest calls. These locations alone cannot
identify the original array-construction expression, and most deep recursive
stacks hit the recording limit.

## Object sizes and causal probe

A separate short recording retained normal TLABs and escape analysis, but enabled
`ObjectAllocationInNewTLAB` and `ObjectAllocationOutsideTLAB`, with stack depth 32
and two one-second measurements after ten warmups. Both dominant locations
reported **32-byte Object arrays**: 8,628 and 8,488 new-TLAB events, plus 50 and 44
outside-TLAB events. Integer events were 16 bytes; three benchmark-entry array
events were 24 bytes. These are recorded refill/large-allocation events, not a
census. TLAB capacity was never counted as bytes allocated by its triggering class.

To identify the source expression, an isolated archive changed only the array
length in `appendLSkip`:

```kotlin
val zs = arrayOfNulls<Any>(skip + xsSize + ysSize) // baseline
// Diagnostic variants add +2 or +4 unused trailing null slots.
```

Each variant used the same checked benchmark harness. Setup compared all four
inputs with an independent iterative Fibonacci oracle: 610, 987, 1597, 2584.
All twelve checks passed. Each serial run used one fork, ten one-second warmups,
three one-second measurements, and the same heap/stack settings and GC profiler.

| Diagnostic variant | Bytes/op | Increase |
|---|---:|---:|
| Original array | 50,172.723 | — |
| Two extra slots: 32 → 40 bytes | 62,684.875 | 12,512.152 |
| Four extra slots: 32 → 48 bytes | 75,196.919 | 25,024.196 |

The second 8-byte increment adds 12,512.044 B/op, closely matching the first.
The linear model implies about **1,564 surviving arrays/op**, **50,048 original
array bytes/op**, and **124 other bytes/op**. These are workload-level estimates;
they are not exact counts for every source-level recursive call. The source and
JVM bytecode checks isolate the changed length expression, but no claim of
identical native compiler graphs is made. The short diagnostic timings are not
production performance comparisons.

This evidence directs further work toward recursive call packing and inlining,
rather than changing the deliberately exceptional neutral path or capture storage.
Fibonacci's permanent benchmark setup now independently checks all four inputs
using host BigInteger arithmetic.

## Reproduction and retained data

Use the installed JDK's `jfr configure` with `lib/jfr/profile.jfc`,
`allocation-profiling=high`, `jdk.ThreadAllocationStatistics#period=1 s`, and
execution/native-method sampling disabled. For the size diagnostic, additionally
enable both allocation-size events and their stack traces. Exact JMH commands,
GC samples, corrected thread/class/full-stack summaries and excluded first samples
are in [the JFR data](fib-allocation-jfr-data.json). Separate output directories
are necessary because JMH's JFR profiler reuses `profile.jfr` across forks.

[The padding-probe data](fib-allocation-probe-data.json) contain raw JMH results,
exact isolated patches, the checked-harness hash and derived allocation slopes.
The patches are diagnostic artifacts, not proposed runtime changes.

[The standalone analysis tool](../../../tools/CadenzaAllocationSummary.java)
streams JFR metadata and aggregates weights without retaining all recorded events:

```sh
java -Xms32m -Xmx256m tools/CadenzaAllocationSummary.java \
  --exclude-first-per-thread recording.jfr > summary.json
```

Use the first-sample exclusion only after inspecting the startup weights and
counters. [Tool guidance](../../../tools/README.md) explains the distinct meanings
of sampled weights, allocation counters and TLAB-size events.
