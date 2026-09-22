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

## Longer Fibonacci validation

A follow-up on runtime `3d9ee9a` used five forks, ten one-second warmups and five
one-second measurements per fork. It stabilized at **14.529 ± 0.218 µs/op** and
**62,145 bytes/op** (25 measurement samples). That is about 4.49× faster than the
original 65.205 µs baseline, with 91.7% less allocation. The longer warmup also
reduced allocation from the first follow-up; the original comparison and its
settings remain above rather than silently replacing those samples. Raw data
is recorded under `fib-stability` in the accompanying JSON.

## Noncapturing closure reuse

The next follow-up caches an immutable closure on each noncapturing lambda node.
`NoncapturingClosure.select` alternates between two returned functions using a
runtime argument, so the returned value varies and escapes. With the standard
two-fork settings, allocation fell from **72 to 40 bytes/op** against `3d9ee9a`
using the same new harness. The remaining allocation includes the benchmark's
boxed input and call argument array. Capturing lambdas still allocate their own
environment and closure.

The new version measured **6.19 ± 0.08 ns/op**. Baseline timing was unstable
(23.46 ± 35.30 ns/op), so this run establishes the 32-byte allocation reduction
without supporting a precise throughput speedup. Both raw runs are included as
`noncapturing-before` and `noncapturing-after` in the accompanying JSON.

## Correctness follow-up checks

The second batch passes 275 tests and a clean distribution build. It fixes parser
boundaries and diagnostics, large literals, complete integer arithmetic and
overflow handling, launcher result rendering, exact floating-point interop, and
neutral propagation through overapplication, conditionals, and builtin results.
The exceptional neutral protocol is preserved.

With ten one-second warmups and five measurements in each of two forks, the
modular accumulator measured 3.108 ± 0.251 µs/op at 56 bytes/op. The fixed-point
counter measured 22.248 ± 2.149 µs/op at 110,961 bytes/op. An immediate control
run of the previous commit `3d9ee9a` with the identical harness and settings was
also slower than the earlier session: 21.703 ± 3.620 µs/op at approximately
110,962 bytes/op. These overlapping intervals do not isolate a counter slowdown
caused by this batch; the session's timing shift limits comparisons with earlier
runs. Allocation remains unchanged. Raw runs are `batch2-loops` and
`batch2-add-control`.

Fibonacci shows the same session shift in a matched check: **28.822 ± 1.150 µs**
for the new batch versus **29.099 ± 2.100 µs** for `3d9ee9a`, both approximately
64,406 bytes/op. These results likewise show no resolved timing difference
between the two revisions under current conditions. The runs appear as
`batch2-fib` and `batch2-fib-control`.
