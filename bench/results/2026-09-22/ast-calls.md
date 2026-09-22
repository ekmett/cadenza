# AST calling follow-up

Compared with `7b8a0b3`, physically binary AST functions passed to `fixNatF` now
call their original function target directly. A retained private cell supplies
the recursive closure without cyclic equality or hashing. Other calling shapes
retain the wrapper. Split roots recognize the same logical body and keep their
self-tail loop; generic partial application copies its remaining arguments once.

These are matched runs on the same M3/GraalVM 25.3.4.1 machine: two forks, ten
one-second warmups and five one-second measurements per fork, one thread, GC
profiler. Each before/after pair uses the identical benchmark harness. JMH ran
standalone with a 128 MB runner heap and 768 MB fork heap; build daemons were
stopped. Errors below are JMH's 99.9% confidence intervals. [Raw data](ast-calls-data.json).

| AST workload | Before time | After time | Before bytes/op | After bytes/op |
|---|---:|---:|---:|---:|
| `fixNatF` counter, limit 1000–1015 | 17.887 ± 1.299 µs | 5.650 ± 0.285 µs | 110,961 | 16,360 |
| Fibonacci, input 15–18 | 33.353 ± 8.456 µs | 23.631 ± 3.438 µs | 74,730 | 50,173 |
| Modular accumulator, limit 1000–1015 | 4.622 ± 1.076 µs | 4.438 ± 0.108 µs | 56 | 56 |
| Escaping captured closure, input 1000–1015 | 16.00 ± 1.43 ns | 17.06 ± 2.40 ns | 104 | 104 |
| Escaping partial application, input 1000–1015 | 55.28 ± 8.11 ns | 74.17 ± 34.16 ns | 248 | 224 |

The counter improved about 3.17× in this comparison and allocated 85.3% less.
This simple loop is still not a general interpreter throughput measure. Fibonacci
also allocated less, although its baseline allocation and timings vary with
compilation state. Timing intervals overlap for the other workloads; the partial
application change establishes a 24-byte allocation saving, not a speedup.

Machine load and memory pressure affected this session. An initial run with the
earlier default heap configuration became severely unstable; it is archived as
`initial-uncontrolled-allocation` but excluded from this table. The bounded-heap
pairs reduce that confound, but their absolute timings should not be compared
directly with earlier sessions or different heap settings. Keep these limits in
mind even when confidence intervals are narrow.

`PartialApplication` chooses among four curried functions at one call site,
forcing the generic dispatch fallback. Runtime arguments vary, and the returned
partial closure escapes to JMH. Trial setup finishes all sixteen input variants
and checks their answers before measurement.

Validation includes real forced Truffle splits, frame reuse while captures and
partial arguments change, distinct bodies under saturated bloom filters,
fixed-point identity/equality/hash behavior, and shared contexts. A separate
deterministic differential generator compares typed programs against an
independent BigInteger and lexical-closure evaluator on both backends. The full
287-test build and installed distribution pass, including a 10,000-program
differential soak (20,000 backend evaluations). The normal CI default is 160
programs; test-only `cadenza.fuzz.cases` and `cadenza.fuzz.seedOffset` properties
allow larger, reproducible runs.
