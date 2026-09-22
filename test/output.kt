import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Engine
import org.graalvm.polyglot.Source
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class OutputTests {
  @Test fun printIdUsesContextOutputAndPreservesArgumentOrder() {
    for (backend in listOf("ast", "bytecode")) {
      val output = ByteArrayOutputStream()
      Context.newBuilder("cadenza").allowExperimentalOptions(true)
        .option("cadenza.Backend", backend).out(output).build().use { context ->
          assertEquals(42, context.eval("cadenza", "plus (printId 20) (printId 22)").asInt())
          assertEquals(7, context.eval("cadenza", "printId").execute(7).asInt())
          assertEquals("20\n22\n7\n", output.toString(Charsets.UTF_8), backend)
        }
    }
  }

  @Test fun sharedEnginePrintsToTheCurrentlyEnteredContext() {
    for (backend in listOf("ast", "bytecode")) {
      Engine.create().use { engine ->
        val source = Source.create("cadenza", "\\(x : Nat) -> printId x")
        val firstOutput = ByteArrayOutputStream()
        val secondOutput = ByteArrayOutputStream()
        fun context(output: ByteArrayOutputStream) = Context.newBuilder("cadenza")
          .engine(engine).allowExperimentalOptions(true).option("cadenza.Backend", backend)
          .out(output).build()
        context(firstOutput).use { first -> context(secondOutput).use { second ->
          val firstPrint = first.eval(source)
          val secondPrint = second.eval(source)
          assertEquals(11, firstPrint.execute(11).asInt())
          assertEquals(22, secondPrint.execute(22).asInt())
          assertEquals(33, firstPrint.execute(33).asInt())
          assertEquals("11\n33\n", firstOutput.toString(Charsets.UTF_8), backend)
          assertEquals("22\n", secondOutput.toString(Charsets.UTF_8), backend)
        } }
      }
    }
  }
}
