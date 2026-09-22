# First AST allocation follow-up

This batch removes the extra StaticShape capture wrapper, shares empty partial
argument arrays, retains each fixed point's recursive closure, and lets the
fixed-point root participate in bounded direct tail-call unrolling. It also
repairs lexical slot shadowing and replaces recursive-binding call thunks with
cells forced at variable reads. Neutral terms still use `SlowPathException`.

The [initial comparison](README.md) supplies the baseline. Same M3/GraalVM/JMH
configuration: two forks, five one-second warmups and five one-second measurements
per fork, GC profiler, varying arguments. [Raw follow-up samples](ast-followup-data.json).

| AST workload | Before time | After time (99.9% CI) | Before bytes/op | After bytes/op |
|---|---:|---:|---:|---:|
| Escaping closure, captures 1000–1015 | 8.14 ns | 7.68 ± 0.25 ns | 136 | 104 |
| `fixNatF` counter, limit 1000–1015 | 11.984 µs | 8.754 ± 0.746 µs | 191,737 | 110,960 |
| Fibonacci, input 15–18 | 65.205 µs | 17.282 ± 8.101 µs | 748,075 | 70,794 |
| Modular accumulator, limit 1000–1015 | 3.285 µs | 3.285 ± 0.104 µs | 56 | 56 |

The capture regression is removed: 104 bytes/op also matches the pre-modernization
AST baseline. Counter allocation fell 42%; Fibonacci allocation fell about 90%.
Fibonacci timing was noisy (individual measurement iterations 14–31 µs), so the
allocation reduction is more reliable than a precise speedup claim. Other tests
of stability and larger workloads are warranted before generalizing.

A clean build, application distribution, and benchmark compilation passed, along
with 93 tests. New regressions cover mixed capture fields and contexts, retained
recursive self identity, saturated fixpoint caches, alternating tail calls,
lexical shadowing, recursive initialization errors, declared-type checking, and
context-local output. This batch changes no source-level numeric semantics.
