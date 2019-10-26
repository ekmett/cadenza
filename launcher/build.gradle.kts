dependencies {
  implementation(project(":language"))
  implementation("org.graalvm.truffle:truffle-api:19.2.0.1")
  implementation("org.graalvm.sdk:launcher-common:19.2.0.1")
}

tasks.getByName<Jar>("jar") {
  baseName = "cadenza-launcher"
  manifest {
    attributes["Main-Class"] = "cadenza.launcher.Launcher"
    attributes["Specification-Title"] = "cadenza.launcher"
    attributes["Specification-Version"] = project.version
    attributes["Specification-Vendor"] = "Machine Intelligence Research Institute"
    attributes["Implementation-Title"] = "cadenza.launcher"
    attributes["Implementation-Version"] = project.version
    attributes["Implementation-Vendor"] = "Machine Intelligence Research Institute"
    attributes["Class-Path"] = configurations.runtimeClasspath.get().files.map { it.getAbsolutePath() } .joinToString(separator = " ")
  }
}

