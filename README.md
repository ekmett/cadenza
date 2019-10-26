# cadenza

[![Travis Continuous Integration Status][travis-img]][travis]
![Most used language][top-language-img]

This package will eventually provide a normalized-by-evaluation lambda calculus implementation in ~~Java~~ Kotlin using truffle with an eye towards efficient evaluation.

cadenza | \ kə-ˈden-zə \ (noun) 1. an virtuosic solo section before the final coda used to display a performer's technique, which is often considerably long

Kotlin code tends to be considerably long, if not so long as the Java code I started this project with, and this is a stepping stone on the way to my [coda](https://github.com/ekmett/coda.git), so here we are.

## Running

If `GRAAL_HOME` or `JAVA_HOME` point to a GraalVM installation, we'll use that. Otherwise `gradle` will download it automatically and place it some place like `~/.gradle/caches/com.palantir.graal/19.2.0.1/graalvm-ce-19.2.0.1`.

* `gradle run` will run the launcher out of the local directory without installing anything.

* `gradle runRegistered` will register the language with Graal and run the launcher from the java home directory

* `gradle runInstalled` will use `gradle installDist` and run out of `build/install/cadenza/bin/cadenza`

## Installing

* `gradle nativeImage` should eventually produce a native executable for the compiler. If I carry on with some of my intended dynamic classloader tricks to construct environments it may be a big less efficient than the jvm version, or become unsupported, however.

* `gradle distZip` or `gradle distTar` will create an archive containing the required runtime jars. However, you'll need to have `JAVA_HOME` set to point to your Graal installation, if you want to use the `cadenza` script from the installation folder.

* `gradle register` will directly install the language into GraalVM making it available to other truffle languages once you `gu rebuild-images`. To launch the script you'll currently need to have `JAVA_HOME` set to point to your Graal installation. (The script could be modified from a standard gradle wrapper to fix this case, unlike above, so this is a temporary situation.)

## TODO

This code is very much a work-in-progress.

* The intention is to support an [eval-apply](https://www.microsoft.com/en-us/research/publication/make-fast-curry-pushenter-vs-evalapply/) execution model, which is a bit strange in the ecosystem of truffle languages. A lot of work is going into trying to figure out how to make that efficient.

* I'm hopeful that I can use truffle rewrites to dynamically trampoline tail-calls, degrading tail positions from a set of recursive calls, to something that handles self-tailcalls, to something that handles self-tailcalls with differing environments to something that does an arbitrary trampoline for the worst case. This would enable us to trust the space usage.

* Currently, Normalization-by-evaluation proceeds through an exceptional control flow path. Is this the right direction going forward if I move to dependent types?


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
 [travis]: http://travis-ci.org/ekmett/cadenza
 [travis-img]: https://secure.travis-ci.org/ekmett/cadenza.png?branch=master
 [top-language-img]: https://img.shields.io/github/languages/top/ekmett/cadenza
 [license]: https://raw.githubusercontent.com/ekmett/cadenza/master/LICENSE.txt
