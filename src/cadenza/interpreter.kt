package cadenza.interpreter

import cadenza.data.append
import cadenza.data.drop
import cadenza.data.map
import cadenza.data.take
import cadenza.jit.*
import cadenza.semantics.*
import cadenza.syntax.*
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.source.Source

typealias Env = Array<Any>

// de bruijn indexed core, for comparsion vs truffle
sealed class Expr {}
// bound vars appended to start of env
data class Lam(val n: Int, val b: Expr, val captures: Array<Int>): Expr() {}
data class Var(val n: Int) : Expr() {}
data class App(val f: Expr, val args: Array<Expr>) : Expr() {}
data class If(val cond: Expr, val then: Expr, val else_: Expr) : Expr() {}
data class Const(val x: Any) : Expr() {}

fun Expr.eval(env: Env): Any = when (this) {
  is Lam -> Closure(n, b, map(captures) { env[it] })
  is Var -> env[n]!!
  is App -> call(f.eval(env), map(args) { it.eval(env) })
  is If -> if (cond.eval(env) as Boolean) then.eval(env) else else_.eval(env)
  is Const -> x
}

sealed class Callable {
  abstract fun call(args: Array<Any>): Any
}

// a env (total substitution to values) applied to Lam n f, as a value
data class Closure(val n: Int, val b: Expr, val env: Env) : Callable() {
  override fun call(args: Array<Any>): Any = when {
    args.size == n -> b.eval(append(args,env) as Array<Any>)
    args.size < n -> Closure(n - args.size, b, append(args,env) as Array<Any>)
    else -> call(
      b.eval(append(take(n, args), env) as Array<Any>),
      drop(n, args) as Array<Any>
    )
  }
}

// a partially applied builtin, as a value
data class BuiltinClosure(val builtin: Builtin, val papArgs: Array<Any>) : Callable() {
  override fun call(args: Array<Any>): Any {
    val ys: Array<Any> = if (papArgs.isEmpty()) args else append(papArgs, args) as Array<Any>

    return when {
      ys.size < builtin.arity -> BuiltinClosure(builtin, ys)
      ys.size == builtin.arity -> callBuiltin(builtin, ys)
      else -> call(
        callBuiltin(builtin, take(builtin.arity, ys)),
        drop(builtin.arity, ys) as Array<Any>
      )
    }!!
  }
}

class FixNatF1(val f: Callable): Builtin1(natF) {
  private val clos = BuiltinClosure(this, arrayOf())

  override fun execute(x: Any?): Any? = f.call(arrayOf(clos, x as Any))
}

fun call(fn: Any, args: Array<Any>): Any = when(fn) {
  is Callable -> fn.call(args)
  else -> throw Exception("attempt to call $fn")
}

val emptyFrame = Truffle.getRuntime().createVirtualFrame(arrayOf(), FrameDescriptor())

fun callBuiltin(builtin: Builtin, ys: Array<Any>): Any = when (builtin) {
  is FixNatF -> {
    assert(ys.size == 2)
    FixNatF1(ys[0] as Callable).execute(ys[1])!!
  }
  else -> builtin.run(emptyFrame, ys as Array<Any?>)!!
}

// assumes it typechecks (use Term.infer to typecheck)
fun elab(ctx: List<Pair<String,NameInfo>>, tm: Term): Expr = when (tm) {
  is Term.TVar -> Var(ctx.indexOfFirst { it.first == tm.name })
  is Term.TIf -> If(elab(ctx, tm.cond), elab(ctx, tm.thenTerm), elab(ctx, tm.elseTerm))
  is Term.TApp -> App(elab(ctx, tm.trator), map(tm.trands) { elab(ctx, it) })
  is Term.TLam -> {
    val ctx2 = tm.names.map { Pair(it.first, NameInfo(it.second, null)) } + ctx
    lam(tm.names.size, elab(ctx2, tm.body))
  }
  is Term.TLitNat -> Const(tm.it)
  is Term.TLet -> TODO()
}

val initialCtxElab: List<Pair<String,NameInfo>> by lazy {
  val r = ArrayList<Pair<String,NameInfo>>()
  var x = initialCtx
  while (x != null) {
    r.add(Pair(x.name, x.value))
    x = x.next
  }
  r
}

val initialEnv: Env = initialCtxElab.map {
  BuiltinClosure(it.second.builtin!!, arrayOf())
}.toTypedArray()

fun parse(source: Source): Expr =
  when (val result = source.parse { grammar }) {
    is Failure -> {
      print(result)
      throw SyntaxError(result)
    }
    is Success -> {
      elab(initialCtxElab, result.value)
    }
  }


fun Expr.fvs(): Set<Int> = when(this) {
  is Lam -> captures.toSet()
  is Var -> arrayOf(n).toSet()
  is App -> f.fvs() + args.map { it.fvs() }.flatten()
  is If -> cond.fvs() + then.fvs() + else_.fvs()
  is Const -> HashSet()
}

fun lam(n: Int, b: Expr): Expr {
  val vs = b.fvs().filter { it >= n }
  return Lam(n, b.subst { Var(if (it < n) it else n + vs.indexOf(it)) }, vs.map { it-n }.toTypedArray())
}

fun Expr.subst(s: (x: Int) -> Expr): Expr = when (this) {
  is Lam -> lam(n, b.subst {
    if (it < n) Var(it) else s(captures[it-n]).subst { x -> Var(x + n) }
  })
  is Var -> s(n)
  is App -> App(f.subst(s), map(args) { it.subst(s) })
  is If -> If(cond.subst(s), then.subst(s), else_.subst(s))
  is Const -> this
}

