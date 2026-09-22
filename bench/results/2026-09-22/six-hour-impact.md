# Six-hour AST runtime improvements

The requested work window is **2026-09-22 04:43:54–10:43:54 UTC**. This report records the verified final runtime and its benchmark endpoint. Its baseline is benchmark commit `dd7c47b` (runtime `89de817`), not restoration commit `26887c4`. The Kotlin/Gradle/Graal upgrade, initial Bytecode DSL backend, StaticShape/indexed-frame migration, and the large original modular-accumulator improvement were already present before the window. They must not be counted as six-hour improvements. The older [restoration comparison](restoration-comparison.md) answers a different baseline question.

## Shipped runtime changes and measured impact

- **Fixed-point recursion retains its self closure**, avoids repeated closure/type construction, and gives eligible binary AST bodies a direct original-target call. Split roots preserve logical body identity and self-tail frame reuse. [First batch](ast-followup.md), [calling follow-up](ast-calls.md).
- **Captured primitives restore directly into typed frame writes**, eliminating a box per body entry in captured fixed-point loops. In one matched useful-work test, fixed-point modular accumulation improved **6.780 → 5.185 µs** and **16,360 → 224 B/op**. A subsequent narrow fixed-self binding helper exposed construction to partial evaluation, improving the same workload **5.144 → 4.495 µs** and **224 → 56 B/op** in its own matched pair. These are separate experiments; do not multiply their timing ratios. [Capture restoration](capture-restore.md), [self binding](fixed-self-binding.md).
- **Escaping captures lost the extra storage wrapper**: the goal-start regression of **136 B/op** returned to **104 B/op**. Noncapturing lambdas reuse immutable closures (**72 → 40 B/op** in a matched test), and generic escaping partial application copies its remaining range once (**248 → 224 B/op**). These are allocation improvements, not established universal throughput gains. [Allocation follow-up](ast-followup.md), [partial application](ast-calls.md).

The fresh [matched endpoint comparison](final-comparison.md) measures Fibonacci
15–18 at **69.380 ± 6.996 → 15.096 ± 4.829 µs/op** and
**748,080 → 50,173 B/op** from goal start to final runtime: **93.3% less allocation**
and **4.60× measured throughput**. Final timing is noisy, so do not generalize the
exact ratio. The ordinary self-tail accumulator was already about **56 B/op at
goal start**; its fresh timing intervals overlap (3.198 ± 0.014 versus
3.184 ± 0.027 µs). The same fresh run confirms the capture repair,
**136 → 104 B/op**, with overlapping timing intervals. The trivial fixed counter
gets almost eliminated by compilation, so its nanosecond result is not a general
interpreter-throughput claim.

The final useful-work fixed-point pair measures the same modular accumulator
on goal-start and final runtime: **21.223 ± 2.864 → 4.889 ± 0.100 µs**, and
**272,081 → 56 B/op**. That is **4.34× measured throughput and 99.979% less allocation**
with nonoverlapping timing intervals. This fresh endpoint result captures the
combined six-hour effect; it does not multiply the earlier per-change ratios.
See the [checked supplemental pair](final-comparison.md#useful-fixed-point-work-during-the-goal).

Correctness work shipped alongside optimization: lexical shadowing and recursive binding cells; complete large-integer literals/arithmetic with checked promotion and guest zero-division errors; exact numeric host imports; neutral propagation through builtin results, overapplication, and symbolic conditionals; parser/token/full-consumption and declared-type diagnostics; source-cache backend isolation; launcher values/errors; and source locations across tail transfers. Signed subtraction and the deliberate neutral `SlowPathException` policy remain unchanged. AST invocation/body instrumentation, statement budgets on both backends, and cancellation behavior are now tested. Matched controls found no resolved ordinary AST regression from the [tooling](tooling-ast.md) or [runtime-diagnostic](runtime-diagnostics.md) changes.

## Evidence quality

The final runtime `2d3b1f3` passed **379 regular tests with zero failures/skips**, plus the **10,000-program independent semantic soak** and benchmark compilation. That count excludes candidate-only tests from rejected experiments. The suite now includes independent BigInteger/lexical-environment oracles on both backends; selected functions with verified installed last-tier code; scalar-neutral substitution histories; promotion/error/recovery replay; forced splits; frame/capture retention; concurrent calls; actual instrumentation/unwind and resource limits; and installed launcher subprocesses. The exhaustive application-effects matrix checks **2,048 calls**, including failure/recovery traces. Seven isolated mutations demonstrably failed their intended assertions, including mutations that preserved numeric answers but reordered effects. These establish sensitivity to specific faults, not an exhaustive mutation score. [Coverage and reproduction](../../../test/README.md), [mutation evidence](../../../test/mutation-checks-2026-09-22.json), [final runtime validation](argument-array.md).

Allocation investigation identified a JFR startup-weight artifact, corrected it explicitly, and added a standalone analyzer with deterministic synthetic-recording checks. A controlled padding experiment attributes approximately **99.75% of remaining warmed Fib allocation to packed call arrays**, rather than blaming neutral exceptions or captures. This is actionable profiling evidence, not itself a runtime optimization. [Attribution](fib-allocation.md), [analyzer checks](../../../tools/README.md).

## Rejected experiments and remaining work

Three candidates were measured and removed: [per-frame storage recovery](frame-recovery.md) added 16 B/op after exotic values without a useful warmed improvement; [fixed-self identity profiles](fixed-self-profile.md) left allocation unchanged and slowed small Fib about 69%; [unary bound roots](bound-self-root.md) saved about 17% allocation but slowed warmed Fib roughly 2.5–2.6×. Reproduction patches and data remain; these are **not shipped runtime features**. The rejected live-root-copy design also has an unresolved concurrent snapshot risk, not a reproduced bug.

AST remains the default. Bytecode is still experimental, its recursive-call performance has not been brought up to AST, and open-term normalization remains AST-only. Bytecode statement entries support budgets, but tail-transfer exits are not balanced; automatic Root/RootBody tags remain disabled. Native Image packaging is not configured. [Bytecode limitations](../../../docs/bytecode.md).

The final [direct argument-array fill](argument-array.md) removes intermediate
collections during AST argument evaluation. Matched guest-interpreter Fibonacci
allocation fell 12.9% for inputs 15–18 and 7.8% for inputs 10–13; timing intervals
overlap. Eight compiled workload configurations and a fresh-context control
passed their performance gates. This does not remove the compiled packed-call
arrays described above.

The [final three-checkpoint comparison](final-comparison.md) uses the same
independently checked harness on restoration, goal start and final runtime. Its
fresh measurements supersede cross-session endpoint timing estimates for these
three workloads. The exact harness and raw samples are retained alongside it.
