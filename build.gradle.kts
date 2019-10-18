import com.palantir.gradle.graal.ExtractGraalTask;
import com.palantir.gradle.graal.NativeImageTask;

repositories {
  jcenter()
  mavenCentral()
}

allprojects {
  repositories {
    jcenter()
    mavenCentral()
  }
}

subprojects {
  version = "0.0"
}

plugins {
  id("org.sonarqube") version "2.7.1"
  id("com.palantir.graal") version "0.6.0"
}

graal {
  graalVersion("19.2.0.1")
  mainClass("cadenza.Launcher")
  outputName("cadenza-native")
  option("--language:cadenza")
}

sonarqube {
  properties {
    property("sonar.projectKey","ekmett_cadenza")
    property("sonar.sourceEncoding","UTF-8")
  }
}

val os = System.getProperty("os.name")
val graalToolingDir = tasks.named<ExtractGraalTask>("extractGraalTooling").get().getOutputDirectory().get().getAsFile().toString()
var graalHome = if (os == "Mac OS X") "$graalToolingDir/Contents/Home" else graalToolingDir
val graalBinDir = if (os == "Linux") graalHome else "$graalHome/bin"

// change the launcher run task to use GraalVM
tasks.withType<JavaExec> {
  dependsOn("extractGraalTooling","jar")
  executable = "$graalBinDir/java"
}

/*
val componentJar = project(":component").tasks.getByName<Jar>("jar")

// register the component
tasks.register("register", Exec::class) {
  dependsOn("extractGraalTooling", componentJar)
  description = "Register the language with graal"
  commandLine = listOf("$graalBinDir/gu","install","-f","-L",componentJar.archiveFileName)
}
*/

// build cadenza-native
tasks.withType<NativeImageTask> {
  // dependsOn("register")
}
