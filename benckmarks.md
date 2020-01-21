

```
Benchmark        Mode  Cnt   Score    Error  Units
Add.cadenza      avgt    5   0.010 ±  0.001  ms/op
Add.interpreter  avgt    5   0.143 ±  0.019  ms/op
Add.kotlin       avgt    5  ≈ 10⁻⁶           ms/op
Fib.cadenza      avgt    5   0.015 ±  0.001  ms/op
Fib.interpreter  avgt    5   0.386 ±  0.157  ms/op
Fib.kotlin       avgt    5   0.003 ±  0.001  ms/op
```

old version with recursive inlining + loop peeling:

```
Benchmark        Mode  Cnt   Score    Error  Units
Add.cadenza      avgt    5   0.015 ±  0.008  ms/op
Add.interpreter  avgt    5   0.209 ±  0.024  ms/op
Add.kotlin       avgt    5  ≈ 10⁻⁶           ms/op
Fib.cadenza      avgt    5   0.059 ±  0.011  ms/op
Fib.interpreter  avgt    5   0.434 ±  0.038  ms/op
Fib.kotlin       avgt    5   0.003 ±  0.001  ms/op
```


interpreter is not in a horribly slow style (i think?) but is also not optimized

on graal jdk:

```
Benchmark               Mode  Cnt   Score    Error  Units
AddBenchmark.addInterp  avgt    5   0.191 ±  0.048  ms/op
AddBenchmark.addKotlin  avgt    5  ≈ 10⁻⁶           ms/op
```

(code for benchmarking truffle is currently broken on graal jdk)

TOOD: bench cadenza in interpreter mode

