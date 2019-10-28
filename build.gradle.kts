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

val SourceSet.kotlin: SourceDirectorySet
  get() = (this as HasConvention).convention.getPlugin(KotlinSourceSet::class.java).kotlin

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

  java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
  }
}

subprojects {
  // version = project.properties["version"]
  sourceSets {
    main {
      java.srcDir("main")
      //withConvention(KotlinSourceSet::class) {
        kotlin.srcDirs("main") 
     // }
      // antlr.srcDirs = listOf(file("$projectDir/main"))
    }
    test {
      java.srcDir("test")
      //withConvention(KotlinSourceSet::class) {
        kotlin.srcDirs("test") 
      //}
    }
  }
}

plugins {
  application
  `build-scan`
  idea
  id("com.palantir.graal") version "0.6.0"
  id("org.jetbrains.dokka") version "0.9.17" // apply false
  id("org.ajoberstar.git-publish") version "2.1.1"
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
  if (System.getenv("CI") != null) {
    repoUri.set("https://github.com/ekmett/cadenza.git") // only pulling on CI, use https
  } else {
    repoUri.set("git@github.com:ekmett/cadenza.git")
  }
  branch.set("gh-pages")
  repoDir.set(file("$buildDir/javadoc"))
  contents {
    from("etc/gh-pages")
  }
}

tasks.getByName("gitPublishCommit").dependsOn(":dokka")

dependencies {
  implementation(project(":assembly"))
  implementation(project(":language"))
  implementation(project(":launcher"))
}

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
    "-Dtruffle.class.path.append=@CADENZA_APP_HOME@/lib/cadenza-language.jar" // hacks expand CADENZA_APP_HOME
    // "-Dtruffle.class.path.append=@CADENZA_APP_HOME@/lib/cadenza-language-${project.version}.jar" // hacks expand CADENZA_APP_HOME
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

var rootBuildDir = project.buildDir

project(":assembly") {
  apply(plugin = "antlr")
  apply(plugin = "kotlin")
  dependencies {
    arrayOf("asm","asm-tree","asm-commons").forEach {
      implementation("org.ow2.asm:$it:7.1")
    }
  }

  tasks.getByName<Jar>("jar") {
    archiveBaseName.set("cadenza-assembly")
    manifest {
      attributes["Class-Path"] = configurations.runtimeClasspath.get().files.joinToString(separator = " ") { it.absolutePath }
    }
  }

  tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
    kotlinOptions.freeCompilerArgs += listOf("-Xuse-experimental=kotlin.Experimental")
    // kotlinOptions.freeCompilerArgs += listOf("-XXLanguage:+InlineClasses")
  }
}

project(":language") {
  apply(plugin = "antlr")
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
    implementation(project(":assembly"))
  }

  tasks.getByName<Jar>("jar") {
    archiveBaseName.set("cadenza-language")
    manifest {
      attributes["Class-Path"] = configurations.runtimeClasspath.get().files.joinToString(separator = " ") { it.absolutePath }
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
      attributes["Main-Class"] = "cadenza.Launcher"
      attributes["Class-Path"] = configurations.runtimeClasspath.get().files.joinToString(separator = " ") { it.absolutePath }
    }
  }
}

// needed to avoid non-thread-safe access to configuration data
val languageRuntime: Configuration by configurations.creating {
  val languageConfigurations = project(":language").configurations
  extendsFrom(
    languageConfigurations.getByName("kotlinRuntime"),
    languageConfigurations.getByName("runtime")
  )
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
    tasks.getByPath(":assembly:jar"),
    tasks.getByPath(":language:jar"),
    tasks.getByPath(":launcher:jar"),
    languageRuntime
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

tasks.withType<DokkaTask> {
  outputFormat = "html"
  outputDirectory = gitPublish.repoDir.get().getAsFile().getAbsolutePath()
  dependsOn(":gitPublishReset")
  subProjects = listOf("language","launcher")
  configuration {
    jdkVersion = 8
    includes = listOf("etc/module.md")
    arrayOf("assembly","language","launcher").forEach {
      sourceLink {
        path = "$it/src/main/kotlin"
        url = "https://github.com/ekmett/cadenza/blob/master/$it/src/main/kotlin/"
        lineSuffix = "#L"
      }
    }
    externalDocumentationLink {
      url = URL("https://www.antlr.org/api/Java/")
      packageListUrl = URL("https://www.antlr.org/api/Java/package-list")
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

tasks.replace("run", JavaExec::class.java).run {
  if (needsExtract) dependsOn(":extractGraalTooling")
  description = "Run cadenza directly from the working directory"
  dependsOn(":launcher:jar")
  executable = "$graalBinDir/java"
  classpath = project(":launcher").sourceSets["main"].runtimeClasspath
  jvmArgs = listOf(
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+EnableJVMCI",
    "-Dtruffle.class.path.append=language/build/libs/cadenza-language.jar"
    // "-Dtruffle.class.path.append=language/build/libs/cadenza-language-${project.version}.jar"
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
    jar.archiveFile.get().asFile.path
  )
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
