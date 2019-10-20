import com.palantir.gradle.graal.ExtractGraalTask;
import com.palantir.gradle.graal.NativeImageTask;

repositories {
  jcenter()
  mavenCentral()
}

plugins {
  java
  maven
  application
  id("org.sonarqube") version "2.7.1"
  id("com.palantir.graal") version "0.6.0"
}

dependencies {
  runtime(project(":language"))
  runtime(project(":launcher"))
}

allprojects {
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
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
  version = "0.0-SNAPSHOT"
}

sonarqube {
  properties {
    property("sonar.projectKey","ekmett_cadenza")
    property("sonar.sourceEncoding","UTF-8")
  }
}

project(":launcher") {
  repositories {
    jcenter()
    mavenCentral()
  }
  apply(plugin = "java")
  dependencies {
    implementation(project(":language"))
    implementation("org.graalvm.sdk:launcher-common:19.2.0.1")
    implementation("org.graalvm.truffle:truffle-api:19.2.0.1")
  }
  java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  tasks.getByName<Jar>("jar") {
    baseName = "cadenza-launcher"
  }
}

project(":component") {
  apply(plugin = "java")
  val jar = tasks.getByName<Jar>("jar") {
    baseName = "cadenza-component"
    from("src/component/resources")
    from(tasks.getByPath(":language:jar"))
    from(tasks.getByPath(":launcher:jar"))
    rename("(.*).jar","jre/languages/cadenza/\$1.jar")
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
  tasks.register("register", Exec::class) {
    dependsOn(":extractGraalTooling", jar)
    description = "Register the language with graal"
    commandLine = listOf("$graalBinDir/gu","install","-f","-L","build/libs/cadenza-component-0.0-SNAPSHOT.jar")
  }
}

tasks.getByName<NativeImageTask>("nativeImage") {
  dependsOn("register")
}

application {
  mainClassName = "cadenza.launcher.Launcher"
  applicationDefaultJvmArgs = listOf("-XX:+UnlockExperimentalVMOptions","-XX:+EnableJVMCI","-Dtruffle.class.path.append=language/build/libs/cadenza-0.0-SNAPSHOT.jar")
}

tasks.getByName<JavaExec>("run") {
  dependsOn(":extractGraalTooling",":language:jar",":launcher:jar")
  // classpath = files(fileTree("language/build/libs/"),fileTree("launcher/build/libs/"))
  executable = "$graalBinDir/java"
}
