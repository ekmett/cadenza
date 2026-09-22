import org.graalvm.polyglot.Context
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.util.Random

/** Independent, simply typed source generator and semantic oracle; no production evaluator. */
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
  private class Expression(val type: Ty, val source: String, val eval: (Map<String, Any>) -> Any)

  private class Generator(seed: Long) {
    private val random = Random(seed)
    private var nextName = 0
    private val numbers = listOf("0", "1", "2", "7", "42", "46341", "2147483647", "2147483648",
      "9223372036854775808", "1267650600228229401496703205376").map(::BigInteger)
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
        return Expression(type, variable) { it.getValue(variable) }
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
      return Expression(resultType, "(${function.source} ${arguments.joinToString(" ") { "(${it.source})" }})") { env ->
        arguments.fold(function.eval(env)) { value, argument -> (value as Function).apply(argument.eval(env)) }
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
      return Expression(type, text) { captured ->
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
          Expression(type, "(if ${condition.source} then ${yes.source} else ${no.source})") { env ->
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
          Expression(type, "(let $local : ${localType.source()} = ${value.source} in ${body.source})") { env ->
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
    fun context(backend: String) = Context.newBuilder("cadenza").allowExperimentalOptions(true)
      .option("cadenza.Backend", backend).build()
    context("ast").use { ast -> context("bytecode").use { bytecode ->
      repeat(cases) { offset ->
        val index = seedOffset + offset
        val seed = 0xCA_DE_22L + index * 104729L
        val type = if (index % 2 == 0) Ty.Nat else Ty.Bool
        val expression = Generator(seed).expression(type, 4 + index % 2)
        val reproduction = "case=$index seed=$seed\n${expression.source}"
        val expected = try { expression.eval(emptyMap()) } catch (error: Throwable) {
          throw AssertionError("oracle failed: $reproduction", error)
        }
        for ((backend, context) in listOf("ast" to ast, "bytecode" to bytecode)) {
          val actual = try {
            val value = context.eval("cadenza", expression.source)
            if (type == Ty.Nat) value.asBigInteger() else value.asBoolean()
          } catch (error: Throwable) {
            throw AssertionError("$backend failed: $reproduction", error)
          }
          assertEquals(expected, actual, "$backend: $reproduction")
        }
      }
    } }
  }
}
