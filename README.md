* [Install GraalVM](https://www.graalvm.org/downloads/)

* Set your `JAVA_HOME` to point to the modified JDK you just downloaded.

* `make run`

* Once you've built the jar, `java -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI -Dtruffle.class.path.append=build/libs/cadenza.jar -jar build/libs/cadenza.jar` seems to run the top level application without polyglot complaining about not having the language installed. IntelliJ has different IDEAs about what the arguments should be when you run it in debug more, however.

* `make component` should install things with `gu`, to make it so this is a viable language we could use in other polyglot repls easily. I'm missing some steps, though. (Likely I need to rebuild polyglot?)
