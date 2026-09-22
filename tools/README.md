# Allocation recording analysis

`CadenzaAllocationSummary.java` is a standalone JDK 25 source-file tool. It reads
JFR files through the JDK consumer API; it is not part of the language or Gradle
build and needs no additional dependencies.

```sh
java -Xms32m -Xmx256m tools/CadenzaAllocationSummary.java recording.jfr > summary.json
java -Xms32m -Xmx256m tools/CadenzaAllocationSummary.java \
  --exclude-first-per-thread recording.jfr > corrected-summary.json
```

The JSON retains allocation sample weights by thread, class and full recorded
stack, including method descriptors, source lines, bytecode indices, and missing
or truncated stack coverage. It also records allocation counters, each thread's
first sample, the twelve largest samples, and separate allocation-size events.
Only event metadata and aggregates are retained, rather than all recorded events.

Use **sample byte weights**, not event counts, to estimate allocation pressure.
Filter the intended benchmark worker before attributing allocations to guest
execution. Keep compiler, profiler and control threads separate. Sampled weights
are estimates; sparse classes can have unstable percentages between recordings.

When sampling starts after warmup, the first sample can carry bytes allocated
before recording began. Verify that against its timestamp, stack and allocation
counters before using `--exclude-first-per-thread`. This option explicitly drops
one chronological allocation sample per thread while retaining its metadata for
inspection. The default includes every sample.

`ObjectAllocationInNewTLAB` describes the object that triggered a new allocation
buffer, not every object allocated in that buffer. Its `allocationSize` can help
identify an object layout. Neither its event count nor its `tlabSize` is a census
of that class's allocations. The output keeps buffer capacity separate from
recorded object sizes. `ObjectAllocationOutsideTLAB` is also reported separately.

See the [Fibonacci attribution report](../bench/results/2026-09-22/fib-allocation.md)
for the actual recording settings, startup-weight correction and limitations of
compiled stack attribution.
