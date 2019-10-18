import com.palantir.gradle.graal.ExtractGraalTask;
import com.palantir.gradle.graal.NativeImageTask;

repositories {
  jcenter()
  mavenCentral()
}

plugins {
  antlr
  application
  java
  maven
  id("org.sonarqube") version "2.7.1"
  id("com.palantir.graal") version "0.6.0"
}

// let us collect some code advisory data from sonarqube
sonarqube {
  properties {
    property("sonar.projectKey","ekmett_cadenza")
    property("sonar.sourceEncoding","UTF-8")
  }
}


dependencies {
  annotationProcessor("org.graalvm.truffle:truffle-api:19.2.0.1")
  annotationProcessor("org.graalvm.truffle:truffle-dsl-processor:19.2.0.1")
  antlr("org.antlr:antlr4:4.7.2")
  implementation("org.graalvm.truffle:truffle-api:19.2.0.1")
  implementation("org.graalvm.truffle:truffle-tck:19.2.0.1")
  implementation("org.graalvm.sdk:graal-sdk:19.2.0.1")
  implementation("org.graalvm.sdk:launcher-common:19.2.0.1")
  implementation("org.antlr:antlr4-runtime:4.7.2")
  testImplementation("org.testng:testng:6.14.3")
  // implementation("com.google.guava:guava:28.1-jre")
}

tasks.register("cadenza", JavaCompile::class) {
  source = fileTree("src/cadenza/java")
}

// gradle run is a bit more convenient
application {
  mainClassName = "cadenza.Launcher"
  applicationDefaultJvmArgs = listOf("-XX:+UnlockExperimentalVMOptions","-XX:+EnableJVMCI","-Dtruffle.class.path.append=$buildDir/libs/cadenza.jar")
}

graal {
  graalVersion("19.2.0.1")
  mainClass("cadenza.Launcher")
  outputName("cadenza-native")
  option("--language:cadenza")
}

val os = System.getProperty("os.name")

val graalToolingDir = tasks.named<ExtractGraalTask>("extractGraalTooling").get().getOutputDirectory().get().getAsFile().toString()
var graalHome = if (os == "Mac OS X") "$graalToolingDir/Contents/Home" else graalToolingDir
val graalBinDir = if (os == "Linux") graalHome else "$graalHome/bin"

// cause gradle run to use the GraalVM
tasks.withType<JavaExec> {
  dependsOn("extractGraalTooling","jar")
  executable = "$graalBinDir/java"
}

tasks.register("cadenzaJar", Jar::class) {
  from("cadenza")
  baseName = "cadenza"
}

// build cadenza-component.jar
tasks.register("component", Jar::class) {
  dependsOn("cadenzaJar","jar")
  baseName = "cadenza-component"
  from("src/component/resources")
  from(tasks["cadenzaJar"])
  rename("cadenza.jar","jre/languages/cadenza/cadenza.jar")
  from(tasks["jar"])
  rename("cadenza-launcher.jar","jre/languages/cadenza/cadenza-launcher.jar")
  manifest {
    attributes["Bundle-Name"] = "Cadenza"
    attributes["Bundle-Description"] = "The cadenza language"
    attributes["Bundle-DocURL"] = "https://github.com/ekmett/cadenza"
    attributes["Bundle-Symbolic-Name"] = "cadenza"
    attributes["Bundle-Version"] = "0.0"
    attributes["Bundle-RequireCapability"] = "org.graalvm;filter:=\"(&(graalvm_version=19.2.0)(os_arch=amd64))\""
    attributes["x-GraalVM-Polyglot-Part"] = "True"
  }
}

// register the component
tasks.register("installComponent", Exec::class) {
  dependsOn("extractGraalTooling", "component")
  description = "Register with polyglot via gu"
  commandLine = listOf("$graalBinDir/gu","install","-f","-L","build/libs/cadenza-component.jar")
}

// build cadenza-native
tasks.withType<NativeImageTask> {
  dependsOn("install") // make sure we have a language first
}

tasks.getByName<JavaCompile>("compileJava") {
  classPath = plus(sourceSets.compileClasspath,files(cadenzaJar))
}

tasks.getByName<Jar>("jar") {
  baseName = "cadenza-launcher"
}

val test by tasks.getting(Test::class) {
  useTestNG()
}

// graal is jvm 1.8, even if we're compiling on something else
java {
  sourceCompatibility = JavaVersion.VERSION_1_8 // todo: jabel to relax this and get "var"
  targetCompatibility = JavaVersion.VERSION_1_8
}

// defaultTasks("run")
