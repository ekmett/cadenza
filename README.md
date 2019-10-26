# cadenza

[![Travis Continuous Integration Status][travis-img]][travis]
![Most used language][top-language-img]

This package will eventually provide a normalized-by-evaluation lambda calculus implementation in ~~Java~~ Kotlin using truffle. With an eye towards efficient evaluation.

cadenza | \ kə-ˈden-zə \ (noun) 1. an virtuosic solo section before the final coda used to display a performer's technique, which is often considerably long

Nothing seems to say ~~Java~~ Kotlin moreso than considerable length, so here we are.

## running

If `GRAAL_HOME` or `JAVA_HOME` point to a GraalVM installation, we'll use that. Otherwise `gradle` will download it automatically and place it in `~/.gradle/caches/com.palantir.graal/19.2.0.1/graalvm-ce-19.2.0.1`.

* `gradle run` will run the launcher out of the local directory

* `gradle runRegistered` will register the language with Graal and run the launcher from the java home directory

* `gradle runInstalled` will use `gradle installDist` and run out of `build/install/cadenza/bin/cadenza`

* `gradle nativeImage` should eventually produce a native executable for the compiler.

* `gradle register` will install the language into GraalVM making it available to other truffle languages once you `gu rebuild-images`

Contribution
============

Unless you explicitly state otherwise, any contribution intentionally submitted
for inclusion in the work by you shall be licensed as per [LICENSE.txt][license], without any
additional terms or conditions.

Contact Information
===================

Contributions and bug reports are welcome!

Please feel free to contact me through github or on the ##coda or #haskell IRC channels on irc.freenode.net.

-Edward Kmett

 [graalvm]: https://www.graalvm.org/downloads
 [travis]: http://travis-ci.org/ekmett/cadenza
 [travis-img]: https://secure.travis-ci.org/ekmett/cadenza.png?branch=master
 [top-language-img]: https://img.shields.io/github/languages/top/ekmett/cadenza
 [license]: https://raw.githubusercontent.com/ekmett/cadenza/master/LICENSE.txt
