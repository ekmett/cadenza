plugins {
  java
  application
}

repositories {
  jcenter()
}

dependencies {
  annotationProcessor("org.graalvm.truffle:truffle-api:19.2.0.1")
  annotationProcessor("org.graalvm.truffle:truffle-dsl-processor:19.2.0.1")
  implementation("org.graalvm.truffle:truffle-api:19.2.0.1")
  implementation("org.graalvm.truffle:truffle-tck:19.2.0.1")
  implementation("org.graalvm.sdk:graal-sdk:19.2.0.1")
  testImplementation("org.testng:testng:6.14.3")
}

application {
  mainClassName = "core.Main"
  applicationDefaultJvmArgs = listOf("-XX:+UnlockExperimentalVMOptions","-XX:+EnableJVMCI")
}

val test by tasks.getting(Test::class) {
  useTestNG()
}
