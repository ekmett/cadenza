import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Exercise the shipped Unix script and JARs, without the Gradle test classpath. */
@EnabledOnOs(OS.LINUX, OS.MAC)
class InstalledLauncherTests {
  @TempDir lateinit var directory: Path
  private val sourceFileName = "--program with spaces.za"

  private data class Execution(val status: Int, val stdout: String, val stderr: String)

  private fun launch(backend: String, program: String): Execution {
    val distribution = Path.of(requireNotNull(System.getProperty("cadenza.test.distribution")) {
      "Run through Gradle's test task to build and locate installDist"
    })
    val launcher = distribution.resolve("bin/cadenza")
    assertTrue(Files.isExecutable(launcher), "Installed launcher is missing or not executable: $launcher")
    val working = Files.createDirectories(directory.resolve("outside repository with spaces"))
    val source = working.resolve(sourceFileName)
    Files.writeString(source, program)
    val stdout = Files.createTempFile(directory, "installed-stdout-", ".txt")
    val stderr = Files.createTempFile(directory, "installed-stderr-", ".txt")
    val builder = ProcessBuilder(launcher.toString(), "--experimental-options",
      "--cadenza.Backend=$backend", "--", source.fileName.toString())
      .directory(working.toFile()).redirectOutput(stdout.toFile()).redirectError(stderr.toFile())
    // Use the test toolchain even when Gradle selected it without a shell JAVA_HOME.
    builder.environment()["JAVA_HOME"] = System.getProperty("java.home")
    builder.environment().remove("JAVA_OPTS")
    builder.environment().remove("CADENZA_OPTS")
    val process = builder.start()
    if (!process.waitFor(30, TimeUnit.SECONDS)) {
      process.destroyForcibly()
      process.waitFor(5, TimeUnit.SECONDS)
      fail<Unit>("Installed launcher timed out: ${Files.readString(stderr)}")
    }
    return Execution(process.exitValue(), Files.readString(stdout), Files.readString(stderr))
  }

  @TestFactory fun installedDistributionWorksOutsideTheRepository() =
    listOf("ast", "bytecode").map { backend -> DynamicTest.dynamicTest("$backend installed launcher") {
      val integer = launch(backend, "plus 2147483647 1")
      assertEquals(0, integer.status, integer.stderr)
      assertEquals("Result: 2147483648\n", integer.stdout)

      val closure = launch(backend, "\\(x : Nat) -> printId x")
      assertEquals(0, closure.status, closure.stderr)
      assertEquals("Result: <function/1>\n", closure.stdout, "Displaying a closure must not execute it")

      val failure = launch(backend, "div 1 0")
      assertEquals(1, failure.status, failure.stderr)
      assertEquals("", failure.stdout)
      assertTrue(failure.stderr.contains("division by zero"), failure.stderr)
      assertTrue(failure.stderr.contains(sourceFileName), failure.stderr)
    } }
}
