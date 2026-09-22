# Truffle tooling and execution limits

Cadenza exposes source locations and standard Truffle tags for execution tools.
AST root events describe actual program or closure invocations. Closure creation
is an expression, not a closure invocation. The root event encloses argument
setup; the root-body event starts after arguments and captures have been loaded.
An instrument that unwinds and re-enters the root therefore repeats its prologue.
This follows the [Truffle RootTag contract](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/StandardTags.RootTag.html).

For this expression language, a statement unit is the top-level expression or
one evaluation of a closure body. A self-tail loop evaluates a new statement unit
on every iteration, even when it reuses a frame. Individual arithmetic operands
and variable reads are not separate statement units. Source sections identify
the body being evaluated. On AST, root events may enclose several self-tail
iterations; body and statement events still observe each iteration.

Both backends support these statement units, so a host can use polyglot limits:

```kotlin
val limits = ResourceLimits.newBuilder().statementLimit(10_000, null).build()
val context = Context.newBuilder("cadenza").resourceLimits(limits).build()
try {
  context.eval("cadenza", program)
} finally {
  context.close(true)
}
```

Exceeding the limit cancels that context. A host can identify this through
`PolyglotException.isCancelled` and `isResourceExhausted`; subsequent execution
in the cancelled context remains rejected. Statement counts measure guest
progress, not elapsed time or memory use. See the
[polyglot resource-limit API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/ResourceLimits.Builder.html).

Tests attach real instruments to check entry/return ordering, frame materialization,
unwind/re-entry, cloning, and body counts. They also exercise actual host cancellation
and limits while verifying that other contexts on a shared engine remain usable.
Bytecode currently exposes statement entry events for these budgets, but does
not emit root/root-body events: the generated interpreter does not balance
tail-transfer entry and exit callbacks. Use AST for root-based profiling and
unwind/re-entry tooling. See [bytecode's coverage notes](bytecode.md).
