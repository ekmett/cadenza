import cadenza.Language
import cadenza.data.BigInt
import cadenza.data.Closure
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.source.Source
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.util.Random

/** Independent, simply typed, pure and total source generator; no production evaluator.
 * No print, recursive let, neutral term, or zero divisor is generated. Observable effects
 * and failures have separate oracles in EvaluationOrderTests and RuntimeTransitionTests.
 */
class DifferentialTests {
  private sealed interface Ty {
    data object Nat : Ty
    data object Bool : Ty
    data class Arrow(val argument: Ty, val result: Ty) : Ty

    fun source(): String = when (this) {
      Nat -> "Nat"
      Bool -> "Bool"
      is Arrow -> "(${argument.source()} -> ${result.source()})"
    }
  }

  private class Function(val apply: (Any) -> Any)
  private class Expression(
    val type: Ty,
    val source: String,
    val children: List<Expression> = emptyList(),
    val freeVariables: Set<String> = children.flatMap { it.freeVariables }.toSet(),
    val eval: (Map<String, Any>) -> Any
  )

  private fun context(backend: String) = Context.newBuilder("cadenza").allowExperimentalOptions(true)
    .option("cadenza.Backend", backend).build()

  private fun scalar(value: Value, type: Ty): Any =
    if (type == Ty.Nat) value.asBigInteger() else value.asBoolean()

  private data class Mismatch(val expected: Any, val actual: Any?, val error: Throwable? = null) {
    val signature: String get() = error?.let { "${it.javaClass.name}:${it.message}" } ?: "wrong value"
  }

  private fun mismatch(context: Context, expression: Expression): Mismatch? {
    val expected = expression.eval(emptyMap())
    return try {
      val actual = scalar(context.eval("cadenza", expression.source), expression.type)
      if (expected == actual) null else Mismatch(expected, actual)
    } catch (error: Throwable) { Mismatch(expected, null, error) }
  }

  /** Diagnostic reduction, not a claim of global minimality: preserve type, closure and failure. */
  private fun smallerFailure(context: Context, original: Expression, failure: Mismatch): Expression {
    val descendants = mutableListOf<Expression>()
    fun visit(expression: Expression) {
      expression.children.forEach { child -> descendants += child; visit(child) }
    }
    visit(original)
    return descendants.asSequence()
      .filter { it.type == original.type && it.freeVariables.isEmpty() && it.source.length < original.source.length }
      .distinctBy { it.source }.sortedBy { it.source.length }.take(64)
      .firstOrNull { mismatch(context, it)?.signature == failure.signature } ?: original
  }

  private class Generator(seed: Long, smallLiterals: Boolean = false) {
    private val random = Random(seed)
    private var nextName = 0
    private val numbers = (if (smallLiterals) listOf("0", "1", "2", "3") else
      listOf("0", "1", "2", "7", "42", "46341", "2147483647", "2147483648",
        "9223372036854775808", "1267650600228229401496703205376")).map(::BigInteger)
    private val argumentTypes = listOf(Ty.Nat, Ty.Bool, Ty.Arrow(Ty.Nat, Ty.Nat))

    private fun <T> choose(values: List<T>): T = values[random.nextInt(values.size)]

    private fun name(scope: Map<String, Ty>): String {
      if (scope.isNotEmpty() && random.nextInt(3) == 0) return choose(scope.keys.toList())
      var index = nextName++
      return buildString {
        append('v')
        do {
          append('a' + index % 26)
          index /= 26
        } while (index != 0)
      }
    }

    private fun literal(value: BigInteger = choose(numbers)) = Expression(Ty.Nat, value.toString()) { value }

    private fun atom(type: Ty, scope: Map<String, Ty>): Expression {
      val variables = scope.filterValues { it == type }.keys.toList()
      if (variables.isNotEmpty() && random.nextBoolean()) {
        val variable = choose(variables)
        return Expression(type, variable, freeVariables = setOf(variable)) { it.getValue(variable) }
      }
      return when (type) {
        Ty.Nat -> literal()
        Ty.Bool -> {
          val value = random.nextBoolean()
          Expression(type, if (value) "(eq 0 0)" else "(eq 0 1)") { value }
        }
        is Ty.Arrow -> {
          if (type == Ty.Arrow(Ty.Nat, Ty.Arrow(Ty.Nat, Ty.Nat)) && random.nextBoolean()) {
            builtin(choose(listOf("plus", "minus", "mult")))
          } else if (type == Ty.Arrow(Ty.Nat, Ty.Nat) && random.nextBoolean()) {
            apply(builtin("plus"), listOf(literal()))
          } else lambda(type, 0, scope)
        }
      }
    }

    private fun builtin(name: String): Expression {
      val resultType = if (name == "eq" || name == "le") Ty.Bool else Ty.Nat
      return Expression(Ty.Arrow(Ty.Nat, Ty.Arrow(Ty.Nat, resultType)), name) {
        Function { left -> Function { right ->
          val x = left as BigInteger
          val y = right as BigInteger
          when (name) {
            "plus" -> x.add(y)
            "minus" -> x.subtract(y)
            "mult" -> x.multiply(y)
            "div" -> x.divide(y)
            "mod" -> x.remainder(y)
            "eq" -> x == y
            "le" -> x <= y
            else -> error("unknown oracle operation $name")
          }
        } }
      }
    }

    private fun apply(function: Expression, arguments: List<Expression>): Expression {
      val resultType = arguments.fold(function.type) { type, argument ->
        val arrow = type as Ty.Arrow
        check(arrow.argument == argument.type)
        arrow.result
      }
      return Expression(resultType, "(${function.source} ${arguments.joinToString(" ") { "(${it.source})" }})",
        children = listOf(function) + arguments) { env ->
        val callable = function.eval(env)
        // A flat application evaluates all of its arguments before entering a body.
        val values = arguments.map { it.eval(env) }
        values.fold(callable) { value, argument -> (value as Function).apply(argument) }
      }
    }

    /** Different physical lambda/application groupings share the oracle's unary semantics. */
    private fun groupedApply(function: Expression, arguments: List<Expression>): Expression {
      var expression = function
      var offset = 0
      while (offset < arguments.size) {
        val count = 1 + random.nextInt(arguments.size - offset)
        expression = apply(expression, arguments.subList(offset, offset + count))
        offset += count
      }
      return expression
    }

    private fun lambda(type: Ty.Arrow, depth: Int, scope: Map<String, Ty>): Expression {
      val parameters = mutableListOf<Pair<String, Ty>>()
      var remaining: Ty = type
      var bodyScope = scope
      do {
        val arrow = remaining as Ty.Arrow
        val parameter = name(bodyScope)
        parameters += parameter to arrow.argument
        bodyScope = bodyScope + (parameter to arrow.argument)
        remaining = arrow.result
      } while (remaining is Ty.Arrow && random.nextBoolean())
      val body = expression(remaining, depth.coerceAtLeast(0), bodyScope)
      val text = "(\\${parameters.joinToString(" ") { "(${it.first} : ${it.second.source()})" }} -> ${body.source})"
      return Expression(type, text, children = listOf(body),
        freeVariables = body.freeVariables - parameters.map { it.first }.toSet()) { captured ->
        fun bind(index: Int, env: Map<String, Any>): Any =
          if (index == parameters.size) body.eval(env)
          else Function { argument -> bind(index + 1, env + (parameters[index].first to argument)) }
        bind(0, captured)
      }
    }

    fun expression(type: Ty, depth: Int, scope: Map<String, Ty> = emptyMap()): Expression {
      if (depth == 0) return atom(type, scope)
      return when (random.nextInt(7)) {
        0 -> atom(type, scope)
        1 -> if (type is Ty.Arrow) lambda(type, depth - 1, scope) else {
          val operation = choose(if (type == Ty.Bool) listOf("eq", "le") else listOf("plus", "minus", "mult", "div", "mod"))
          val left = expression(Ty.Nat, depth - 1, scope)
          val right = if (operation == "div" || operation == "mod") literal(choose(numbers.filter { it.signum() > 0 }))
            else expression(Ty.Nat, depth - 1, scope)
          groupedApply(builtin(operation), listOf(left, right))
        }
        2 -> {
          val condition = expression(Ty.Bool, depth - 1, scope)
          val yes = expression(type, depth - 1, scope)
          val no = expression(type, depth - 1, scope)
          Expression(type, "(if ${condition.source} then ${yes.source} else ${no.source})",
            children = listOf(condition, yes, no)) { env ->
            if (condition.eval(env) as Boolean) yes.eval(env) else no.eval(env)
          }
        }
        3 -> {
          val local = name(scope)
          val localType = choose(argumentTypes)
          // Cadenza lets are recursive: a shadowed name cannot refer to its outer binding
          // in the initializer. Excluding it keeps these generated lets nonrecursive.
          val value = expression(localType, depth - 1, scope - local)
          val body = expression(type, depth - 1, scope + (local to localType))
          Expression(type, "(let $local : ${localType.source()} = ${value.source} in ${body.source})",
            children = listOf(value, body), freeVariables = value.freeVariables + (body.freeVariables - local)) { env ->
            body.eval(env + (local to value.eval(env)))
          }
        }
        else -> {
          val types = List(1 + random.nextInt(3)) { choose(argumentTypes) }
          val functionType = types.foldRight(type) { argument, result -> Ty.Arrow(argument, result) } as Ty.Arrow
          val function = expression(functionType, depth - 1, scope)
          val arguments = types.map { expression(it, (depth - 1).coerceAtMost(2), scope) }
          groupedApply(function, arguments)
        }
      }
    }
  }

  @Test fun deterministicTypedProgramsAgreeWithIndependentSemantics() {
    val cases = System.getProperty("cadenza.fuzz.cases", "160").toInt()
    val seedOffset = System.getProperty("cadenza.fuzz.seedOffset", "0").toInt()
    require(cases in 1..10000) { "cadenza.fuzz.cases must be between 1 and 10000" }
    require(seedOffset >= 0 && seedOffset <= Int.MAX_VALUE - cases) {
      "cadenza.fuzz.seedOffset must leave room for the requested cases"
    }
    context("ast").use { ast -> context("bytecode").use { bytecode ->
      repeat(cases) { offset ->
        val index = seedOffset + offset
        val seed = 0xCA_DE_22L + index * 104729L
        val type = if (index % 2 == 0) Ty.Nat else Ty.Bool
        val expression = Generator(seed).expression(type, 4 + index % 2)
        val reproduction = "case=$index seed=$seed\n${expression.source}"
        check(expression.freeVariables.isEmpty()) { "generator produced an open term: $reproduction" }
        for ((backend, context) in listOf("ast" to ast, "bytecode" to bytecode)) {
          val failure = try { mismatch(context, expression) } catch (error: Throwable) {
            throw AssertionError("oracle failed: $reproduction", error)
          }
          if (failure != null) {
            val smaller = smallerFailure(context, expression, failure)
            throw AssertionError("$backend: $reproduction\nExpected: ${failure.expected}\n" +
              "Actual: ${failure.actual ?: failure.error}\nSmaller failing closed subtree:\n${smaller.source}", failure.error)
          }
        }
      }
    } }
  }

  private data class HistoryInput(
    val captured: BigInteger = BigInteger.ZERO,
    val choose: Boolean = true,
    val x: BigInteger = BigInteger.ZERO,
    val y: BigInteger = BigInteger.ZERO
  )

  private class HistoryProgram(val seed: Long, val yes: Expression, val no: Expression) {
    private fun branch(expression: Expression, tag: Int) =
      "plus (mult 128 (${expression.source})) " +
        "(plus (mult 32 captured) (plus (mult 8 y) (plus (mult 2 x) $tag)))"

    // The inner target is shared by different captures and branch choices. Supplying x
    // alone really creates a PAP: the innermost physical lambda has two parameters.
    val source = "\\(captured : Nat) -> \\(choose : Bool) -> \\(x : Nat) (y : Nat) -> " +
      "if choose then ${branch(yes, 0)} else ${branch(no, 1)}"

    fun expected(input: HistoryInput): BigInteger {
      val environment = mapOf<String, Any>("captured" to input.captured, "x" to input.x, "y" to input.y)
      val generated = (if (input.choose) yes else no).eval(environment) as BigInteger
      return generated * BigInteger.valueOf(128) + input.captured * BigInteger.valueOf(32) +
        input.y * BigInteger.valueOf(8) + input.x * BigInteger.TWO +
        if (input.choose) BigInteger.ZERO else BigInteger.ONE
    }

    fun location(input: HistoryInput) = "seed=$seed $input\n$source"
  }

  private fun historyPrograms(): List<HistoryProgram> =
    listOf(0xCA_DE_2201L, 0xCA_DE_2202L, 0xCA_DE_2203L, 0xCA_DE_2204L).map { seed ->
      val generator = Generator(seed, smallLiterals = true)
      val scope = mapOf("captured" to Ty.Nat, "x" to Ty.Nat, "y" to Ty.Nat)
      val program = HistoryProgram(seed, generator.expression(Ty.Nat, 3, scope), generator.expression(Ty.Nat, 3, scope))
      // Weighted residues guarantee each of these changes is observable even if a
      // random subexpression ignores a variable or cancels another term. Thus varying
      // arguments cannot accidentally amount to repeatedly compiling a constant.
      val initial = HistoryInput()
      val expected = program.expected(initial)
      for (changed in listOf(initial.copy(x = BigInteger.ONE), initial.copy(y = BigInteger.ONE),
        initial.copy(captured = BigInteger.ONE), initial.copy(choose = false))) {
        assertNotEquals(expected, program.expected(changed), program.location(changed))
      }
      program
    }

  private val historyHuge = BigInteger.ONE.shiftLeft(80) + BigInteger.valueOf(17)

  private fun historyArgument(value: BigInteger): Any =
    if (value <= BigInteger.valueOf(Int.MAX_VALUE.toLong())) value.toInt() else BigInt(value)

  @Test fun generatedFunctionsReplayCapturesAndPartialsAcrossNumericHistories() {
    val base = HistoryInput(BigInteger.TWO, true, BigInteger.ONE, BigInteger.TWO)
    val history = listOf(base, base.copy(x = BigInteger.valueOf(3)), base.copy(x = historyHuge),
      base.copy(captured = historyHuge), base.copy(choose = false),
      base.copy(captured = historyHuge + BigInteger.ONE, choose = false, y = historyHuge),
      base.copy(captured = BigInteger.ONE, y = BigInteger.ZERO), base)
    for (backend in listOf("ast", "bytecode")) context(backend).use { context ->
      for (program in historyPrograms()) {
        val make = context.eval("cadenza", program.source)
        val retained = mutableListOf<Pair<Value, HistoryInput>>()
        for (input in history) {
          val captured = historyArgument(input.captured)
          val x = historyArgument(input.x)
          val y = historyArgument(input.y)
          val function = make.execute(captured, input.choose)
          val partial = function.execute(x)
          val expected = program.expected(input)
          val location = "$backend ${program.location(input)}"
          assertEquals(expected, function.execute(x, y).asBigInteger(), "full: $location")
          assertEquals(expected, partial.execute(y).asBigInteger(), "partial: $location")
          assertEquals(expected, make.execute(captured, input.choose, x, y).asBigInteger(), "overapplication: $location")
          retained += partial to input
        }
        // Replay old captures and old bound x values with new y values after all later
        // calls have changed the same body target's numeric and branch profiles.
        for ((partial, input) in retained.reversed()) {
          for (y in listOf(BigInteger.ZERO, historyHuge + BigInteger.TWO, BigInteger.valueOf(3))) {
            val replay = input.copy(y = y)
            assertEquals(program.expected(replay), partial.execute(historyArgument(y)).asBigInteger(),
              "$backend retained partial ${program.location(replay)}")
          }
        }
      }
    }
  }

  @Test fun selectedGeneratedFunctionsReplayAfterVerifiedGraalCompilation() {
    CompilationTestSupport.requireOptimizingRuntime()
    CompilationTestSupport.context().use { context ->
      context.initialize("cadenza")
      context.enter()
      try {
        // Only two reviewed seeds compile here. The 160/10,000-program generator and
        // the portable history test above keep their ordinary runtime-independent scope.
        for (program in historyPrograms().take(2)) {
          val factory = Language.currentLanguage().parse(
            Source.newBuilder("cadenza", program.source, "generated-history-${program.seed}.za").build()
          ).call() as Closure
          fun call(function: Closure, vararg arguments: Any): Any? {
            assertEquals(function.arity, arguments.size)
            val prefix = if (function.env == null) arrayOf<Any?>(0L) else arrayOf<Any?>(0L, function.env)
            return function.callTarget.call(*cadenza.data.append(
              cadenza.data.append(prefix, function.papArgs), arguments))
          }
          fun make(input: HistoryInput): Closure =
            call(call(factory, historyArgument(input.captured)) as Closure, input.choose) as Closure
          fun integer(result: Any?): BigInteger = when (result) {
            is Int -> BigInteger.valueOf(result.toLong())
            is BigInt -> result.value
            else -> error("Expected concrete generated result, got $result")
          }
          val base = HistoryInput(BigInteger.TWO, true, BigInteger.ONE, BigInteger.TWO)
          val original = make(base)
          val target = original.callTarget
          val retained = InteropLibrary.getUncached().execute(original, historyArgument(base.x)) as Closure
          assertSame(target, retained.callTarget)
          fun warm(includeOtherBranch: Boolean, requirePrimitive: Boolean) {
            repeat(24) { index ->
              val input = HistoryInput(BigInteger.valueOf((index % 3).toLong()),
                !includeOtherBranch || index % 2 == 0,
                BigInteger.valueOf(((index + 1) % 3).toLong()), BigInteger.valueOf(((index + 2) % 3).toLong()))
              val function = make(input)
              assertSame(target, function.callTarget, "History must reuse the generated body target")
              val result = call(function, historyArgument(input.x), historyArgument(input.y))
              if (requirePrimitive) assertInstanceOf(Int::class.javaObjectType, result,
                "Small warmup must precede promotion: ${program.location(input)}")
              assertEquals(program.expected(input), integer(result), program.location(input))
            }
          }
          warm(includeOtherBranch = false, requirePrimitive = true)
          val promoted = base.copy(x = historyHuge)
          CompilationTestSupport.compileAndVerify(target)
          assertEquals(program.expected(promoted), integer(call(original,
            historyArgument(promoted.x), historyArgument(promoted.y))), program.location(promoted))

          // Prepare the changed environment/PAP before requesting compilation, so the
          // immediately following call enters the exact target whose code was verified.
          val changed = base.copy(captured = historyHuge, choose = false, y = historyHuge + BigInteger.ONE)
          val changedFunction = make(changed)
          val changedPartial = InteropLibrary.getUncached().execute(changedFunction, historyArgument(changed.x)) as Closure
          assertSame(target, changedPartial.callTarget)
          warm(includeOtherBranch = true, requirePrimitive = false)
          CompilationTestSupport.compileAndVerify(target)
          assertEquals(program.expected(changed), integer(call(changedPartial, historyArgument(changed.y))),
            program.location(changed))
          val replay = base.copy(y = BigInteger.valueOf(3))
          assertEquals(program.expected(replay), integer(call(retained, historyArgument(replay.y))), program.location(replay))
          assertEquals(program.expected(base), integer(call(original, historyArgument(base.x), historyArgument(base.y))),
            program.location(base))
        }
      } finally {
        context.leave()
      }
    }
  }

  /** All compositions of n enumerate every possible placement of application boundaries. */
  private fun partitions(n: Int): List<List<Int>> =
    if (n == 0) listOf(emptyList()) else (1..n).flatMap { first -> partitions(n - first).map { listOf(first) + it } }

  @Test fun escapingHigherOrderClosuresObeyEveryApplicationGroupingAfterCacheSaturation() {
    val source = "\\(captured : Nat) (choose : Bool) -> if choose then " +
      "\\(f : Nat -> Nat) (a : Nat) (b : Nat) -> plus captured (f (minus (mult a 3) b)) else " +
      "\\(f : Nat -> Nat) -> \\(a : Nat) -> \\(b : Nat) -> minus (f (minus (mult a 3) b)) captured"
    for (backend in listOf("ast", "bytecode")) context(backend).use { context ->
      val function = context.eval("cadenza", source)
      val helperFactory = context.eval("cadenza",
        "\\(scale : Nat) (bias : Nat) -> \\(x : Nat) -> plus (mult x scale) bias")
      val saved = mutableListOf<Pair<Value, BigInteger>>()
      // More than three helper targets exercise the same higher-order call sites after
      // their direct caches saturate. Small inputs return after BigInt specialization.
      for (index in listOf(0, 1, 2, 3, 4, 5, 6, 7, 0, 1)) {
        val scale = BigInteger.valueOf((index + 2).toLong())
        val bias = BigInteger.valueOf((index * 7 + 1).toLong())
        val helperSource = "\\(x : Nat) -> plus (mult x $scale) $bias"
        // Alternate one target with varying captured environments and several targets
        // with no environment, so the generic path must handle both calling conventions.
        val helper = if (index % 2 == 0) helperFactory.execute(scale, bias)
          else context.eval("cadenza", helperSource)
        val captured = if (index % 2 == 0) BigInteger.valueOf(41) else BigInteger.ONE.shiftLeft(100) + bias
        val a = if (index % 3 == 0) BigInteger.ONE.shiftLeft(40) else BigInteger.valueOf(17)
        val b = BigInteger.valueOf(5)
        for (choice in listOf(true, false)) {
          val transformed = (a * BigInteger.valueOf(3) - b) * scale + bias
          val expected = if (choice) transformed + captured else transformed - captured
          val arguments = arrayOf<Any>(captured, choice, helper, a, b)
          for (groups in partitions(arguments.size)) {
            var partial = function.execute() // Empty application must also preserve captures/PAPs.
            var offset = 0
            for (count in groups) {
              partial = partial.execute(*arguments.copyOfRange(offset, offset + count))
              offset += count
              if (offset < arguments.size) partial = partial.execute()
            }
            assertEquals(expected, partial.asBigInteger(),
              "$backend helper=$index choice=$choice groups=$groups captured=$captured a=$a b=$b\n$source\n$helperSource")
          }
          saved += function.execute(captured, choice, helper) to expected
        }
      }
      // Earlier partials must survive subsequent calls with other captures and targets.
      saved.forEachIndexed { index, (partial, expected) ->
        val helperIndex = listOf(0, 1, 2, 3, 4, 5, 6, 7, 0, 1)[index / 2]
        val a = if (helperIndex % 3 == 0) BigInteger.ONE.shiftLeft(40) else BigInteger.valueOf(17)
        assertEquals(expected, partial.execute(a, 5).asBigInteger(), "$backend retained partial $index")
      }
    }
  }

  private data class RecursiveProgram(
    val name: String,
    val body: String,
    val inputs: List<Int>,
    val oracle: (BigInteger, BigInteger, Int) -> BigInteger
  )

  @Test fun boundedRecursiveProgramsAgreeWithClosedFormsAndAnIterativeOracle() {
    val linear: (BigInteger, BigInteger, Int) -> BigInteger = { seed, step, n -> seed + step * BigInteger.valueOf(n.toLong()) }
    val smallInputs = listOf(0, 1, 2, 7, 17, 31)
    val programs = listOf(
      RecursiveProgram("tail accumulator",
        "let loop : Nat -> Nat -> Nat = \\(n : Nat) (acc : Nat) -> " +
          "if eq n 0 then acc else let next : Nat = plus acc step in loop (minus n 1) next in \\(n : Nat) -> loop n seed",
        smallInputs, linear),
      RecursiveProgram("curried tail accumulator with changing captures",
        "let loop : Nat -> Nat -> Nat = \\(n : Nat) -> \\(acc : Nat) -> " +
          "if eq n 0 then acc else (loop (minus n 1)) (plus acc step) in \\(n : Nat) -> loop n seed",
        smallInputs, linear),
      RecursiveProgram("non-tail continuation",
        "let loop : Nat -> Nat = \\(n : Nat) -> if eq n 0 then seed else plus step (loop (minus n 1)) in loop",
        smallInputs, linear),
      RecursiveProgram("fixed-point non-tail continuation",
        "fixNatF (\\(self : Nat -> Nat) (n : Nat) -> if eq n 0 then seed else plus step (self (minus n 1)))",
        smallInputs, linear),
      RecursiveProgram("recursive higher-order state escapes before final overapplication",
        "let build : Nat -> (Nat -> Nat) -> Nat -> Nat = \\(n : Nat) (f : Nat -> Nat) -> " +
          "if eq n 0 then f else build (minus n 1) (\\(x : Nat) -> f (plus x step)) in " +
          "\\(n : Nat) -> build n (\\(x : Nat) -> plus seed x) 0",
        smallInputs, linear),
      RecursiveProgram("branching non-tail recurrence",
        "let fib : Nat -> Nat = \\(n : Nat) -> if eq n 0 then seed else if eq n 1 then step else " +
          "plus (fib (minus n 1)) (fib (minus n 2)) in fib",
        listOf(0, 1, 2, 5, 9, 12), { seed, step, n ->
          var previous = seed
          var current = step
          repeat(n) { val next = previous + current; previous = current; current = next }
          previous
        })
    )
    val captures = listOf(
      BigInteger.ZERO to BigInteger.ONE,
      BigInteger.ONE to BigInteger.ZERO,
      BigInteger.valueOf(5) to BigInteger.valueOf(7),
      BigInteger.valueOf(Int.MAX_VALUE.toLong()) to BigInteger.ONE,
      BigInteger.ONE.shiftLeft(100) to BigInteger.ONE.shiftLeft(65),
      BigInteger.valueOf(9) to BigInteger.TWO
    )
    for (backend in listOf("ast", "bytecode")) context(backend).use { context ->
      for (program in programs) {
        val source = "\\(seed : Nat) (step : Nat) -> ${program.body}"
        val make = context.eval("cadenza", source)
        val retained = captures.map { (seed, step) -> make.execute(seed, step) }
        // Reverse traversal revisits older environments after later ones warmed the same AST.
        for (index in captures.indices.reversed()) {
          val (seed, step) = captures[index]
          for (n in program.inputs) {
            assertEquals(program.oracle(seed, step, n), retained[index].execute(n).asBigInteger(),
              "$backend ${program.name}: seed=$seed step=$step n=$n\n$source")
          }
        }
      }
      val mutual = context.eval("cadenza",
        "let even : Nat -> Bool = let odd : Nat -> Bool = \\(n : Nat) -> " +
          "if eq n 0 then eq 0 1 else even (minus n 1) in " +
          "\\(n : Nat) -> if eq n 0 then eq 0 0 else odd (minus n 1) in even")
      for (n in listOf(0, 1, 2, 7, 32, 1001)) {
        assertEquals(n % 2 == 0, mutual.execute(n).asBoolean(), "$backend mutual recursion n=$n")
      }
    }
  }
}
