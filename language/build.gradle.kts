plugins {
  antlr
  `java-library`
  //maven
}

dependencies {
  annotationProcessor("org.graalvm.truffle:truffle-api:19.2.0.1")
  annotationProcessor("org.graalvm.truffle:truffle-dsl-processor:19.2.0.1")
  antlr("org.antlr:antlr4:4.7.2")
  implementation("org.graalvm.truffle:truffle-api:19.2.0.1")
  implementation("org.graalvm.sdk:graal-sdk:19.2.0.1")
  implementation("org.antlr:antlr4-runtime:4.7.2")
  testImplementation("org.testng:testng:6.14.3")
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8 // todo: jabel to relax this and get "var"
  targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.getByName<Test>("test") {
  useTestNG()
}

tasks.getByName<Jar>("jar") {
  baseName = "cadenza"
}
