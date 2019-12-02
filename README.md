# cadenza

[![Travis Continuous Integration Status][travis-img]][travis]
![Most used language][top-language-img]

This package will eventually provide a normalized-by-evaluation lambda calculus implementation in ~~Java~~ Kotlin using truffle with an eye towards efficient evaluation.

cadenza | \ kə-ˈden-zə \ (noun) 1. a considerably long virtuosic solo section before the final [coda](https://github.com/ekmett/coda) used to display a performer's technique

Kotlin code tends to be considerably long, if not so long as the Java code I started this project with.

## Running

Ensure `JAVA_HOME` points to a JDK 11 instalation.

* `gradle run` will run the launcher out of the local directory without installing anything.

## Installing

* `gradle distZip` or `gradle distTar` will create an archive containing the required runtime jars. However, you'll need to have `JAVA_HOME` set to point to your Graal installation, if you want to use the `cadenza` script from the installation folder.

## TODO

This code is very much a work-in-progress.

* The intention is to support an [eval-apply](https://www.microsoft.com/en-us/research/publication/make-fast-curry-pushenter-vs-evalapply/) execution model, which is a bit strange in the ecosystem of truffle languages. A lot of work is going into trying to figure out how to make that efficient.

* I'm hopeful that I can use truffle rewrites to dynamically trampoline tail-calls, degrading tail positions from a set of recursive calls, to something that handles self-tailcalls, to something that handles self-tailcalls with differing environments to something that does an arbitrary trampoline for the worst case. This would enable us to trust the space usage.

* Currently, Normalization-by-evaluation proceeds through an exceptional control flow path. It is worth noting this is basically completely irrelevant if we're seeking "just a better Haskell runtime" but it is pretty important for dependent types. Different ways to pass around neutral values should probably be explored.

## Windows XP

In the unlikely event that anybody cares about this project that also uses Windows XP, one of the dependencies used for color pretty printing depends on the "Microsoft Visual C++ 2008 SP1 Redistributable" when invoked on Windows.

You can get a free copy from MS at:

http://www.microsoft.com/en-us/download/details.aspx?displaylang=en&id=5582

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
