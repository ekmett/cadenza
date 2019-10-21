import com.palantir.gradle.graal.ExtractGraalTask;
import com.palantir.gradle.graal.NativeImageTask;

group = project.properties["group"].toString()
version = project.properties["version"].toString()

buildscript {
  repositories {
    gradlePluginPortal()
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("http://palantir.bintray.com/releases") }
  }

  dependencies {
    classpath("com.palantir.baseline:gradle-baseline-java:2.24.0")
    classpath("gradle.plugin.org.inferred:gradle-processors:2.1.0")
    classpath("${project.group}:gradle:${project.version}")
  }
}

allprojects {
  repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("http://palantir.bintray.com/releases") }
  }

  apply(plugin = "java")
  apply(plugin = "org.inferred.processors")
  apply(plugin = "com.palantir.baseline-versions")
  apply(plugin = "com.palantir.baseline-idea")

  java {
    sourceCompatibility = JavaVersion.VERSION_12
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  dependencies {
    annotationProcessor("${project.group}:gradle:${project.version}")
  }

  tasks.withType<JavaCompile> {
    options.compilerArgs = listOf("--release","8")
  }
}

plugins {
  application
  idea
  id("org.sonarqube") version "2.7.1"
  id("com.palantir.baseline-config") version "2.24.0"
  id("com.palantir.graal") version "0.6.0"
}

dependencies {
  runtime(project(":language"))
  runtime(project(":launcher"))
}

graal {
  graalVersion("19.2.0.1")
  mainClass("cadenza.launcher.Launcher")
  outputName("cadenza-native")
  option("--language:cadenza")
}

val os = System.getProperty("os.name")
val graalToolingDir = tasks.getByName<ExtractGraalTask>("extractGraalTooling").getOutputDirectory().get().getAsFile().toString()
var graalHome = if (os == "Mac OS X") "$graalToolingDir/Contents/Home" else graalToolingDir
val graalBinDir = if (os == "Linux") graalHome else "$graalHome/bin"

subprojects {
  version = project.properties["version"]
}

sonarqube {
  properties {
    property("sonar.projectKey","ekmett_cadenza")
    property("sonar.sourceEncoding","UTF-8")
  }
}

project(":component") {
  val jar = tasks.getByName<Jar>("jar")

  // register the component
  tasks.register("register", Exec::class) {
    dependsOn(":extractGraalTooling", jar)
    description = "Register the language with graal"
    commandLine = listOf(
      "$graalBinDir/gu",
      "install",
      "-f",
      "-L",
      jar.archiveFile.get().getAsFile().getPath()
    )
  }
}

tasks.getByName<NativeImageTask>("nativeImage") {
  dependsOn(":component:register")
}

application {
  mainClassName = "cadenza.launcher.Launcher"
  applicationDefaultJvmArgs = listOf(
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+EnableJVMCI",
    "-Dtruffle.class.path.append=" + project("language").tasks.getByName<Jar>("jar").archiveFile.get().getAsFile().getPath()
  )
}

tasks.getByName<JavaExec>("run") {
  dependsOn(":extractGraalTooling",":language:jar",":launcher:jar")
  executable = "$graalBinDir/java"
}
