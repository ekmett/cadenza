import org.intelligence.parser.*
import cadenza.syntax.*
import cadenza.Language
import cadenza.Loc
import cadenza.data.BigInt
import cadenza.jit.Code
import cadenza.semantics.Term
import com.oracle.truffle.api.source.Source
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.math.BigInteger

class ParserTests {
  val source : Source by lazy {
    Source.newBuilder("cadenza", "xxx", "xxx.za").build()
  }

  val source2 : Source by lazy {
    Source.newBuilder("cadenza", "(\\(x : Nat) (y : Nat) -> plus x y) w z", "lam.za").build()
  }

  @Test fun stringCanEndExactlyAtEof() {
    val parser = Parse(")")
    org.junit.jupiter.api.Assertions.assertEquals(")", parser.string(")"))
    parser.eof
  }

  @Test fun parenthesizedApplicationAtEofAndBooleanTypes() {
    for (text in listOf("plus (mult 6 7) (minus 2 2)", "\\(b : Bool) -> if b then 1 else 2")) {
      val parsed = Source.newBuilder("cadenza", text, "eof.za").build().parse { grammar }
      org.junit.jupiter.api.Assertions.assertTrue(parsed is Success<*>, text)
    }
  }

  @Test fun lam() {
    val result = source2.parse { grammar } as Success<*>
  }

  @Test fun many() {
    val result = source.parse {
      many { char('x') }
    } as Success<List<Char>>
    val xs = result.value
    assert(xs.size == 3) { "bad size" }
    xs.forEach { assert(it == 'x') }
  }

  @Test fun some() {
    source.parse {
      some { char('y') }
    } as Failure
    val result = source.parse {
      some { char('x') }
    } as Success
    val xs = result.value
    assert(xs.size == 3) { "bad size" }
    xs.forEach { assert(it == 'x') }
  }

  @Test fun choice() {
    val bad = source.parse<Nothing> {
      choice({expected("a")},{expected("b")})
    } as Failure
    val es = bad.expected.toTypedArray()
    assert(es.size == 2)
    assert(es[0] == "a")
    assert(es[1] == "b")
  }
}

class ParserBoundaryTests {
  private fun context(backend: String) = Context.newBuilder("cadenza")
    .allowExperimentalOptions(true).option("cadenza.Backend", backend).build()

  @TestFactory fun whitespaceKeywordPrefixesAndParenthesizedTypes(): List<DynamicTest> {
    val programs = listOf(
      " 42 ",
      "\n\t42\r\n",
      "(\\ (x : Nat) -> x) 42",
      "let iffy : Nat = 42 in iffy",
      "let letter : Nat = 42 in letter",
      "let inbox : Nat = 42 in inbox",
      "let thenx : Nat = 42 in thenx",
      "let elsewhere : Nat = 42 in elsewhere",
      "let Natty : Nat = 42 in Natty",
      "let Boolish : Nat = 42 in Boolish",
      "(\\(f : (Nat -> Nat) -> Nat) -> f (\\(x : Nat) -> x)) (\\(g : Nat -> Nat) -> g 42)",
      "(\\(f : Nat -> Nat -> Nat) -> f 20 22) plus",
      "let f : ((Nat) -> (Nat)) = \\(x : Nat) -> x in f 42"
    )
    return listOf("ast", "bytecode").flatMap { backend -> programs.map { program ->
      DynamicTest.dynamicTest("$backend: $program") {
        context(backend).use { assertEquals(42, it.eval("cadenza", program).asInt()) }
      }
    } }
  }

  @TestFactory fun malformedSourcesAreLocatedGuestSyntaxErrors(): List<DynamicTest> {
    val programs = listOf(
      "", " ", "\n", "(", "(42", "42)", "42 @ garbage", "42;",
      "letx : Nat = 42 in x", "let let : Nat = 42 in let", "if eq 1 1 then 42",
      "\\(x : Natty) -> x", "\\(x : Boolish) -> x", "\\(x : (Nat ->)) -> x"
    )
    return listOf("ast", "bytecode").flatMap { backend -> programs.map { program ->
      DynamicTest.dynamicTest("$backend rejects: $program") {
        context(backend).use { context ->
          val error = assertThrows(PolyglotException::class.java) { context.eval("cadenza", program) }
          assertTrue(error.isSyntaxError)
          assertFalse(error.isInternalError)
          assertNotNull(error.sourceLocation)
          assertTrue(error.message!!.contains("expected"), error.message)
          assertTrue(error.sourceLocation!!.charIndex in 0..program.length)
        }
      }
    } }
  }

  @TestFactory fun naturalLiteralsExtendBeyondMachineIntegers(): List<DynamicTest> {
    val programs = listOf(
      "2147483647" to "2147483647",
      "2147483648" to "2147483648",
      "9223372036854775808" to "9223372036854775808",
      "12345678901234567890123456789012345678901234567890" to "12345678901234567890123456789012345678901234567890",
      "plus 2147483648 1" to "2147483649",
      "minus 2147483648 1" to "2147483647",
      "(\\(x : Nat) -> (\\(y : Nat) -> plus x y) 1) 2147483648" to "2147483649",
      "00000000000000000000000000000000042" to "42"
    )
    return listOf("ast", "bytecode").flatMap { backend -> programs.map { (program, expected) ->
      DynamicTest.dynamicTest("$backend: $program") {
        context(backend).use { assertEquals(BigInteger(expected), it.eval("cadenza", program).asBigInteger()) }
      }
    } }
  }

  @Test fun referenceInterpreterAcceptsLargeLiteralConstants() {
    val source = Source.newBuilder("cadenza", "\n2147483648", "literal.za").build()
    val expression = cadenza.interpreter.parse(source)
    val constant = expression as cadenza.interpreter.Const
    assertEquals(BigInteger("2147483648"), (constant.x as BigInt).value)
  }

  @Test fun genericParserStillAllowsExplicitPartialConsumption() {
    val source = Source.newBuilder("cadenza", "42)", "partial.za").build()
    assertTrue(source.parse { grammar } is Success)
    val result = source.parse { program } as Failure
    assertEquals(2, result.pos)
    assertTrue(result.diagnostic.contains("EOF"))
  }

  @Test fun syntaxErrorsCarryDiagnosticsWithoutPrintingGlobally() {
    val globalOutput = ByteArrayOutputStream()
    val original = System.out
    Context.create("cadenza").use { context ->
      context.initialize("cadenza")
      try {
        System.setOut(PrintStream(globalOutput))
        val error = assertThrows(PolyglotException::class.java) { context.eval("cadenza", "(42") }
        assertTrue(error.message!!.contains("expected"), error.message)
        assertTrue(error.message!!.contains(")"), error.message)
      } finally {
        System.setOut(original)
      }
    }
    assertEquals("", globalOutput.toString(Charsets.UTF_8))
  }

  @Test fun conditionalsRetainSourceRangesForInstrumentation() {
    val text = "if le 1 2 then 42 else 0"
    val source = Source.newBuilder("cadenza", text, "if.za").build()
    val term = (source.parse { program } as Success).value as Term.TIf
    assertEquals(Loc.Range(0, text.length), term.loc)
    context("ast").use { context ->
      context.initialize("cadenza")
      context.enter()
      try {
        val target = Language.currentLanguage().parse(source) as com.oracle.truffle.api.RootCallTarget
        var conditional: Code.If? = null
        target.rootNode.accept { node ->
          if (node is Code.If) conditional = node
          true
        }
        assertNotNull(conditional)
        assertTrue(conditional!!.isInstrumentable())
        assertEquals(text, conditional!!.sourceSection!!.characters.toString())
      } finally {
        context.leave()
      }
    }
  }
}

