import com.palantir.gradle.graal.ExtractGraalTask
import com.palantir.gradle.graal.NativeImageTask
import java.net.URL
import java.util.Properties
import org.apache.tools.ant.filters.ReplaceTokens
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.gradle.api.internal.HasConvention

group = project.properties["group"].toString()
// version = project.properties["version"].toString()

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
  jcenter()
  mavenCentral()
  maven { url = uri("https://jitpack.io") }
  maven { url = uri("http://palantir.bintray.com/releases") }
}


apply(plugin = "kotlin")
apply(plugin = "kotlin-kapt")

plugins {
  application
  `build-scan`
  idea
  id("com.palantir.graal") version "0.6.0"
  id("org.jetbrains.dokka") version "0.9.17"
  id("org.ajoberstar.git-publish") version "2.1.1"
}

dependencies {
  implementation(kotlin("stdlib"))
  implementation(kotlin("stdlib-jdk8"))
  arrayOf("asm","asm-tree","asm-commons").forEach { implementation("org.ow2.asm:$it:7.1") }
  implementation("org.graalvm.sdk:graal-sdk:19.2.0.1")
  implementation("org.graalvm.sdk:launcher-common:19.2.0.1")
  implementation("org.graalvm.truffle:truffle-api:19.2.0.1")
  "kapt"("org.graalvm.truffle:truffle-api:19.2.0.1")
  "kapt"("org.graalvm.truffle:truffle-dsl-processor:19.2.0.1")
  testImplementation("org.testng:testng:6.14.3")
  testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<KotlinCompile> {
  kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
}

val SourceSet.kotlin: SourceDirectorySet
  get() = (this as HasConvention).convention.getPlugin(KotlinSourceSet::class.java).kotlin

sourceSets {
  main {
    java.srcDir("java")
    kotlin.srcDirs("src")
  }
  test {
    kotlin.srcDirs("t")
  }
}

// disable for private
buildScan {
  termsOfServiceUrl = "https://gradle.com/terms-of-service"
  termsOfServiceAgree = "yes"
  if (System.getenv("CI") != null) { // on travis, always publish build-scan
    publishAlways()
    tag("CI")
  }
  tag(System.getProperty("os.name"))
}

gitPublish {
  repoUri.set(
    if (System.getenv("CI") != null) "https://github.com/ekmett/cadenza.git"
    else "git@github.com:ekmett/cadenza.git"
  )
  branch.set("gh-pages")
  repoDir.set(file("$buildDir/javadoc"))
  contents { from("etc/gh-pages") }
}

tasks.getByName("gitPublishCommit").dependsOn(":dokka")

graal {
  graalVersion("19.2.0.1")
  mainClass("cadenza.Launcher")
  outputName("cadenza-native")
  option("--language:cadenza")
}

application {
  mainClassName = "cadenza.Launcher"
  applicationDefaultJvmArgs = listOf(
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+EnableJVMCI",
    "-Dtruffle.class.path.append=@CADENZA_APP_HOME@/lib/cadenza.jar"
  )
}

var rootBuildDir = project.buildDir

tasks.test {
  useJUnitPlatform()
  testLogging { events("passed","skipped","failed") }
}

tasks.getByName<Jar>("jar") {
  exclude("jre/**")
  exclude("META-INF/symlinks")
  exclude("META-INF/permissions")
  archiveBaseName.set("cadenza")
  manifest {
    attributes["Main-Class"] = "cadenza.Launcher"
    attributes["Class-Path"] = configurations.runtimeClasspath.get().files.joinToString(separator = " ") { it.absolutePath }
  }
}


tasks.register("componentJar", Jar::class) {
  archiveBaseName.set("cadenza-component")
  from(tasks.getByPath(":processResources"))
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
    tasks.getByPath(":jar"),
    configurations.getByName("runtimeClasspath")
  )) {
    rename("(.*).jar","jre/languages/cadenza/lib/\$1.jar")
    exclude("graal-sdk*.jar", "truffle-api*.jar", "launcher-common*.jar") //, "annotations*.jar")
  }

  manifest {
    attributes["Bundle-Name"] = "Cadenza"
    attributes["Bundle-Description"] = "The cadenza language"
    attributes["Bundle-DocURL"] = "https://github.com/ekmett/cadenza"
    attributes["Bundle-Symbolic-Name"] = "cadenza"
    attributes["Bundle-Version"] = "0.0-SNAPSHOT"
    attributes["Bundle-RequireCapability"] = "org.graalvm;filter:=\"(&(graalvm_version=19.2.0)(os_arch=amd64))\""
    attributes["x-GraalVM-Polyglot-Part"] = "True"
  }
}

val componentJar = tasks.getByName<Jar>("componentJar")

tasks.register("register", Exec::class) {
  group = "installation"
  if (needsExtract) dependsOn(":extractGraalTooling")
  dependsOn(componentJar)
  description = "Register cadenza with graal"
  commandLine = listOf(
    "$graalBinDir/gu",
    "install",
    "-f",
    "-L",
    "build/libs/cadenza-component.jar"
  )
}

distributions.main {
  baseName = "cadenza"
  contents {
    from(componentJar)
    exclude( "graal-sdk*.jar", "truffle-api*.jar", "launcher-common*.jar")
    filesMatching("**/cadenza") {
      filter(ReplaceTokens::class, "tokens" to mapOf("CADENZA_APP_HOME" to "\$APP_HOME"))
    }
    filesMatching("**/cadenza.bat") {
      filter(ReplaceTokens::class, "tokens" to mapOf("CADENZA_APP_HOME" to "%~dp0.."))
    }
    from("LICENSE.txt")
  }
}


tasks.withType<ProcessResources> {
  from("etc/native-image.properties") {
    expand(project.properties)
    rename("native-image.properties","jre/languages/cadenza/native-image.properties")
  }
  from(files("etc/symlinks","etc/permissions")) {
    rename("(.*)","META-INF/$1")
  }
}

tasks.withType<DokkaTask> {
  outputFormat = "html"
  outputDirectory = gitPublish.repoDir.get().getAsFile().getAbsolutePath()
  dependsOn(":gitPublishReset")
  configuration {
    jdkVersion = 8
    includes = listOf("etc/module.md")
    arrayOf("src","t","java").forEach {
      sourceLink {
        path = "$it"
        url = "https://github.com/ekmett/cadenza/blob/master/$it"
        lineSuffix = "#L"
      }
    }
  }
}

tasks.register("pages") {
  description = "Publish documentation"
  group = "documentation"
  dependsOn(":gitPublishPush")
}

val os : String? = System.getProperty("os.name")
logger.info("os = {}",os)

var graalHome0 : String? = System.getenv("GRAAL_HOME")

if (graalHome0 == null) {
  val javaHome = System.getenv("JAVA_HOME")
  if (javaHome != null) {
    logger.info("checking JAVA_HOME {} for Graal install", javaHome)
    val releaseFile = file("${javaHome}/release")
    if (releaseFile.exists()) {
      val releaseProps = Properties()
      releaseProps.load(releaseFile.inputStream())
      val ver = releaseProps.getProperty("GRAALVM_VERSION")
      if (ver != null) {
        logger.info("graal version {} detected in JAVA_HOME", ver)
        graalHome0 = javaHome
      }
    }
  }
}

val needsExtract = graalHome0 == null
val graalToolingDir = tasks.getByName<ExtractGraalTask>("extractGraalTooling").outputDirectory.get().asFile.toString()!!
val graalHome : String = graalHome0 ?: if (os == "Mac OS X") "$graalToolingDir/Contents/Home" else graalToolingDir
val graalBinDir : String = if (os == "Linux") graalHome else "$graalHome/bin"

logger.info("graalHome = {}", graalHome)
logger.info("graalBinDir = {}", graalBinDir)

// can i just tweak this one now?
tasks.replace("run", JavaExec::class.java).run {
  if (needsExtract) dependsOn(":extractGraalTooling")
  description = "Run cadenza directly from the working directory"
  dependsOn(":jar")
  executable = "$graalBinDir/java"
  classpath = sourceSets["main"].runtimeClasspath
  jvmArgs = listOf(
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+EnableJVMCI",
    "-Dtruffle.class.path.append=build/libs/cadenza.jar"
  )
  main = "cadenza.Launcher"
}

// assumes we are building on graal
tasks.register("runInstalled", Exec::class) {
  group = "application"
  if (needsExtract) dependsOn(":extractGraalTooling")
  description = "Run a version of cadenza from the distribution dir"
  dependsOn(":installDist")
  executable = "$buildDir/install/cadenza/bin/cadenza"
  environment("JAVA_HOME", graalHome)
  outputs.upToDateWhen { false }
}

// assumes we are building on graal
tasks.register("runRegistered", Exec::class) {
  group = "application"
  if (needsExtract) dependsOn(":extractGraalTooling")
  description = "Run a registered version of cadenza"
  dependsOn(":register")
  executable = "$graalBinDir/cadenza"
  environment("JAVA_HOME", graalHome)
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
