# Neutral execution and the ordinary-path budget

Experiment base: `4fcb8843dcbbf1f11c0e2d31fe3520610973baa9`, after the completed restoration and six-hour improvement work. Acceptance requires near-zero overhead for ordinary evaluation, including recovery after symbolic input. Faster normalization alone is insufficient.

## Ordinary-path measurements

Apple M3/macOS ARM64, GraalVM 25.3.4.1, single thread. Each cell: 2 independent JVM forks, 10 × 1 s warmup, 5 × 1 s measurement per fork, varying runtime inputs (four for Fibonacci, sixteen for the other controls); JMH GC allocation profiler. Fork heap 128–768 MiB. Separate serial processes; no concurrent builds or other experimental benchmark jobs. Times are microseconds/op; raw JSON includes JMH uncertainty and individual samples.

| Workload | Concrete-only diagnostic | Original exception design | Value-returning candidate |
|---|---:|---:|---:|
| Fibonacci 15–18 |10.831|10.870|19.589|
| Fixed accumulator 1000–1015 |4.519|4.503|4.499|
| Accumulator 1000–1015 |2.977|2.971|2.973|
| Captured closure 1000–1015 |0.007857|0.007660|0.007700|

The original exception design and concrete-only diagnostic have essentially equal performance in these controls. This is evidence for a negligible ordinary-path cost on these workloads, not proof for every program. The concrete-only diagnostic removes neutral semantics and is never a production candidate.

The value-returning candidate is rejected: Fibonacci becomes 80% slower and allocation rises from 50,173 to 95,745 bytes/op (+91%). The other three ordinary controls remain close, so those alone would have missed the regression. The exact compiler mechanism behind this regression has not been isolated.

## Symbolic throughput of the rejected value candidate

| Workload | Original µs/op | Value candidate µs/op | Speedup | Bytes/op original → candidate |
|---|---:|---:|---:|---:|
| Arithmetic DAG | 0.578 | 0.0355 | 16.3× | 2,112 → 816 |
| Conditional/recursive sums | 5.525 | 0.200 | 27.7× | 6,512 → 3,376 |
| Higher-order/partial application | 0.610 | 0.0304 | 20.1× | 760 → 336 |

These are all-symbolic workloads with escaping residuals. They show the potential of compiler-visible normalization, but do not compensate for the ordinary Fibonacci regression. The original conditional measurement has relatively wide uncertainty (±1.128 µs, JMH interval); use the raw records rather than treating the rounded ratio as precise.

The arithmetic microbenchmark is deliberately narrow:

```text
\(x : Nat) ->
  let a : Nat = plus (mult x 3) 17 in
  let b : Nat = minus (mult a 5) x in
  let c : Nat = plus (mod b 97) (div (plus b 7) 3) in
  minus (mult c 11) (mod x 13)
```

Timed symbolic calls supply a prebuilt unknown `Nat` represented by `NeutralValue`; all eleven builtin operations depend on it and construct residual data. Reused locals make this a DAG, not a fully copied tree. It stresses neutral propagation, local binding and escaping residual allocation, without recursion, guest higher-order calls or concrete BigInteger arithmetic. Parsing, marker construction and the independent BigInteger oracle occur in setup. Sixteen marker identities rotate during timing. Fibonacci instead stresses recursive concrete calls, making it a necessary independent ordinary-path gate.

These results do not establish a neutral-frequency break-even point: the large symbolic speedups and the Fibonacci regression come from different programs. The same-source mixed and recovery benchmark modes are provided for such comparisons. Selecting a separate normalizer at a known normalization boundary is a possible future design, not an implemented or performance-verified result.

## Compiler gates

* Simply changing NeutralException from SlowPathException to ControlFlowException failed existing compiled-history tests with `BailoutException: Code installation failed: code is too large` on AArch64.
* Keeping the original fast methods and entering BranchProfiles only in neutral handlers also failed that installation gate in `DifferentialTests.selectedGeneratedFunctionsReplayAfterVerifiedGraalCompilation`. The failing history changes captures and partial application, without supplying neutral inputs. Both failed runs were stopped after recording the compiler error and expensive diagnostic retries; neither is claimed to have completed the full suite.
* The value-returning candidate passed 383 tests and 10,000 semantic-soak cases. A new instrumented-root test verified 24/24 neutral returns in installed last-tier guest code, followed by compiled concrete/BigInteger/concrete recovery. That establishes compilability, not acceptable ordinary-path performance.

The same compiled-neutral test is an intentional negative control for the original SlowPathException runtime. That control failed before the 24-return assertion because repeated deoptimizations triggered the configured compilation failure action; it did not produce a measured 0/24 count. Portable residual semantics and the legacy exception contracts passed on the original runtime.

## Correctness and measurement safeguards

The new harness uses arithmetic DAGs, neutral conditionals around recursive sums, and higher-order/partially applied calls. 16 distinct runtime inputs and 16 distinct symbolic identities prevent constant-result timing. Residuals escape to JMH. A separate fresh AST checks residual structure and independent host BigInteger substitutions, including promoted values; validation does not teach the measured ordinary root a neutral history. The concrete-only setup switch is restricted to concrete mode and must be applied identically to comparison variants.

New portable tests retain residuals across repeated neutral, concrete and promoted histories, checking both branches under independent substitutions. Legacy exception tests check later operand effects and enclosing residual construction, and both conditional branches. They exposed an early value-path bug and failed before its correction. The compiler test helper now initializes the optimizing runtime before detecting its implementation, preventing an isolated test from silently skipping because the implementation class had not yet loaded.

## Artifacts

`neutral-tracing-data.json` contains the full JMH records; `neutral-tracing-harness.kt.txt` preserves the exact initial harness used by the recorded runs. Candidate patches are against the base commit. The concrete-only patch intentionally omits semantics. None of these archived patches is an endorsement for production.

## Final decision

Retain the original `SlowPathException` runtime. No experimental runtime changes were adopted. The producer-profile attempt also failed compilation, this time with AArch64 `BranchTargetOutOfBoundsException: Branch target 1129788 out of bounds`. Its suite was stopped after saving that failure; no full-suite pass or timing result is claimed for any ControlFlowException candidate.

The repository keeps the portable residual-history and legacy-exception tests, the runtime initialization fix for compiler tests, and the neutral benchmark. The candidate-only compiled-neutral assertion is archived as `neutral-compilation-test.kt.txt`, rather than imposing an incompatible requirement on the deliberately interpreter-only production neutral handlers.

The harness now also offers `compiledThenNeutralThenConcrete`, which verifies installed ordinary last-tier code before injecting a 32-call neutral burst, then measures steady concrete recovery. This extension is not in the frozen initial harness and did not affect the preceding ordinary or symbolic comparisons. Transition latency, cold construction overhead, interpreter overhead, large-program scaling and other architectures are not measured by those comparisons.

## Retained-tree validation

The final production tree passes 382 tests with zero failures or skips, plus the six-test semantic-soak task configured for 10,000 cases. All 15 workload/history combinations pass a one-fork smoke run, including the new history that injects neutrals after verified last-tier compilation. Smoke timings are excluded from the performance tables. Production sources are byte-for-byte unchanged from the experiment base.

## Reproduction

Extract the base commit to separate directories. Add the frozen harness as `bench/neutralTracing.kt` in each, and apply the named candidate patch where appropriate. Use the pinned GraalVM toolchain and build each archive separately. Run ordinary controls before symbolic timing; do not benchmark alongside builds.

For example, on both the original and value-returning archive:

```sh
./gradlew bench --args='^cadenza.bench.Fib.cadenza$ -p backend=ast -p size=15 -wi 10 -w 1s -i 5 -r 1s -f 2 -t 1 -prof gc -jvmArgsAppend "-Xms128m -Xmx768m" -rf json -rff fib.json'
./gradlew bench --args='^cadenza.bench.NeutralTracing.evaluate$ -p workload=arithmetic -p mode=neutral -wi 10 -w 1s -i 5 -r 1s -f 2 -t 1 -prof gc -jvmArgsAppend "-Xms128m -Xmx768m" -rf json -rff neutral.json'
```

Require exactly two successful `NEUTRAL_SETUP_CHECK` messages per symbolic case, nonempty JMH JSON, and successful forks. The recorded runner used standalone JMH after the build; complete JVM flags are in each raw JSON entry. Compiler experiments require copying the archived candidate test to `test/neutralCompilation.kt` and using the fixed `test/compilation.kt` helper. Compiler failures are evidence against adoption even when an isolated benchmark appears fast.

Truffle documents [SlowPathException](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/SlowPathException.html) handlers as excluded from compilation; [ControlFlowException](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/ControlFlowException.html) is intended for interpreter control flow. Making this particular exception compiler-visible proved insufficient for an acceptable whole-evaluator result.

