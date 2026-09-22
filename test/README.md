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
The random language is deliberately pure and total: it excludes output, recursive
initializers, neutral syntax, and division by zero. Its application oracle evaluates
each flat group of arguments before invoking a body. Effects and failures use the
separate trace and runtime-transition tests below.
For a generated failure, the diagnostic also searches up to 64 smaller, closed,
same-typed subexpressions for the same failure. This is a bounded reduction aid,
not a guarantee of a globally minimal program.

Additional oracle checks cover bounded tail and non-tail recursion, recursive
higher-order state, mutual recursion, all application groupings, and retained
partials after dispatch caches saturate. Numeric properties use independent
`BigInteger` operations and quotient/remainder identities, including signed
intermediates and promotion boundaries.

Numeric edge checks also create an uncached source for each boundary case and
compare fresh primitive sites with sites that have already seen large integers.
This prevents early promotion in a long property sequence from hiding bugs in
the original integer specialization. Both histories replay ordinary values and
the boundary case on the same function.

Four fixed generated function bodies are also replayed through one parsed factory
per backend, with changing numeric representations, branch choices, captures, and
retained partial applications. Weighted result terms make each input observably
relevant even when a random subexpression ignores it. Two of those bodies have a
separate Graal-specific test that verifies installed last-tier code before numeric
and captured-environment transitions. This does not compile every soak program.

A separate 120-case AST-only sample supplies internal symbolic Nat/Bool arguments.
It excludes function-valued conditionals, so residuals need only scalar builtin
calls and conditionals. An independent substitution oracle recognizes distinct
input/capture markers, uses host arithmetic, and selects residual branches; it
never executes guest closures or builtins. Retained residuals and partials are
replayed after small, symbolic, large, and small inputs on the same body target.
Concrete recovery must return concrete values. The sample checks that every
supported scalar residual operation was exercised and leaves the deliberate
neutral exception mechanism unchanged.

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

`ApplicationEffectsTests` exhausts the eight lambda groupings and eight application
groupings of four arguments. Its independent event scheduler predicts when each
argument prints and when each newly satisfied function body runs. Weighted results
check argument positions as well as effects. Each call site sees changing targets,
large integers and captures, then revisits retained closures. Every argument failure
position and reachable body boundary is followed by a successful call on the same
site, checking both the truncated trace and recovery.

`FixedSelfIdentityTests` alternates equal-but-distinct recursive closures within a
single tail loop. It checks exact incoming self references, one reused frame and
the executing root, including after a real `DirectCallNode` split. Equality alone
is insufficient here: a previous binding must not replace the incoming closure.

When adding a regression, prefer an externally observable result or a mathematical
oracle. Exercise one call site repeatedly across relevant histories: small and large
integers, cache saturation, retained partials, and exceptions followed by success.
Keep the smallest reproducer when a generated program fails. Tests that inspect
Truffle storage or AST identity should explain the runtime contract they protect.

`RuntimeTransitionTests` includes one Graal-specific compilation test. It requests
synchronous compilation and verifies installed last-tier code before exercising
BigInt promotion, a neutral result, and a guest exception, then checks recovery.
The explicit compilation tests are skipped on a nonoptimizing Truffle runtime; the portable
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

Concurrency tests enter one context from four threads and synchronize before
calling shared functions. Independent weighted and triangular-number oracles
check recursive results, captures and retained partials through concurrent
specialization changes. Workers and failure cleanup are bounded so a broken
runtime cannot indefinitely block the test JVM.

The regular test task builds `installDist`. On Linux and macOS, launcher tests
execute its actual script and packaged JARs from a temporary directory with spaces,
using a source filename beginning with `--`. Both backends must print a large
integer, display a closure without executing it, and return a nonzero status with
the source filename for a guest error. These subprocesses use the selected Java
toolchain and a timeout; they do not inherit Gradle's test classpath.

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

A later source-cache check reused one cached SDK `Source` across live AST and
bytecode contexts, inspected the executing roots through instrumentation, and
revisited retained closures. Deliberately treating all backend options as
compatible caused this test to reject the first bytecode context executing AST
code. The older test called `Language.parse` directly and bypassed this cache;
the replacement checks the public path that hosts actually use. This fourth
mutation was also built only in an isolated temporary copy.

A fifth isolated mutation swapped the branches stored in a neutral conditional,
leaving concrete conditionals unchanged. The generated neutral-history test
compiled and rejected it when replaying a retained partial: seed `1325428639`,
captured value `0`, input `0`, and choice `true` expected `false` but received
`true`. This checks the independent residual oracle rather than backend agreement.

A sixth isolated mutation skipped a tail-frame slot write whenever the incoming
closure compared equal to the previous value. Both fixed-self identity tests
rejected the stale reference at iteration 1, including the split-root case. The
unmodified tests passed; the mutant compiled and failed the intended identity
assertions rather than failing during setup.

A seventh isolated mutation flattened nested AST applications. The new grouping
matrix still obtained the expected numeric result but rejected the moved body
effects: `[3, 10119, 5, 7, 11, 10122]` became `[3, 5, 7, 11, 10119, 10122]`.
It also caught the changed trace during recovery. The two bytecode cases passed
because this mutation affected only AST compilation.
