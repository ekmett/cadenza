apply(plugin = "java")

tasks.withType<ProcessResources> {
  from("native-image.properties") {
    expand(project.properties)
  }
  rename("native-image.properties","jre/languages/cadenza/native-image.properties")
}

val jar = tasks.getByName<Jar>("jar") {
  baseName = "cadenza-component"
  from("../LICENSE.txt") { rename("LICENSE.txt","LICENSE_CADENZA") }
  from("../LICENSE.txt") { rename("LICENSE.txt","jre/languages/cadenza/LICENSE.txt") }
  from(tasks.getByPath(":language:jar")) {
    rename("(.*).jar","jre/languages/cadenza/lib/\$1.jar")
  }
  from(tasks.getByPath(":launcher:jar")) {
    rename("(.*).jar","jre/languages/cadenza/lib/\$1.jar")
  }
  from(tasks.getByPath(":startScripts")) {
    rename("(.*)","jre/languages/cadenza/bin/$1")
  }
  from(project(":language").configurations.getByName("antlrRuntime")) {
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
