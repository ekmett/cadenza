plugins {
  application
  java
  maven
}

dependencies {
  implementation(project(":language"))
  implementation("org.graalvm.sdk:launcher-common:19.2.0.1")
  implementation("org.graalvm.truffle:truffle-api:19.2.0.1")
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

application {
  mainClassName = "cadenza.Launcher"
  applicationDefaultJvmArgs = listOf("-XX:+UnlockExperimentalVMOptions","-XX:+EnableJVMCI","-Dtruffle.class.path.append=$buildDir/libs/cadenza.jar")
}

tasks.getByName<Jar>("jar") {
  baseName = "cadenza-launcher"
}

