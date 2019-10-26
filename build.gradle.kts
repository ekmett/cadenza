import com.palantir.gradle.graal.ExtractGraalTask
import com.palantir.gradle.graal.NativeImageTask
import java.net.URL
import java.util.Properties
import org.apache.tools.ant.filters.ReplaceTokens
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = project.properties["group"].toString()
version = project.properties["version"].toString()

buildscript {
  repositories {
    mavenCentral()
    maven { url = uri("https://dl.bintray.com/kotlin/kotlin-eap") }
  }
  dependencies {
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.41")
    classpath("org.jetbrains.dokka:dokka-gradle-plugin:0.10.0")
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

  tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
  }
}

subprojects {
  version = project.properties["version"]
}

plugins {
  application
  idea
  id("com.palantir.graal") version "0.6.0"
  id("org.jetbrains.dokka") version "0.9.17" // apply false
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

application {
  mainClassName = "cadenza.launcher.Launcher"
  applicationDefaultJvmArgs = listOf(
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+EnableJVMCI",
    "-Dtruffle.class.path.append=@CADENZA_APP_HOME@/lib/cadenza-language-${project.version}.jar" // hacks expand CADENZA_APP_HOME
  )
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

project(":language") {
  apply(plugin = "antlr")
  apply(plugin = "kotlin")
  apply(plugin = "kotlin-kapt")

  val antlrRuntime by configurations.creating
  val kotlinRuntime by configurations.creating

  dependencies {
    "kapt"("org.graalvm.truffle:truffle-api:19.2.0.1")
    "kapt"("org.graalvm.truffle:truffle-dsl-processor:19.2.0.1")
    "antlr"("org.antlr:antlr4:4.7.2")
    antlrRuntime("org.antlr:antlr4-runtime:4.7.2")
    compileOnly("org.graalvm.truffle:truffle-api:19.2.0.1")
    compileOnly("org.graalvm.sdk:graal-sdk:19.2.0.1")
    implementation("org.antlr:antlr4-runtime:4.7.2")
    kotlinRuntime(kotlin("stdlib"))
    kotlinRuntime(kotlin("stdlib-jdk8"))
    testImplementation("org.testng:testng:6.14.3")
  }

  tasks.getByName<Jar>("jar") {
    archiveBaseName.set("cadenza-language")
    manifest {
      attributes["Class-Path"] =  configurations.runtimeClasspath.get().files.map { it.getAbsolutePath() } .joinToString(separator = " ")
    }
  }

  tasks.withType<AntlrTask> {
    arguments.addAll(listOf("-package", "cadenza.syntax", "-no-listener", "-no-visitor"))
  }
}

project(":launcher") {
  dependencies {
    implementation(project(":language"))
    implementation("org.graalvm.truffle:truffle-api:19.2.0.1")
    implementation("org.graalvm.sdk:launcher-common:19.2.0.1")
  }
  
  tasks.getByName<Jar>("jar") {
    archiveBaseName.set("cadenza-launcher")
    manifest {
      attributes["Main-Class"] = "cadenza.launcher.Launcher"
      attributes["Class-Path"] = configurations.runtimeClasspath.get().files.map { it.getAbsolutePath() } .joinToString(separator = " ")
    }
  }
}

// calculate local platform values

val jar = tasks.getByName<Jar>("jar") {
  baseName = "cadenza"
  description = "Build the cadenza component for graal"

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

  from(files(
    tasks.getByPath(":language:jar"),
    tasks.getByPath(":launcher:jar"),
    project(":language").configurations.getByName("kotlinRuntime"),
    project(":language").configurations.getByName("runtime")
  )) {
    rename("(.*).jar","jre/languages/cadenza/lib/\$1.jar")
    exclude(
      "graal-sdk*.jar", "truffle-api*.jar", "launcher-common*.jar", // graal/truffle parts that ship with graalvm and shouldn't be shadowed
      "antlr4-4*.jar", "javax.json*.jar", "org.abego.*.jar", "ST4*.jar", // unused runtime bits of antlr
      "annotations*.jar"
    )
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

tasks.withType<ProcessResources> {
  from("etc/native-image.properties") {
    expand(project.properties)
    rename("etc/native-image.properties","jre/languages/cadenza/native-image.properties")
  }
  from(files("etc/symlinks","etc/permissions")) {
    rename("(.*)","META-INF/$1")
  }
}


val os = System.getProperty("os.name")
logger.info("os = {}",os)

var graalHome = System.getenv("GRAAL_HOME")

if (graalHome == null) {
  val javaHome = System.getenv("JAVA_HOME")
  logger.info("checking JAVA_HOME {} for Graal install",javaHome)
  val releaseFile = file("${javaHome}/release")
  if (releaseFile.exists()) {
    val releaseProps = Properties()
    releaseProps.load(releaseFile.inputStream())
    val ver = releaseProps.getProperty("GRAALVM_VERSION")
    if (ver != null) {
      logger.info("graal version {} detected in JAVA_HOME",ver)
      graalHome = javaHome
    }
  }
}

val needsExtract = graalHome == null
if (needsExtract) {
  val graalToolingDir = tasks.getByName<ExtractGraalTask>("extractGraalTooling").getOutputDirectory().get().getAsFile().toString()
  graalHome = if (os == "Mac OS X") "$graalToolingDir/Contents/Home" else graalToolingDir
}

val graalBinDir = if (os == "Linux") graalHome else "$graalHome/bin"

logger.info("graalHome = {}",graalHome)
logger.info("graalBinDir = {}",graalBinDir)

tasks.replace("run", JavaExec::class.java).run {
  if (needsExtract) dependsOn(":extractGraalTooling")
  description = "Run cadenza directly from the working directory"
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

// assumes we are building on graal
tasks.register("runInstalled", Exec::class) {
  group = "application"
  if (needsExtract) dependsOn(":extractGraalTooling")
  description = "Run a version of cadenza from the distribution dir"
  dependsOn(":installDist")
  executable = "$buildDir/install/cadenza/bin/cadenza"
  outputs.upToDateWhen { false }
}

tasks.register("register", Exec::class) {
  group = "installation"
  if (needsExtract) dependsOn(":extractGraalTooling")
  dependsOn(jar)
  description = "Register cadenza with graal"
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
  group = "application"
  if (needsExtract) dependsOn(":extractGraalTooling")
  description = "Run a registered version of cadenza"
  dependsOn(":register")
  executable = "$graalBinDir/cadenza"
  outputs.upToDateWhen { false }
}

tasks.getByName<NativeImageTask>("nativeImage") {
  group="build"
  dependsOn(":register")
}

tasks.register("unregister", Exec::class) {
  group = "installation"
  if (needsExtract) dependsOn(":extractGraalTooling")
  description = "Unregister cadenza with graal"
  commandLine = listOf(
    "$graalBinDir/gu",
    "remove",
    "cadenza"
  )
}

tasks.withType<DokkaTask> {
  outputFormat = "html"
  outputDirectory = "$buildDir/javadoc"
  subProjects = listOf("language","launcher")
  configuration {
    jdkVersion = 8
    includes = listOf("etc/module.md")
    sourceLink {
      path = "language/src/main/kotlin"
      url = "https://github.com/ekmett/cadenza/blob/master/language/src/main/kotlin/"
      lineSuffix = "#L"
    }
    sourceLink {
      path = "launcher/src/main/kotlin"
      url = "https://github.com/ekmett/cadenza/blob/master/launcher/src/main/kotlin/"
      lineSuffix = "#L"
    }
    externalDocumentationLink {
      url = URL("https://www.antlr.org/api/Java/")
      packageListUrl = URL("https://www.antlr.org/api/Java/package-list")
    }
  }
}

