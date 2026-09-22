# Experimental Bytecode DSL backend

The AST interpreter remains the default. Select the new backend explicitly:

```sh
build/install/cadenza/bin/cadenza --experimental-options --cadenza.Backend=bytecode examples/fib.za
```

With the Polyglot API:

```java
try (var context = Context.newBuilder("cadenza")
        .allowExperimentalOptions(true)
        .option("cadenza.Backend", "bytecode")
        .build()) {
    var add = context.eval("cadenza", "\\(x : Nat) (y : Nat) -> plus x y");
    assert add.execute(20).execute(22).asInt() == 42;
}
```

Both backends use the same parser and type checker. The bytecode compiler lowers natural literals,
conditionals, local bindings, recursive bindings, closures, and function applications directly to
Truffle Bytecode DSL operations. It generates a real bytecode interpreter rather than wrapping an
AST in a single operation. Common arithmetic and comparisons have dedicated instructions; other
builtins and all function applications use the existing guest dispatch machinery. Tail calls reuse
the existing trampoline, so tail-recursive bytecode calls have bounded Java stack
usage. Non-tail recursion still consumes Java stack frames.

`BytecodeRoot.java` enables uncached interpretation and primitive boxing elimination for `int` and
`boolean`. The generated interpreter provides quickening, typed stack/local storage, and lazy source
metadata. Function applications force the cached tier because the shared guest dispatch nodes need
adopted cache state. The compiler constructs constants and nested function targets once, making its
builder parser safe to replay when Truffle requests source metadata or instrumentation tags.

Both backends expose statement units at the top-level expression and each execution of a closure
body, including every tail-recursive iteration. Creating a partial application does not enter its
body. The bytecode backend emits an explicit `StatementTag` around each source body; the entry
trampoline and dispatch helpers are not source statements. Closure root sections identify their
own lambda, and statement sections identify the body expression. Tags remain lazy until an
instrument requests them.

One tooling limitation remains around the shared tail-call trampoline: a tail call leaves a
bytecode root through `ControlFlowException`, which the generated interpreter propagates before
running tag-exit handlers. A three-step countdown diagnostic observed five statement entries but
only two returns on bytecode; AST reported the remaining three tail exits. Ordinary success and
guest-error exits work correctly. Automatic bytecode `RootTag` and `RootBodyTag` emission is disabled
to avoid misleading root profilers. Use the AST backend for root-based profiling and debugger work.
Bytecode statement entry events support budgets, but tools must not assume balanced statement
return/exception events for tail calls yet.

Polyglot `ResourceLimits.statementLimit` therefore bounds these units on both backends. It counts
closure entries rather than arithmetic operands, so it is not a wall-clock time limit or a bound on
the cost of a single large integer operation. Exceeding a limit cancels that context; other contexts
sharing the engine retain their own budgets. `BytecodeInstrumentationTests` checks real instrument
events, source ranges, late instrumentation, and cancellation through the public Polyglot API.

The prototype targets **closed terms with concrete runtime values**. Normalization of open terms
and neutral values remains an AST feature; its `SlowPathException` mechanism is unchanged. Bytecode
root, root-body, expression, and call tags, and debugger scope customization, remain future work.
Captured values currently travel in the
closure's partial-argument array; the AST backend uses the Static Object Model capture layout.
Arithmetic follows the AST builtins: integer literals have arbitrary precision, and addition,
subtraction, multiplication, division, remainder, and comparisons support `BigInt`. Overflow
promotes to `BigInt`; division and remainder by zero produce guest errors. Subtraction retains
its existing signed result behavior despite the source type being named `Nat`.

`BytecodeTests` compares both backends on arithmetic, conditionals, captured closures, partial and
overapplication, builtin fixpoints, and long tail recursion. It also exercises the Polyglot boundary,
integer overflow, local shadowing, source-metadata replay, AST ownership verification of generated
roots, and backend isolation when contexts share an engine.

The JMH workloads accept a `backend` parameter. Compare the same workload and input across both
values, keeping warm evaluation separate from cold context/parse setup and escaping closure
allocation. For allocation measurements add JMH's `-prof gc`. Short smoke runs establish that the
workloads execute; use full warmup and measurement iterations before drawing performance conclusions.

The DSL itself remains experimental upstream. The implementation is compiled against the project's
pinned Truffle release; consult the [Bytecode DSL introduction](https://github.com/oracle/graal/blob/master/truffle/docs/bytecode_dsl/BytecodeDSL.md)
and [user guide](https://github.com/oracle/graal/blob/master/truffle/docs/bytecode_dsl/UserGuide.md) when
upgrading it.
