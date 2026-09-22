# Rejected fixed-self identity profiling

Profiling the resolved fixed-point self closure did not eliminate the surviving
recursive call arrays. It substantially slowed the smaller Fibonacci workload,
so the candidate was removed. The production runtime remains the implementation
at `27509a66faa62b5fe64993e82191bbe163f18289`.

The experiment added a separate identity profile for each AST argument slot,
applied only when resolving the private fixed-self token. Its hypothesis was
that constant self identity would expose the closure target and argument metadata
to partial evaluation. Ordinary arguments and the physical calling convention
were unchanged. Cloned roots received fresh profiles.

| Workload (four alternating inputs) | Baseline µs/op | Candidate µs/op | Baseline B/op | Candidate B/op |
|---|---:|---:|---:|---:|
| Fibonacci 15–18 | 10.937 ± 0.125 | 12.351 ± 1.423 | 50,172.50 | 50,172.65 |
| Fibonacci 10–13 | 0.994 ± 0.009 | 1.677 ± 0.030 | 4,480.05 | 4,480.08 |

These are matched JMH 1.37 average-time runs on Apple M3, macOS ARM64,
GraalVM 25.3.4.1 / JDK 25.0.4.1+1. Each side used two forks, one thread,
10 × 1 s warmup, 5 × 1 s measurement, the GC profiler, 128–768 MiB fork heaps,
and a 32 MiB stack. Error bounds are JMH's 99.9% confidence intervals. Builds and
benchmarks ran serially. Both harnesses checked all varying inputs against an
independent iterative BigInteger oracle before measurement.

Allocation is unchanged within measurement noise. The smaller workload has
clearly separated intervals and about 69% greater latency. The larger candidate
is variable and its interval slightly overlaps the baseline; that result does
not establish a precise slowdown. No additional control benchmarks were run
after the primary gate failed.

The candidate passed 37 focused correctness tests, including three new checks
for exact self identity, real split-root profile independence, and captured
fixed closures. Passing those checks does not compensate for the performance
regression. After removing the candidate, the complete 372-test suite passed
with no failures or skips; the unchanged 10,000-program semantic soak remained
up to date and benchmark classes compiled successfully.

[Raw JMH results](fixed-self-profile-data.json) preserve both forks and GC data.
The [candidate patch](fixed-self-profile-candidate.patch) is an **unapplied
experimental artifact**, including its candidate-only tests. It is retained to
make this negative result reproducible, not as a recommended runtime change.
The earlier [allocation attribution](fib-allocation.md) still identifies packed
call arrays as the main remaining Fibonacci allocation.
