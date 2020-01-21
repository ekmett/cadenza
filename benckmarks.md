

Benchmark                Mode  Cnt   Score    Error  Units
AddBenchmark.addCadenza  avgt    5   0.010 ±  0.001  ms/op
AddBenchmark.addInterp   avgt    5   0.197 ±  0.037  ms/op
AddBenchmark.addKotlin   avgt    5  ≈ 10⁻⁶           ms/op


kotlin -> cadenza 10000x slowdown
cadenza -> interp 20x slowdown

interpreter is not in a horribly slow style (i think?) but is also not optimized

on graal jdk:
Benchmark               Mode  Cnt   Score    Error  Units
AddBenchmark.addInterp  avgt    5   0.191 ±  0.048  ms/op
AddBenchmark.addKotlin  avgt    5  ≈ 10⁻⁶           ms/op

(code for benchmarking truffle is currently broken on graal jdk)

TOOD: bench cadenza in interpreter mode

