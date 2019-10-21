# cadenza

[![Travis Continuous Integration Status][travis-img]][travis]
![Most used language][top-language-img]

This package will eventually provide a normalized-by-evaluation lambda calculus implementation in java using truffle. With an eye towards efficient evaluation.

cadenza | \ kə-ˈden-zə \ (noun) 1. an virtuosic solo section before the final coda used to display a performer's technique, which is often considerably long

Nothing seems to say Java moreso than considerable length, so here we are.

## running

* `gradle run --args="<args>"` should just download GraalVM CE automatically and use it to run the launcher for testing.

* `gradle nativeImage` should eventually produce a native executable for the compiler. (Once I figure out how to include everything.)

* `gradle register` will install the language into GraalVM making it available to other truffle languages once you rebuild their images with `gu`.

The install will download the latest Graal CE edition into a cache with gradle and install there. TODO: I should add an option for a manual Graal path.

The code is built from java 12 source code (with access to only java 8 libraries) and so your host JVM should be jdk12+. Runtime will be performed against the downloaded graal installation.

## using your own graal

Set JAVA_HOME to a site where you have installed jdk12+ and set GRAAL_HOME to a directory with graal-ce or graal-ee installed.

## running by hand

* Set your `JAVA_HOME`. If you used `gradle extractGraalTooling` it should point to something in `~/.gradle/caches/com.palantir.graal/19.2.0.1/graalvm-ce-19.2.0.1`

* Once you've built the jar with `gradle jar`, `java -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI -Dtruffle.class.path.append=build/libs/cadenza.jar -jar build/libs/cadenza.jar` seems to run the top level application without polyglot complaining about not having the language installed. IntelliJ has different IDEAs about what the arguments should be when you run it in debug more, however.

* `make component` should install things with `gu`, to make it so this is a viable language we could use in other polyglot repls easily. I'm missing some steps, though. (Likely I need to rebuild polyglot?)

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
