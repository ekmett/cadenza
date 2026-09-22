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

## Deterministic regression checks

With JDK 25 selected by `JAVA_HOME` or `PATH`, and Python 3 available:

```sh
tools/test-allocation-summary.sh
```

This standalone check uses only the JDK and Python standard library. It compiles
the analyzer and a small fixture generator into a temporary directory, runs the
real command-line analyzer, and parses its output with Python's independent JSON
parser. Python runs in isolated mode so `PYTHONOPTIMIZE` cannot disable assertions.
No Gradle build or language runtime is involved; temporary files are removed on exit.

The recordings are **synthetic test data, not allocation measurements**. Custom
JFR events use the relevant JDK event names and explicitly assigned weights and
sizes. Only their custom event IDs are enabled. The generator verifies the exact
event counts and verifies that newer samples really precede the chronological
first sample in recording read order before testing the correction.

The fixture checks:

- Per-thread chronological first-sample exclusion, independently of read order
  and largest weight; raw and corrected counts and byte weights reconcile.
- Distinct classes at the same stack, repeated identical sites, multiple sites
  for one class, missing stacks, and retention of the twelve largest samples.
- Chronologically sorted allocation counters and separate triggering-object
  sizes, TLAB capacities, and outside-TLAB size events.
- JSON control characters, quotes, backslashes, Unicode, a thread whose only
  sample is excluded, and empty recordings in both reporting modes.
- A separate sample emitted under 128 recursive frames with JFR stack depth set
  to 32: the generator verifies actual truncation, and the assertions check its
  weight, retained frames, and first-sample exclusion independently of missing stacks.

Sensitivity was checked on 2026-09-22 with two isolated analyzer mutations:
choosing the first sample in file order, and choosing each thread's largest sample.
Both failed the corrected-weight assertion, including with `PYTHONOPTIMIZE=2` set.
The production analyzer was not modified for those checks.

These checks validate the analyzer's bookkeeping. They do not establish accuracy
or coverage of real JFR allocation sampling or repair truncated recorded stacks.
