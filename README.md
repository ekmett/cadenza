* [Install GraalVM](https://www.graalvm.org/downloads/)

* Set your `JAVA_HOME` to point to the modified JDK you just downloaded.

* `make run`

* `java -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI -Dtruffle.class.path.append=build/libs/core.jar -jar build/libs/core.jar` seems to run the top level application without polyglot complaining about not having the language installed
