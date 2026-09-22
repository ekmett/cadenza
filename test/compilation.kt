import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.Truffle
import org.graalvm.polyglot.Context
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

/** Only tests explicitly requesting compilation depend on these runtime-only public APIs. */
internal object CompilationTestSupport {
  fun requireOptimizingRuntime() {
    val runtimeType = runCatching {
      Class.forName("com.oracle.truffle.runtime.OptimizedTruffleRuntime")
    }.getOrNull()
    assumeTrue(runtimeType?.isInstance(Truffle.getRuntime()) == true, "Requires the optimizing Graal runtime")
  }

  fun context(): Context = Context.newBuilder("cadenza").allowExperimentalOptions(true)
    .option("cadenza.Backend", "ast")
    .option("engine.BackgroundCompilation", "false")
    .option("engine.MultiTier", "false")
    .option("engine.SingleTierCompilationThreshold", "20")
    .option("engine.CompilationFailureAction", "Throw")
    .build()

  fun compileAndVerify(target: RootCallTarget) {
    // Keep truffle-runtime a runtimeOnly dependency. Compilation is synchronous, and
    // failure to install code is a failed assertion rather than inferred from timing.
    val targetType = Class.forName("com.oracle.truffle.runtime.OptimizedCallTarget")
    assertTrue(targetType.isInstance(target))
    targetType.getMethod("compile", Boolean::class.javaPrimitiveType).invoke(target, true)
    assertEquals(true, targetType.getMethod("isValidLastTier").invoke(target),
      "The following transition must start with installed last-tier code")
  }
}
