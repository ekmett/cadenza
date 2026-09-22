# Measuring Cadenza

Run with the documented GraalVM `JAVA_HOME`, using the checked-in Gradle wrapper:

```sh
./gradlew bench --args='-l'
./gradlew bench --args='cadenza.bench.(Add|Fib)\..* -wi 5 -i 5 -f 2'
./gradlew bench --args='cadenza.bench.AddLet.* -wi 5 -i 5 -f 2'
./gradlew bench --args='cadenza.bench.CapturedClosure.* -prof gc'
./gradlew bench --args='cadenza.bench.NeutralNormalization.*'
./gradlew bench --args='cadenza.bench.ColdStart.*'
```

The `backend` parameter runs the AST and experimental Bytecode DSL backends
separately; use `-p backend=ast` or `-p backend=bytecode` to select one. The Kotlin
and reference interpreter baselines do not depend on the selected backend.
Neutral normalization stays on the AST backend.

`Add` and `Fib` compare warm guest execution, the small reference interpreter,
and Kotlin with the same inputs and results. Each trial owns a thread-local
context, enters it during setup, and leaves/closes it during teardown. Parsing
and initial closure construction occur in setup. The benchmark returns every
result to JMH. Inputs cycle through a small range from a mutable benchmark-state
field and enter the guest through frame arguments, preventing constant folding
of an entire closed benchmark program. `Add` and Kotlin both stop at the limit;
the old Kotlin baseline performed one extra increment. `AddLet` measures
recursive let independently because the reference interpreter does not support
that construct.

`CapturedClosure` measures escaping closure creation; use JMH's GC profiler to
compare bytes allocated per operation as well as throughput. `NeutralNormalization`
measures the deliberately exceptional, temporary neutral-term path separately
from ordinary execution. `ColdStart` includes fresh context creation, uncached
source parsing, execution, and shutdown. Its single-shot timings are distinct
from the warm steady-state timings; they are JVM-process-warm, not operating
system process startup measurements.

Small `-wi 0 -i 1 -w 100ms -r 100ms -f 0` runs are smoke tests, not performance
measurements. For comparisons record the GraalVM build, CPU, OS, commit, JMH
arguments, and GC profiler output, and use multiple forks. Context/host-entry
costs are intentionally excluded from the warm guest benchmark (it uses a
rooted guest dispatch); cold timings include them. The Kotlin baseline may
optimize the addition loop more aggressively; it is a reference computation,
not a promise that the two implementations execute identical machine code.
