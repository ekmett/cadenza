
apply(plugin = "antlr")
apply(plugin = "kotlin")
apply(plugin = "kotlin-kapt")

val antlrRuntime by configurations.creating

dependencies {
  compile("com.palantir.safe-logging:preconditions:1.11.0")
  testCompile("com.palantir.safe-logging:preconditions-assertj:1.11.0")
  "kapt"("org.graalvm.truffle:truffle-api:19.2.0.1")
  "kapt"("org.graalvm.truffle:truffle-dsl-processor:19.2.0.1")
  "antlr"("org.antlr:antlr4:4.7.2")
  "antlrRuntime"("org.antlr:antlr4-runtime:4.7.2")
  implementation("org.graalvm.truffle:truffle-api:19.2.0.1")
  implementation("org.graalvm.sdk:graal-sdk:19.2.0.1")
  implementation("org.antlr:antlr4-runtime:4.7.2")
  testImplementation("org.testng:testng:6.14.3")
  implementation("com.palantir.safe-logging:safe-logging")
  implementation("com.palantir.safe-logging:preconditions")
}

tasks.getByName<Jar>("jar") {
  baseName = "cadenza-language"
}

tasks.withType<AntlrTask> {
  arguments.addAll(listOf("-package", "cadenza.syntax", "-no-listener", "-visitor"))
}
