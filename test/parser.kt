import org.intelligence.parser.*
import cadenza.syntax.*
import com.oracle.truffle.api.source.Source
import org.junit.jupiter.api.Test

class ParserTests {
  val source : Source by lazy {
    Source.newBuilder("cadenza", "xxx", "xxx.za").build()
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



