

```
Benchmark        Mode  Cnt   Score    Error  Units
Add.cadenza      avgt    5   0.022 ±  0.027  ms/op
Add.interpreter  avgt    5   0.169 ±  0.115  ms/op
Add.kotlin       avgt    5  ≈ 10⁻⁵           ms/op
Fib.cadenza      avgt    5   0.024 ±  0.008  ms/op
Fib.interpreter  avgt    5   0.458 ±  0.159  ms/op
Fib.kotlin       avgt    5   0.007 ±  0.001  ms/op
``` 

old version with recursive inlining + loop peeling:

```
Benchmark        Mode  Cnt   Score    Error  Units
Add.cadenza      avgt    5   0.014 ±  0.005  ms/op
Add.interpreter  avgt    5   0.124 ±  0.013  ms/op
Add.kotlin       avgt    5  ≈ 10⁻⁵           ms/op
Fib.cadenza      avgt    5   0.077 ±  0.002  ms/op
Fib.interpreter  avgt    5   0.300 ±  0.014  ms/op
Fib.kotlin       avgt    5   0.004 ±  0.001  ms/op
```

i think fib is slower because it fails to inline a builtin (uses up inlining budget on recursive occs)
is faster with "-Dgraal.TruffleMaximumRecursiveInlining=1"


interpreter is not in a horribly slow style (i think?) but is also not optimized

on graal jdk:

```
Benchmark               Mode  Cnt   Score    Error  Units
AddBenchmark.addInterp  avgt    5   0.191 ±  0.048  ms/op
AddBenchmark.addKotlin  avgt    5  ≈ 10⁻⁶           ms/op
```

(code for benchmarking truffle is currently broken on graal jdk)

TOOD: bench cadenza in interpreter mode

