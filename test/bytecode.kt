import cadenza.Language
import cadenza.bytecode.BytecodeRoot
import cadenza.data.Closure
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Engine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.DynamicTest
import java.math.BigInteger

class BytecodeTests {
  private fun context(backend: String) = Context.newBuilder("cadenza")
    .allowExperimentalOptions(true).option("cadenza.Backend", backend).build()

  @TestFactory fun concreteProgramsAgreeWithAst(): List<DynamicTest> {
    val programs = listOf(
      "42",
      "plus (mult 6 7) (minus 2 2)",
      "if le 3 4 then plus 20 22 else div 1 0",
      "(\\(x : Nat) -> (\\(y : Nat) -> plus x y) 2) 40",
      "(plus 20) 22",
      "(\\(x : Nat) -> \\(y : Nat) -> plus x y) 20 22",
      "let f : Nat -> Nat = \\(x : Nat) -> plus x 1 in f 41",
      "fixNatF (\\(r : Nat -> Nat) (n : Nat) -> if le n 0 then 42 else r (minus n 1)) 10000",
      "let count : Nat -> Nat = \\(x : Nat) -> if le 100000 x then x else count (plus x 1) in count 0"
    )
    return programs.map { program -> DynamicTest.dynamicTest(program) {
      context("ast").use { ast -> context("bytecode").use { bytecode ->
        assertEquals(ast.eval("cadenza", program).asInt(), bytecode.eval("cadenza", program).asInt(), program)
      } }
    } }
  }

  @Test fun bytecodeClosuresWorkAtHostBoundaryAndOverflowToBigInts() {
    context("bytecode").use { context ->
      val add = context.eval("cadenza", "\\(x : Nat) (y : Nat) -> plus x y")
      assertEquals(42, add.execute(20).execute(22).asInt())
      assertEquals(42, add.execute(41, 1).asInt())
      assertEquals(BigInteger.valueOf(Int.MAX_VALUE.toLong()).add(BigInteger.ONE),
        add.execute(Int.MAX_VALUE, 1).asBigInteger())
      assertEquals(42, add.execute(40, 2).asInt())
    }
  }

  @Test fun bytecodeUsesScopedPrimitiveLocals() {
    context("bytecode").use { context ->
      assertEquals(42, context.eval("cadenza",
        "let x : Nat = 40 in plus (let x : Nat = 2 in x) x").asInt())
    }
  }

  @Test fun bytecodeSourceMetadataCanBeReplayed() {
    context("bytecode").use { context ->
      context.initialize("cadenza")
      context.enter()
      try {
        val source = com.oracle.truffle.api.source.Source.newBuilder("cadenza",
          "\\(x : Nat) -> plus x 1", "bytecode.za").build()
        val closure = Language.currentLanguage().parse(source).call() as Closure
        val root = closure.callTarget.rootNode as BytecodeRoot
        assertTrue(root.dump().contains("Add"))
        assertNotNull(root.ensureSourceSection())
        assertEquals(source, root.sourceSection.source)
        assertEquals(42, com.oracle.truffle.api.interop.InteropLibrary.getUncached().execute(closure, 41))
        assertTrue(com.oracle.truffle.api.nodes.NodeUtil.verify(root))
      } finally {
        context.leave()
      }
    }
  }

  @Test fun backendSelectionIsIsolatedWithinASharedEngine() {
    Engine.create().use { engine ->
      for (backend in listOf("ast", "bytecode", "ast", "bytecode")) {
        Context.newBuilder("cadenza").engine(engine).allowExperimentalOptions(true)
          .option("cadenza.Backend", backend).build().use { context ->
            context.initialize("cadenza")
            context.enter()
            try {
              val source = com.oracle.truffle.api.source.Source.newBuilder("cadenza", "42", "same.za").build()
              val target = Language.currentLanguage().parse(source) as com.oracle.truffle.api.RootCallTarget
              assertEquals(if (backend == "bytecode") "bytecode program root" else "program root", target.rootNode.name)
              assertEquals(42, target.call())
            } finally {
              context.leave()
            }
          }
      }
    }
  }

  @Test fun unknownBackendIsRejected() {
    assertThrows(IllegalArgumentException::class.java) { context("unknown").close() }
  }
}
