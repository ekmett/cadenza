// build cadenza-component.jar
// merge into top level project?

plugins {
  java
}

tasks.withType<Jar> {
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
