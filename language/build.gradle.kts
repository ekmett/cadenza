apply(plugin = "antlr")
apply(plugin = "kotlin")
apply(plugin = "kotlin-kapt")
apply(plugin = "org.jetbrains.dokka")

val antlrRuntime by configurations.creating
val kotlinRuntime by configurations.creating

dependencies {
  "kapt"("org.graalvm.truffle:truffle-api:19.2.0.1")
  "kapt"("org.graalvm.truffle:truffle-dsl-processor:19.2.0.1")
  "antlr"("org.antlr:antlr4:4.7.2")
  antlrRuntime("org.antlr:antlr4-runtime:4.7.2")
  implementation("org.graalvm.truffle:truffle-api:19.2.0.1")
  implementation("org.graalvm.sdk:graal-sdk:19.2.0.1")
  implementation("org.antlr:antlr4-runtime:4.7.2")
  kotlinRuntime(kotlin("stdlib"))
  kotlinRuntime(kotlin("stdlib-jdk8"))
  testImplementation("org.testng:testng:6.14.3")
}

tasks.getByName<Jar>("jar") {
  baseName = "cadenza-language"
  manifest {
    attributes["Main-Class"] = "cadenza.Main"
    attributes["Specification-Title"] = "cadenza-language"
    attributes["Specification-Version"] = project.version
    attributes["Specification-Vendor"] = "Machine Intelligence Research Institute"
    attributes["Implementation-Title"] = "cadenza-language"
    attributes["Implementation-Version"] = project.version
    attributes["Implementation-Vendor"] = "Machine Intelligence Research Institute"
    attributes["Class-Path"] =  configurations.runtimeClasspath.get().files.map { it.getAbsolutePath() } .joinToString(separator = " ")
  }
}

tasks.withType<AntlrTask> {
  arguments.addAll(listOf("-package", "cadenza.syntax", "-no-listener", "-visitor"))
}
