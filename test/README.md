# Testing Cadenza

Run `./gradlew test` with the GraalVM setup described in the project README.
The suite checks both AST and bytecode where their supported semantics overlap.
Internal frame, node ownership, and neutral-term tests also exercise contracts
that the public polyglot boundary deliberately does not expose.

`DifferentialTests` generates typed programs and compares each backend against
an independent evaluator using immutable lexical maps, Kotlin closures, and
`BigInteger`. Agreement between the two production backends alone is insufficient:
they share the parser, type checker, and calling convention, so they can share bugs.
Failures include the deterministic case index, seed, and guest source.
For a generated failure, the diagnostic also searches up to 64 smaller, closed,
same-typed subexpressions for the same failure. This is a bounded reduction aid,
not a guarantee of a globally minimal program.

Additional oracle checks cover bounded tail and non-tail recursion, recursive
higher-order state, mutual recursion, all application groupings, and retained
partials after dispatch caches saturate. Numeric properties use independent
`BigInteger` operations and quotient/remainder identities, including signed
intermediates and promotion boundaries.

The regular suite samples 160 generated programs. A larger run is a separate
Gradle task, with its own results and bounded test heap:

```sh
./gradlew semanticSoak
./gradlew semanticSoak -PfuzzCases=1000 -PfuzzSeedOffset=10000
./gradlew semanticSoak -PfuzzCases=1 -PfuzzSeedOffset=1234 --rerun-tasks
```

The last command reproduces generated case 1234. The case count must be between
1 and 10,000. Changing either Gradle property invalidates the task's cached result.
Linux CI runs the 10,000-case soak; Linux and macOS run the regular suite. Failed
CI jobs retain HTML and XML test reports.

`EvaluationOrderTests` observes `printId` traces as well as results. It checks
function-before-argument evaluation, left-to-right arguments, strict lets,
partial application, branch laziness, failure short-circuiting, and captures
across tail iterations. Flat and nested applications intentionally have different
effect ordering: tests must not assume they are interchangeable for effectful code.

When adding a regression, prefer an externally observable result or a mathematical
oracle. Exercise one call site repeatedly across relevant histories: small and large
integers, cache saturation, retained partials, and exceptions followed by success.
Keep the smallest reproducer when a generated program fails. Tests that inspect
Truffle storage or AST identity should explain the runtime contract they protect.

`RuntimeTransitionTests` includes one Graal-specific compilation test. It requests
synchronous compilation and verifies installed last-tier code before exercising
BigInt promotion, a neutral result, and a guest exception, then checks recovery.
Only this test is skipped on a nonoptimizing Truffle runtime; the portable
semantic tests still run. This provides targeted compiled-code coverage, not a
claim that every regression test runs compiled.

Frontend tests check independently constructed higher-order types and exact error
locations, including shebangs and whitespace prefixes. Static rejection must occur
before guest output, and the context must remain usable afterward.

Tooling tests attach real Truffle listeners, inspect retained frames, and request
unwind/re-entry to verify that argument setup runs again. Host lifecycle tests
cancel an executing loop at an observable checkpoint and verify shared-engine
isolation. Statement-limit tests exercise the public polyglot API on both
backends, including infinite recursion with a bounded watchdog. Bytecode tests
check that unsupported root events are absent rather than accepting unmatched
entries as valid profiler data; ordinary statement returns and guest-error exits
are checked separately from the documented tail-transfer limitation.

Use the separate JMH workloads to measure allocation and throughput changes.

## Test sensitivity check, 2026-09-22

After the full 332-test suite and 10,000-program soak passed, three deliberately
incorrect changes were built and tested individually in a temporary copy:

| Mutation | New test that rejected it | Observed failure |
|---|---|---|
| Replace signed remainder with nonnegative modulo | Signed quotient/remainder oracle | Expected `-1`, received `4294967294` |
| Compute signed byte range from absolute-value bit length | Integral interop boundaries | `BigInt(-128)` incorrectly rejected |
| Evaluate arguments right-to-left, then restore their positions | Evaluation-order trace | Answer remained 42; printed arguments were reversed |

All three compiled successfully and failed the intended assertions. The working
runtime was never mutated. [Exact mutations and assertion failures](mutation-checks-2026-09-22.json)
are retained as evidence of these specific tests' sensitivity; this is not an
exhaustive mutation score for the suite.
