import org.graalvm.polyglot.Context
import org.junit.jupiter.api.Test

val ctx: Context = Context.create()

class ExampleTests {
  @Test fun fib() {
    val x = ctx.eval("cadenza", "fixNatF (\\(f : Nat -> Nat) (x : Nat) -> if le x 1 then x else plus (f (minus x 1)) (f (minus x 2))) 15")
    assert(x.asInt() == 610)
  }

  @Test fun add() {
    val x = ctx.eval("cadenza", "fixNatF (\\(f : Nat -> Nat) (x : Nat) -> if le 100 x then x else f (plus x 1)) 0")
    assert(x.asInt() == 100)
  }
}