import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Value
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.ByteArrayOutputStream
import java.math.BigInteger

/** Application grouping is observable when arguments and intermediate bodies have effects. */
class ApplicationEffectsTests {
  private val names = listOf("a", "b", "c", "d")
  private val weights = listOf(1000000, 10000, 100, 1).map { BigInteger.valueOf(it.toLong()) }
  private val small = listOf(3, 5, 7, 11).map { BigInteger.valueOf(it.toLong()) }
  private val recovered = listOf(2, 13, 17, 29).map { BigInteger.valueOf(it.toLong()) }
  private val huge = BigInteger.ONE.shiftLeft(80)
  private val large = listOf(huge + BigInteger.valueOf(31), BigInteger.valueOf(37),
    huge + BigInteger.valueOf(41), BigInteger.valueOf(43))

  // Each bit marks one of the three possible boundaries between four parameters.
  // Enumerating cuts, rather than generating arbitrary syntax, makes the scope exhaustive.
  private val groupings = (0 until 8).map { cuts ->
    val boundaries = (1..3).filter { cuts and (1 shl (it - 1)) != 0 } + 4
    boundaries.zip(listOf(0) + boundaries).map { (end, start) -> end - start }
  }

  private fun boundaries(groups: List<Int>): List<Int> = groups.runningFold(0, Int::plus).drop(1)

  private fun factorySource(groups: List<Int>): String {
    var body = "plus captured (plus (mult 1000000 a) (plus (mult 10000 b) (plus (mult 100 c) d)))"
    val ends = boundaries(groups)
    for (index in groups.indices.reversed()) {
      val end = ends[index]
      val start = end - groups[index]
      val binders = names.subList(start, end).joinToString(" ") { "($it : Nat)" }
      body = "\\$binders -> let entered : Nat = printId (plus captured ${10000 + end}) in " +
        "let checked : Nat = if eq failBody $end then div 1 0 else 0 in $body"
    }
    return "\\(captured : Nat) (failBody : Nat) -> $body"
  }

  private fun applySource(groups: List<Int>): String {
    var body = "f"
    var start = 0
    for (count in groups) {
      val arguments = names.subList(start, start + count).mapIndexed { offset, name ->
        "(if eq failArgument ${start + offset + 1} then div (printId $name) 0 else printId $name)"
      }.joinToString(" ")
      body = "($body $arguments)"
      start += count
    }
    return "\\(f : Nat -> Nat -> Nat -> Nat -> Nat) (failArgument : Nat) " +
      names.joinToString(" ") { "($it : Nat)" } + " -> $body"
  }

  private data class Expected(val trace: List<BigInteger>, val fails: Boolean, val result: BigInteger?)

  /**
   * This oracle schedules numbered events; it never parses or evaluates guest code.
   * A call first observes its entire argument chunk. Only afterward can any newly
   * satisfied lambda boundary run. An incomplete boundary produces no body event.
   */
  private fun expected(lambdaGroups: List<Int>, applicationGroups: List<Int>, captured: BigInteger,
                       arguments: List<BigInteger>, failArgument: Int = 0, failBody: Int = 0): Expected {
    val events = mutableListOf<BigInteger>()
    val bodyBoundaries = boundaries(lambdaGroups)
    var argument = 0
    var body = 0
    for (chunk in applicationGroups) {
      repeat(chunk) {
        events += arguments[argument]
        argument++
        if (argument == failArgument) return Expected(events, true, null)
      }
      while (body < bodyBoundaries.size && bodyBoundaries[body] <= argument) {
        val end = bodyBoundaries[body++]
        events += captured + BigInteger.valueOf((10000 + end).toLong())
        if (end == failBody) return Expected(events, true, null)
      }
    }
    return Expected(events, false, arguments.zip(weights).fold(captured) { acc, (value, weight) ->
      acc + value * weight
    })
  }

  private fun argument(value: BigInteger): Any = if (value.bitLength() < 31) value.toInt() else value

  private class Harness(val context: Context, val output: ByteArrayOutputStream)

  private fun bothBackends(name: String, test: (String, Harness) -> Unit): List<DynamicTest> =
    listOf("ast", "bytecode").map { backend -> DynamicTest.dynamicTest("$backend: $name") {
      val output = ByteArrayOutputStream()
      Context.newBuilder("cadenza").allowExperimentalOptions(true)
        .option("cadenza.Backend", backend).out(output).build().use { context ->
          test(backend, Harness(context, output))
        }
    } }

  private fun check(harness: Harness, apply: Value, function: Value, lambdaGroups: List<Int>,
                    applicationGroups: List<Int>, captured: BigInteger, arguments: List<BigInteger>,
                    location: String, failArgument: Int = 0, failBody: Int = 0) {
    val expected = expected(lambdaGroups, applicationGroups, captured, arguments, failArgument, failBody)
    val diagnostic = "$location lambdas=$lambdaGroups applications=$applicationGroups " +
      "capture=$captured arguments=$arguments failArgument=$failArgument failBody=$failBody\n" +
      "factory: ${factorySource(lambdaGroups)}\napply: ${applySource(applicationGroups)}"
    harness.output.reset()
    val supplied = arrayOf<Any>(function, failArgument, *arguments.map(::argument).toTypedArray())
    if (expected.fails) {
      val failure = assertThrows(PolyglotException::class.java, { apply.execute(*supplied) }, diagnostic)
      assertTrue(failure.isGuestException, diagnostic)
      assertFalse(failure.isInternalError, diagnostic)
      assertTrue(failure.message.orEmpty().contains("division by zero"), diagnostic)
    } else {
      assertEquals(expected.result, apply.execute(*supplied).asBigInteger(), diagnostic)
    }
    val trace = harness.output.toString(Charsets.UTF_8).lineSequence().filter { it.isNotEmpty() }
      .map(::BigInteger).toList()
    assertEquals(expected.trace, trace, diagnostic)
  }

  @TestFactory fun everyGroupingPreservesEffectsAcrossTargetAndNumericHistories() =
    bothBackends("all lambda/application boundaries through numeric and capture histories") { backend, harness ->
      val factories = groupings.map { harness.context.eval("cadenza", factorySource(it)) }
      val applications = groupings.map { harness.context.eval("cadenza", applySource(it)) }
      val captures = groupings.indices.map { BigInteger.valueOf((101 + 17 * it).toLong()) }
      val original = factories.indices.map { factories[it].execute(argument(captures[it]), 0) }

      for (phase in 0..2) {
        val changed = phase == 1
        val phaseCaptures = captures.map { if (changed) huge + it else it }
        val functions = if (changed) factories.indices.map {
          factories[it].execute(argument(phaseCaptures[it]), 0)
        } else original
        val values = when (phase) { 0 -> small; 1 -> large; else -> recovered }
        for (applicationIndex in groupings.indices) {
          // Reusing each apply body with all eight distinct targets exceeds the bounded
          // direct caches. The final phase returns to closures retained before promotion.
          val indices = if (changed) groupings.indices.reversed() else groupings.indices.toList()
          for (index in indices) {
            check(harness, applications[applicationIndex], functions[index], groupings[index],
              groupings[applicationIndex], phaseCaptures[index], values, "$backend phase=$phase")
          }
        }
      }
    }

  @TestFactory fun everyFailureBoundaryStopsEffectsAndTheSameCallSitesRecover() =
    bothBackends("argument/body failure boundaries through partial and overapplication") { backend, harness ->
      val factories = groupings.map { harness.context.eval("cadenza", factorySource(it)) }
      val applications = groupings.map { harness.context.eval("cadenza", applySource(it)) }
      for (applicationIndex in groupings.indices) {
        for (index in groupings.indices) {
          val captured = BigInteger.valueOf((503 + 19 * index).toLong())
          val good = factories[index].execute(argument(captured), 0)
          val failures = (1..4).map { it to 0 } + boundaries(groupings[index]).map { 0 to it }
          for ((failArgument, failBody) in failures) {
            val function = if (failBody == 0) good else factories[index].execute(argument(captured), failBody)
            check(harness, applications[applicationIndex], function, groupings[index],
              groupings[applicationIndex], captured, small, "$backend failure", failArgument, failBody)
            check(harness, applications[applicationIndex], good, groupings[index],
              groupings[applicationIndex], captured, recovered, "$backend immediate recovery")
          }
        }
      }
    }
}
