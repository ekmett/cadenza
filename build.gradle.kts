import com.palantir.gradle.graal.ExtractGraalTask;
import com.palantir.gradle.graal.NativeImageTask;

allprojects {
  apply(plugin = "java")
  repositories {
    jcenter()
    mavenCentral()
  }
  java {
    sourceCompatibility = JavaVersion.VERSION_1_8 // TODO: jabel
    targetCompatibility = JavaVersion.VERSION_1_8
  }
}

plugins {
  maven
  application
  id("org.sonarqube") version "2.7.1"
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
  version = project.properties["version"] // "0.0-SNAPSHOT"
}

sonarqube {
  properties {
    property("sonar.projectKey","ekmett_cadenza")
    property("sonar.sourceEncoding","UTF-8")
  }
}

project(":language") {
  apply (plugin="antlr")
  apply (plugin="java-library")
  dependencies {
    annotationProcessor("org.graalvm.truffle:truffle-api:19.2.0.1")
    annotationProcessor("org.graalvm.truffle:truffle-dsl-processor:19.2.0.1")
    "antlr"("org.antlr:antlr4:4.7.2")
    implementation("org.graalvm.truffle:truffle-api:19.2.0.1")
    implementation("org.graalvm.sdk:graal-sdk:19.2.0.1")
    implementation("org.antlr:antlr4-runtime:4.7.2")
    testImplementation("org.testng:testng:6.14.3")
  }
  tasks.getByName<Jar>("jar") {
    baseName = "cadenza-language"
  }
}

project(":launcher") {
  dependencies {
    implementation(project(":language"))
    implementation("org.graalvm.truffle:truffle-api:19.2.0.1")
    implementation("org.graalvm.sdk:launcher-common:19.2.0.1")
  }
  tasks.getByName<Jar>("jar") {
    baseName = "cadenza-launcher"
  }
}

project(":component") {
  apply(plugin = "java")
  tasks.withType<ProcessResources> {
    from("native-image.properties") {
      expand(project.properties)
    }
    rename("native-image.properties","jre/languages/cadenza/native-image.properties")
  }
  val jar = tasks.getByName<Jar>("jar") {
    baseName = "cadenza-component"
    from(tasks.getByPath(":language:jar"))
    from(tasks.getByPath(":launcher:jar"))
    rename("(.*).jar","jre/languages/cadenza/\$1.jar")
    manifest {
      attributes["Bundle-Name"] = "Cadenza"
      attributes["Bundle-Description"] = "The cadenza language"
      attributes["Bundle-DocURL"] = "https://github.com/ekmett/cadenza"
      attributes["Bundle-Symbolic-Name"] = "cadenza"
      attributes["Bundle-Version"] = project.version.toString()
      attributes["Bundle-RequireCapability"] = "org.graalvm;filter:=\"(&(graalvm_version=19.2.0)(os_arch=amd64))\""
      attributes["x-GraalVM-Polyglot-Part"] = "True"
    }
  }
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
