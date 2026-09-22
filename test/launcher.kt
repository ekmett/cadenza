import cadenza.Launcher
import org.graalvm.polyglot.Context
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class LauncherTests {
  @TempDir lateinit var directory: Path

  private data class Execution(val status: Int, val stdout: String, val stderr: String)

  private fun launch(arguments: List<String>): Execution {
    // Gradle's test worker loads the test runtime through a separate classloader;
    // java.class.path alone only contains the worker bootstrap JAR.
    val classpath = (generateSequence(javaClass.classLoader) { it.parent }
      .flatMap { (it as? URLClassLoader)?.getURLs()?.asSequence() ?: emptySequence() }
      .filter { it.protocol == "file" }
      .map { File(it.toURI()).absolutePath }.toList() +
      System.getProperty("java.class.path").split(File.pathSeparator) +
      File(Launcher::class.java.protectionDomain.codeSource.location.toURI()).absolutePath)
      .distinct().joinToString(File.pathSeparator)
    val stdout = Files.createTempFile(directory, "stdout-", ".txt")
    val stderr = Files.createTempFile(directory, "stderr-", ".txt")
    val command = listOf(
      Path.of(System.getProperty("java.home"), "bin", "java").toString(),
      "--enable-native-access=ALL-UNNAMED", "-cp", classpath, Launcher::class.java.name
    ) + arguments
    val process = ProcessBuilder(command).directory(directory.toFile())
      .redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start()
    if (!process.waitFor(30, TimeUnit.SECONDS)) {
      process.destroyForcibly()
      fail<Unit>("launcher timed out: ${Files.readString(stderr)}")
    }
    return Execution(process.exitValue(), Files.readString(stdout), Files.readString(stderr))
  }

  private fun program(text: String, backend: String = "ast"): Execution {
    val source = Files.createTempFile(directory, "program-", ".za")
    Files.writeString(source, text)
    return launch(listOf("--experimental-options", "--cadenza.Backend=$backend", source.toString()))
  }

  @TestFactory fun evaluatesAndRendersValuesWithoutExecutingFunctions(): List<DynamicTest> {
    val cases = listOf(
      "42" to "42",
      "123456789012345678901234567890" to "123456789012345678901234567890",
      "eq 1 1" to "true",
      "eq 1 2" to "false",
      "\\(x : Nat) -> printId x" to "<function/1>"
    )
    return listOf("ast", "bytecode").flatMap { backend -> cases.map { (source, expected) ->
      DynamicTest.dynamicTest("$backend renders $source") {
        val result = program(source, backend)
        assertEquals(0, result.status, result.stderr)
        assertEquals("Result: $expected\n", result.stdout)
      }
    } }
  }

  @TestFactory fun guestFailuresUseStderrAndReturnFailureStatus(): List<DynamicTest> {
    val cases = listOf(
      "(42" to "expected",
      "let x : Nat = eq 1 1 in x" to "type mismatch",
      "let x : Nat = x in x" to "recursive binding read before initialization"
    )
    return cases.map { (source, message) -> DynamicTest.dynamicTest(message) {
      val result = program(source)
      assertEquals(1, result.status, result.stderr)
      assertEquals("", result.stdout)
      assertTrue(result.stderr.contains(message), result.stderr)
    } }
  }

  @Test fun missingFilesUseStderrAndReturnFailureStatus() {
    val result = launch(listOf(directory.resolve("missing.za").toString()))
    assertEquals(1, result.status, result.stderr)
    assertEquals("", result.stdout)
    assertTrue(result.stderr.contains("Error loading file"), result.stderr)
    assertTrue(result.stderr.contains("missing.za"), result.stderr)
  }

  @Test fun optionTerminatorAllowsDashPrefixedFiles() {
    Files.writeString(directory.resolve("--program.za"), "42")
    val result = launch(listOf("--", "--program.za"))
    assertEquals(0, result.status, result.stderr)
    assertEquals("Result: 42\n", result.stdout)
  }

  @Test fun partialApplicationsDisplayTheirRemainingArity() {
    Context.create("cadenza").use { context ->
      val add = context.eval("cadenza", "plus")
      assertEquals("<function/2>", add.toString())
      val increment = add.execute(1)
      assertEquals("<function/1>", increment.toString())
      assertEquals(42, increment.execute(41).asInt())
    }
  }
}
