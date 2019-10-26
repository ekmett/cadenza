import com.palantir.gradle.graal.ExtractGraalTask;
import org.apache.tools.ant.filters.ReplaceTokens
import com.palantir.gradle.graal.NativeImageTask;
import java.util.Properties;

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
    implementation(kotlin("stdlib-jdk8"))
  }
}

plugins {
  application
  idea
  id("com.palantir.graal") version "0.6.0"
  id("org.jetbrains.dokka") version "0.9.17" apply false
}

dependencies {
  implementation(project(":language"))
  implementation(project(":launcher"))
}

graal {
  graalVersion("19.2.0.1")
  mainClass("cadenza.launcher.Launcher")
  outputName("cadenza-native")
  option("--language:cadenza")
}

val os = System.getProperty("os.name")
var graalHome = System.getenv("GRAAL_HOME")

if (graalHome == null) {
  val javaHome = System.getenv("JAVA_HOME")
  val releaseFile = file("${javaHome}/release")
  if (releaseFile.exists()) {
    val releaseProps = Properties()
    releaseProps.load(releaseFile.inputStream())
    if (releaseProps.getProperty("GRAALVM_VERSION") != null) graalHome = javaHome
  }
}

val needsExtract = graalHome == null
if (needsExtract) {
  val graalToolingDir = tasks.getByName<ExtractGraalTask>("extractGraalTooling").getOutputDirectory().get().getAsFile().toString()
  graalHome = if (os == "Mac OS X") "$graalToolingDir/Contents/Home" else graalToolingDir
}

val graalBinDir = if (os == "Linux") graalHome else "$graalHome/bin"

subprojects {
  version = project.properties["version"]
}

application {
  mainClassName = "cadenza.launcher.Launcher"
  applicationDefaultJvmArgs = listOf(
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+EnableJVMCI",
    "-Dtruffle.class.path.append=@CADENZA_APP_HOME@/lib/cadenza-language-${project.version}.jar"
    // "-Dtruffle.class.path.append=language/build/libs/cadenza-language-${project.version}.jar"
  )
}

tasks.withType<ProcessResources> {
  from("native-image.properties") {
    expand(project.properties)
  }
  rename("native-image.properties","jre/languages/cadenza/native-image.properties")
}

distributions {
  main {
    baseName = "cadenza"
    contents {
      exclude(
        "graal-sdk*.jar", "truffle-api*.jar", "launcher-common*.jar",
        "antlr4-4*.jar", "javax.json*.jar", "org.abego.*.jar", "ST4*.jar",
        "annotations*.jar"
      )
      filesMatching("**/cadenza") {
        filter(ReplaceTokens::class, "tokens" to mapOf("CADENZA_APP_HOME" to "\$APP_HOME"))
      }
      filesMatching("**/cadenza.bat") {
        filter(ReplaceTokens::class, "tokens" to mapOf("CADENZA_APP_HOME" to "%~dp0.."))
      }
      from("LICENSE.txt")
    }
  }
}

val jar = tasks.getByName<Jar>("jar") {
  baseName = "cadenza"
  from("LICENSE.txt") { rename("LICENSE.txt","LICENSE_cadenza.txt") }
  from("LICENSE.txt") { rename("LICENSE.txt","jre/languages/cadenza/LICENSE.txt") }
  from(tasks.getByPath(":startScripts")) {
    rename("(.*)","jre/languages/cadenza/bin/$1")
    filesMatching("**/cadenza") {
      filter(ReplaceTokens::class, "tokens" to mapOf("CADENZA_APP_HOME" to "\$APP_HOME"))
    }
    filesMatching("**/cadenza.bat") {
      filter(ReplaceTokens::class, "tokens" to mapOf("CADENZA_APP_HOME" to "%~dp0.."))
    }
  }
  from(tasks.getByPath(":language:jar")) {
    rename("(.*).jar","jre/languages/cadenza/lib/\$1.jar")
  }
  from(tasks.getByPath(":launcher:jar")) {
    rename("(.*).jar","jre/languages/cadenza/lib/\$1.jar")
  }

  from(project(":language").configurations.getByName("kotlinRuntime")) {
    rename("(.*).jar","jre/languages/cadenza/lib/\$1.jar")
    exclude("annotations*.jar")
  }

  // grab top-level deps.
  from(project(":language").configurations.getByName("runtime")) {
    exclude(
      "graal-sdk*.jar", "truffle-api*.jar", "launcher-common*.jar", // graal/truffle parts that ship with graalvm and shouldn't be shadowed
      "antlr4-4*.jar", "javax.json*.jar", "org.abego.*.jar", "ST4*.jar" // unused runtime bits of antlr
    )
    rename("(.*).jar","jre/languages/cadenza/lib/\$1.jar")
  }

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

tasks.register("register", Exec::class) {
  if (needsExtract) dependsOn(":extractGraalTooling")
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

// assumes we are building on graal
tasks.register("runRegistered", Exec::class) {
  if (needsExtract) dependsOn(":extractGraalTooling")
  dependsOn(":register")
  executable = "$graalBinDir/cadenza"
}

tasks.getByName<NativeImageTask>("nativeImage") {
  dependsOn(":register")
}

tasks.replace("run", JavaExec::class.java).run {
  if (needsExtract) dependsOn(":extractGraalTooling")
  dependsOn(":launcher:jar")
  executable = "$graalBinDir/java"
  classpath = project(":launcher").sourceSets["main"].runtimeClasspath
  jvmArgs = listOf(
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+EnableJVMCI",
    "-Dtruffle.class.path.append=language/build/libs/cadenza-language-${project.version}.jar"
  )
  main = "cadenza.launcher.Launcher"
}

