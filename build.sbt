lazy val root = project
  .in(file("."))
  .settings(common)
  .settings(
    inThisBuild(Seq(
      scalaVersion   := "2.13.0",
      version        := "0.0.0-SNAPSHOT"
      // organization   := "com.comonad"
    )),
    name := "coda",
    description := "testing a truffle evaluator for strong beta normalization",
    licenses := Seq(("BSD 2-Clause", url("https://github.com/ekmett/core/blob/master/LICENSE.md"))),
    scalacOptions := Seq("-deprecation"),
  )

lazy val graalvmVersion = "19.2.0.1"

lazy val common = Seq(
  test in assembly := {},
  assemblyExcludedJars in assembly := {
    val cp = (fullClasspath in assembly).value
    cp filter { f =>
      val path = f.data.toString
      (path contains "com.oracle.truffle") || (path contains "org.graalvm")
    }
  },
  Compile / run / fork := true,
  javaOptions ++= truffleOptions ,
  libraryDependencies ++= Seq(
    "org.graalvm.truffle" % "truffle-api" % graalvmVersion,
    "org.graalvm.truffle" % "truffle-dsl-processor" % graalvmVersion,
    "org.graalvm.truffle" % "truffle-tck" % graalvmVersion,
    "org.graalvm.sdk" % "graal-sdk" % graalvmVersion,
  )
)

lazy val truffleOptions = Seq(
  // "-Dgraal.Dump",
  // "-Dgraal.Dump=Truffle:1",
  // "-Dgraal.TruffleBackgroundCompilation=false",
  // "-Dgraal.TraceTruffleCompilation=true",
  // "-Dgraal.TraceTruffleCompilationDetails=true",
  "-XX:+UnlockExperimentalVMOptions",
  "-XX:+EnableJVMCI"
  // "-XX:+UseJVMCICompiler" // replace the top tier compiler with graal
  // "-XX:+UseJVMCINativeLibrary" // use a compiled graal JVMCI native library if available for faster startup
)
