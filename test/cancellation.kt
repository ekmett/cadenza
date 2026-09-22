import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Engine
import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CancellationTests {
  @TestFactory fun cancellingRunningGuestCodeLeavesOtherSharedEngineContextsUsable() =
    listOf("ast", "bytecode").map { backend -> DynamicTest.dynamicTest("$backend cancellation and engine isolation") {
      val enteredLoop = CountDownLatch(1)
      val output = object : OutputStream() {
        override fun write(value: Int) {
          if (value == '\n'.code) enteredLoop.countDown()
        }
      }
      // Daemon workers keep a broken cancellation path from hanging the entire test JVM.
      val workers = Executors.newFixedThreadPool(2) { task ->
        Thread(task, "cadenza-cancellation-test").apply { isDaemon = true }
      }
      val engine = Engine.create()
      fun context() = Context.newBuilder("cadenza").engine(engine).allowExperimentalOptions(true)
        .option("cadenza.Backend", backend).out(output).build()
      val running = context()
      val survivor = context()
      var cancellationCompleted = false
      try {
        val evaluation = workers.submit<PolyglotException> {
          assertThrows(PolyglotException::class.java) {
            running.eval("cadenza", """
              let loop : Nat -> Nat = \(n : Nat) ->
                let next : Nat = mod (plus n 1) 65521 in
                if eq n 1000 then loop (printId next) else loop next
              in loop 0
            """.trimIndent())
          }
        }
        assertTrue(enteredLoop.await(15, TimeUnit.SECONDS), "Guest loop never reached its observable checkpoint")
        val survivingFunction = survivor.eval("cadenza", "\\(n : Nat) -> plus n 1")
        assertEquals(42, survivingFunction.execute(41).asInt())
        workers.submit { running.close(true) }.get(15, TimeUnit.SECONDS)
        cancellationCompleted = true
        val error = evaluation.get(15, TimeUnit.SECONDS)
        assertTrue(error.isCancelled, error.toString())
        assertFalse(error.isInternalError, error.toString())
        assertEquals(43, survivingFunction.execute(42).asInt())
        assertEquals(42, survivor.eval("cadenza", "mult 6 7").asInt())
        val reentry = assertThrows(PolyglotException::class.java) { running.eval("cadenza", "42") }
        assertTrue(reentry.isCancelled, reentry.toString())
      } finally {
        survivor.close()
        // Avoid a blocking close on the assertion thread if the behavior under test broke.
        if (cancellationCompleted) engine.close()
        else workers.submit { engine.close(true) }
        workers.shutdownNow()
      }
    } }
}
