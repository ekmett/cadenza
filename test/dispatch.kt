import org.graalvm.polyglot.Context
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DispatchTests {
  @Test fun hostEntryHandlesMoreThanThreeArgumentCounts() {
    Context.create("cadenza").use { context ->
      val function = context.eval("cadenza",
        "\\(a : Nat) (b : Nat) (c : Nat) (d : Nat) (e : Nat) (f : Nat) -> plus a (plus b (plus c (plus d (plus e f))))")
      repeat(3) {
        for (count in 0..6) {
          val partial = function.execute(*(1..count).map { it as Any }.toTypedArray())
          val result = if (count == 6) partial else
            partial.execute(*(count + 1..6).map { it as Any }.toTypedArray())
          assertEquals(21, result.asInt())
        }
      }
    }
  }

  @Test fun overapplicationHandlesMoreThanThreeCurryingShapes() {
    Context.create("cadenza").use { context ->
      val functionType = List(7) { "Nat" }.joinToString(" -> ")
      val apply = context.eval("cadenza", "\\(f : $functionType) -> f 1 2 3 4 5 6")
      val functions = (1..6).map { firstArity ->
        val first = (1..firstArity).joinToString(" ") { "(${('a'.code + it - 1).toChar()} : Nat)" }
        val rest = if (firstArity == 6) "" else
          "\\" + (firstArity + 1..6).joinToString(" ") { "(${('a'.code + it - 1).toChar()} : Nat)" } + " -> "
        val sum = (2..6).fold("a") { expression, i -> "plus ($expression) ${('a'.code + i - 1).toChar()}" }
        context.eval("cadenza", "\\$first -> $rest$sum")
      }
      repeat(5) {
        functions.forEach { function -> assertEquals(21, apply.execute(function).asInt()) }
      }
    }
  }
}
