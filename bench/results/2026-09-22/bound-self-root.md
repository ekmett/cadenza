# Rejected unary fixed-self entry

A private unary entry for noncapturing fixed-point bodies reduced warmed Fibonacci
allocation, but made execution about 2.5–2.6 times slower in the matched runs.
The candidate was removed. The production runtime remains the implementation at
`05054d5d85f5aa782d15543fed87d6bec30c2998`.

The experiment kept each public closure's original call target, fixed-self token,
partial arguments, equality and hash semantics. Only the bounded cached `fixNatF`
path could create a unary entry, for an AST body with physical arity two, no
captures and no existing partial arguments. Its root owned a deep copy of the
invocation subtree, a copied frame descriptor and a fresh logical body identity.
An exact-call specialization supplied `[bloom, argument]`; generic calls retained
the original binary convention. Split unary roots shared only their own binding's
logical identity. Both direct caches remained bounded at three entries.

| Warmed workload | Baseline µs/op | Candidate µs/op | Baseline B/op | Candidate B/op |
|---|---:|---:|---:|---:|
| Fibonacci 15–18 | 11.368 ± 1.856 | 28.832 ± 0.269 | 50,172.55 | 41,655.36 |
| Fibonacci 10–13 | 0.993 ± 0.010 | 2.631 ± 0.148 | 4,480.05 | 3,743.13 |

The larger baseline timing is variable, but both timing comparisons have clearly
separated intervals. Mean allocation falls by 17.0% and 16.4%, respectively.
The smaller candidate's allocation is also variable (±292 B/op); raw samples are
retained. A smaller physical array did not deliver the predicted 25% total saving,
and throughput regressed decisively. Native compiler graphs and allocation stacks
were not collected for this candidate, so these results do not establish the
specific compiler decision responsible for the slowdown.

The retained `ColdFixedPoint` benchmark checks uncached parsing, fixed-point
construction and first execution of Fibonacci 5–8 in fresh contexts. Its results
were **1.083 ± 0.153 → 1.148 ± 0.242 ms/op**, with **98,087 → 99,343 B/op**.
Timing and allocation intervals overlap; this short, context-inclusive experiment
does not establish a startup regression or an exact cost per extra root. The JVM
process is warm, and context shutdown plus an independent result check are included.

## Method and correctness

Baseline: `05054d5d85f5aa782d15543fed87d6bec30c2998`, with the same added cold
benchmark on both sides. Candidate: the [unapplied patch](bound-self-root-candidate.patch).
Hardware/runtime: Apple M3, macOS ARM64, GraalVM 25.3.4.1,
JDK 25.0.4.1+1, JMH 1.37. Builds and benchmark forks ran serially.

Warmed pairs used two forks, one thread, 10 × 1 s warmup, 5 × 1 s measurement,
the GC profiler, 128–768 MiB fork heaps and a 32 MiB stack. Cold pairs used two
forks, ten warmup single shots and twenty measured single shots per fork, batch
size one, with the same heap and GC settings. Error bounds are JMH's 99.9%
confidence intervals. All varying Fibonacci inputs were checked against
independent iterative host oracles. [Raw JMH results](bound-self-root-data.json)
include the per-fork samples and allocation error bounds.

The candidate passed 30 existing focused tests and a final 13-test batch covering
its seven new ABI/ownership tests plus instrumentation. Checks included exact self
identity across equal bindings, original binary calls with an explicit self,
real splitting and frame reuse, captured/prefixed fallback, actual cache
saturation, installed last-tier execution, concurrent eligible calls, and
copying an already instrumented body before unwind/re-entry. A source assertion
initially omitted the parser's trailing whitespace; after normalizing that
whitespace the final batch passed without skips. These focused checks are not a
claim that the rejected candidate passed the full suite.

Independent review also identified an **unresolved concurrency risk**: the factory
copied a live invocation subtree while another thread could be specializing or
instrumenting the original. No failing interleaving was reproduced. Any future
version must establish safe snapshot behavior; simply acquiring another root's
lock during cache initialization also needs a lock-order review. The existing
concurrent-call test does not prove this separate snapshot property.

No broad warmed control suite was run after the primary performance gate failed.
The new unary runtime and its candidate-only tests are retained only in the patch.
The cold benchmark and an ordinary cloned-root unwind regression remain useful
independently of the rejected design. The [earlier allocation attribution](fib-allocation.md)
still identifies call packing as the principal surviving Fibonacci allocation.

After restoration, all 379 regular tests and the 10,000-program semantic soak
passed without failures or skips. Benchmark classes compiled, and an untimed
smoke check exercised all four cold-workload inputs on both AST and bytecode.
