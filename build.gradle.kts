import com.palantir.gradle.graal.ExtractGraalTask;
import com.palantir.gradle.graal.NativeImageTask;

group = project.properties["group"].toString()
version = project.properties["version"].toString()

buildscript {
  repositories {
    mavenCentral()
  }
  dependencies {
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.41")
  }
}

repositories {
  mavenCentral()
}

//dependencies {
//  compile("org.jetbrains.kotlin:kotlin-stdlib:1.3.41")
//}

allprojects {
  repositories {
    jcenter()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("http://palantir.bintray.com/releases") }
  }

  apply(plugin = "kotlin")

  dependencies {
    implementation(kotlin("stdlib"))
  }
}

plugins {
  application
  idea
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
val systemGraalHome = System.getenv("GRAAL_HOME")
val needsExtract = systemGraalHome == null
val graalToolingDir = tasks.getByName<ExtractGraalTask>("extractGraalTooling").getOutputDirectory().get().getAsFile().toString()
var graalHome = if (needsExtract)
  (if (os == "Mac OS X") "$graalToolingDir/Contents/Home" else graalToolingDir)
  else systemGraalHome
val graalBinDir = if (os == "Linux") graalHome else "$graalHome/bin"

subprojects {
  version = project.properties["version"]
}

project(":component") {
  val jar = tasks.getByName<Jar>("jar")

  tasks.register("register", Exec::class) {
    if (needsExtract) dependsOn("extractGraalTooling")
    dependsOn(jar)
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
  if (needsExtract) dependsOn(":extractGraalTooling")
  dependsOn(":language:jar",":launcher:jar")
  executable = "$graalBinDir/java"
}
