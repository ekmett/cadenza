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

