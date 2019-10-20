group = project.properties["group"].toString()
version = project.properties["version"].toString()

repositories {
  jcenter()
  maven { url = uri("https://jitpack.io") }
}

plugins {
  java
}

dependencies {
  compileOnly("com.github.bsideup.jabel:jabel-javac-plugin:0.2.0")
}

tasks.withType<Jar> {
  from(configurations.compileOnly.get().map { if (it.isDirectory) it else zipTree(it)}) {
    exclude("META-INF/services/javax.annotation.processing.Processor")
  }
}
