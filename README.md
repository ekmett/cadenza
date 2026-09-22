# cadenza

[![Build](https://github.com/ekmett/cadenza/actions/workflows/build.yml/badge.svg)](https://github.com/ekmett/cadenza/actions/workflows/build.yml)
![Most used language][top-language-img]

This package will eventually provide a normalized-by-evaluation lambda calculus implementation in ~~Java~~ Kotlin using truffle with an eye towards efficient evaluation.

cadenza | \ kə-ˈden-zə \ (noun) 1. a considerably long virtuosic solo section before the final [coda](https://github.com/ekmett/coda) used to display a performer's technique

Kotlin code tends to be considerably long, if not so long as the Java code I started this project with.

## Running

Use **GraalVM 25.3.4.1 (JDK 25)** with the matching Truffle 25.3.4.1 libraries.
The build uses the checked-in Gradle 9.7.1 wrapper and Kotlin 2.4.20; dependencies
come from Maven Central.

Download GraalVM for your platform from [GraalVM downloads](https://www.graalvm.org/downloads/)
and set `JAVA_HOME` to its JDK home (`Contents/Home` inside the bundle on macOS):

```sh
export JAVA_HOME=/path/to/graalvm/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew test
./gradlew run --args='examples/fib.za'
```

`./gradlew run` defaults to `examples/add.za`, which counts to 20,000,000.
The launcher prints `Result: ...`. The language is still a prototype: use the
explicitly typed syntax in `add.za`, `fib.za`, and `collatz.za`; `fibRec.za`
contains an older, untyped syntax that the current parser does not accept.

GraalVM is needed for optimizing guest compilation with Truffle 25.1 and later.
A plain JDK can provide an interpreter fallback, but is not the supported JIT
configuration for this project. There is no longer a separate compiler JAR to
put on the upgrade module path or a language component to install with `gu`.

## Installing

```sh
./gradlew installDist
build/install/cadenza/bin/cadenza examples/fib.za
```

Keep `JAVA_HOME` set to GraalVM when running the installed launcher.
`./gradlew distZip` and `./gradlew distTar` produce portable application archives
containing Cadenza and its runtime dependencies; the JDK is supplied separately.

To see guest compilation (including on-stack replacement of long-running loops):

```sh
build/install/cadenza/bin/cadenza --experimental-options \
  --engine.TraceCompilation=true --engine.BackgroundCompilation=false examples/add.za
```

The initial migration was checked on macOS ARM64 with Oracle GraalVM 25.3.4.1:
the test suite, distribution build, and JMH smoke run pass; `add.za` returns
20,000,000 with tier-2 OSR compilation. JDK 25 currently prints an upstream
`sun.misc.Unsafe::objectFieldOffset` deprecation warning from Truffle at startup.

## Experimental bytecode backend

The AST interpreter remains the default. Try the concrete typed-core Bytecode DSL backend with:

```sh
build/install/cadenza/bin/cadenza --experimental-options --cadenza.Backend=bytecode examples/fib.za
```

See [backend coverage and limitations](docs/bytecode.md). Both backends share the
parser, type checker, closure calling convention, and tail-call trampoline.

## Benchmarks

```sh
./gradlew bench --args='-l'
./gradlew bench --args='-wi 3 -i 3 -f 1'
```

See [benchmark methodology](bench/README.md) for warm, cold, allocation, and
neutral-path benchmarks, runtime-varying inputs, and GC profiling.

The runtime uses primitive-specialized indexed frame slots, immutable closure
captures backed by Truffle StaticShape, bounded dispatch caches, and shared guest
and polyglot calling conventions. Neutral terms deliberately use the exceptional
SlowPathException path: they are temporary values outside ordinary execution.

The migration uses generated language providers,
and the public Truffle loop API. The old reflective OSR factory, `gu` component
packaging, build-scan uploads, and obsolete documentation publishing plugins
have been removed from the build. Native Image packaging is not configured.

## TODO

This code is very much a work-in-progress.

* The intention is to support an [eval-apply](https://www.microsoft.com/en-us/research/publication/make-fast-curry-pushenter-vs-evalapply/) execution model, which is a bit strange in the ecosystem of truffle languages. A lot of work is going into trying to figure out how to make that efficient.

* I'm hopeful that I can use truffle rewrites to dynamically trampoline tail-calls, degrading tail positions from a set of recursive calls, to something that handles self-tailcalls, to something that handles self-tailcalls with differing environments to something that does an arbitrary trampoline for the worst case. This would enable us to trust the space usage.

* Normalization by evaluation deliberately sends exotic, temporary neutral terms
  through an exceptional control flow path; ordinary evaluation remains the fast path.

## Contribution

Unless you explicitly state otherwise, any contribution intentionally submitted
for inclusion in the work by you shall be licensed as per [LICENSE.txt][license], without any
additional terms or conditions.

Contact Information
===================

Contributions and bug reports are welcome!

Please feel free to contact me through github or on the ##coda or #haskell IRC channels on irc.freenode.net.

-Edward Kmett

 [graalvm]: https://www.graalvm.org/downloads
 [top-language-img]: https://img.shields.io/github/languages/top/ekmett/cadenza
 [license]: https://raw.githubusercontent.com/ekmett/cadenza/master/LICENSE.txt
