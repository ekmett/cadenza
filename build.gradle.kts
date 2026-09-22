plugins {
  application
  idea
  kotlin("jvm") version "2.4.20"
  kotlin("kapt") version "2.4.20"
}

repositories { mavenCentral() }

val graalVersion = "25.3.4.1"

dependencies {
  implementation(kotlin("stdlib"))
  arrayOf("asm", "asm-tree").forEach { implementation("org.ow2.asm:$it:9.8") }
  implementation("org.fusesource.jansi:jansi:2.4.2")
  implementation("org.graalvm.polyglot:polyglot:$graalVersion")
  implementation("org.graalvm.sdk:launcher-common:$graalVersion")
  implementation("org.graalvm.truffle:truffle-api:$graalVersion")
  runtimeOnly("org.graalvm.truffle:truffle-runtime:$graalVersion")
  kapt("org.graalvm.truffle:truffle-dsl-processor:$graalVersion")
  testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin { jvmToolchain(25) }

sourceSets {
  main {
    java.setSrcDirs(listOf("src"))
    kotlin.setSrcDirs(listOf("src"))
  }
  test { kotlin.setSrcDirs(listOf("test")) }
}

val bench = sourceSets.create("bench") {
  java.setSrcDirs(listOf("bench"))
  kotlin.setSrcDirs(listOf("bench"))
  compileClasspath += sourceSets.main.get().output
  runtimeClasspath += sourceSets.main.get().output
}
configurations[bench.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[bench.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())
dependencies {
  add(bench.implementationConfigurationName, "org.openjdk.jmh:jmh-core:1.37")
  add("kaptBench", "org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

application {
  mainClass.set("cadenza.Launcher")
  applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED", "-Xss32m")
}

tasks.test {
  useJUnitPlatform()
  jvmArgs(application.applicationDefaultJvmArgs)
  testLogging { events("failed", "skipped") }
}

tasks.register<Test>("semanticSoak") {
  group = "verification"
  description = "Check generated programs against independent semantics (configurable fuzzCases/fuzzSeedOffset)."
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  useJUnitPlatform()
  jvmArgs(application.applicationDefaultJvmArgs)
  maxHeapSize = "768m"
  filter { includeTestsMatching("DifferentialTests") }
  systemProperty("cadenza.fuzz.cases", providers.gradleProperty("fuzzCases").getOrElse("10000"))
  systemProperty("cadenza.fuzz.seedOffset", providers.gradleProperty("fuzzSeedOffset").getOrElse("0"))
  testLogging { events("failed", "skipped") }
}

tasks.register<JavaExec>("bench") {
  group = "verification"
  description = "Run JMH benchmarks (use --args to select benchmarks and options)."
  classpath = bench.runtimeClasspath
  mainClass.set("org.openjdk.jmh.Main")
  jvmArgs(application.applicationDefaultJvmArgs)
}

tasks.jar {
  manifest { attributes["Main-Class"] = "cadenza.Launcher" }
}

distributions.main { contents { from("LICENSE.txt") } }

tasks.register<Exec>("runInstalled") {
  group = "application"
  dependsOn(tasks.installDist)
  commandLine(layout.buildDirectory.file("install/cadenza/bin/cadenza").get().asFile)
}
