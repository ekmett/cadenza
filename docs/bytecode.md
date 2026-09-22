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
the existing trampoline, so recursive bytecode functions have bounded Java stack usage.

`BytecodeRoot.java` enables uncached interpretation and primitive boxing elimination for `int` and
`boolean`. The generated interpreter provides quickening, typed stack/local storage, and lazy source
metadata. Function applications force the cached tier because the shared guest dispatch nodes need
adopted cache state. The compiler constructs constants and nested function targets once, making its
builder parser safe to replay when Truffle requests source metadata.

The prototype targets **closed terms with concrete runtime values**. Normalization of open terms
and neutral values remains an AST feature; its `SlowPathException` mechanism is unchanged. Bytecode
debugger/instrumentation tags are not implemented yet. Captured values currently travel in the
closure's partial-argument array; the AST backend uses the Static Object Model capture layout.
Arithmetic follows the existing builtin behavior, including promotion of addition/subtraction to
`BigInt` on integer overflow. It does not broaden the numeric semantics of other builtins.

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
