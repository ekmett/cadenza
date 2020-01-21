package cadenza.interpreter

import cadenza.data.append
import cadenza.data.drop
import cadenza.data.map
import cadenza.data.take
import cadenza.jit.Builtin
import cadenza.jit.FixNatF
import cadenza.jit.Plus
import cadenza.jit.initialCtx
import cadenza.semantics.*
import cadenza.syntax.*
import com.oracle.truffle.api.source.Source

// de bruijn indexed core, for comparsion vs truffle
sealed class Expr
data class Lam(val n: Int, val b: Expr) : Expr()
data class Var(val n: Int) : Expr()
data class App(val f: Expr, val args: Array<Expr>) : Expr()
data class If(val cond: Expr, val then: Expr, val else_: Expr) : Expr()
data class Const(val x: Any) : Expr()

// a env (total substitution to values) applied to Lam n f, as a value
data class Closure(val n: Int, val b: Expr, val env: Env)
// a partially applied builtin, as a value
data class BuiltinClosure(val builtin: Builtin, val papArgs: Array<Any>)

// singly linked list for environment
data class InterpConsEnv(val v: Any, val e: InterpConsEnv?)
typealias Env = InterpConsEnv?

fun Env.lookup(ix: Int): Any? {
  var it = this
  var k = ix
  while (k > 0 && it != null) {
    it = it.e
    k--
  }
  if (it == null) {
    return null
  }
  return it.v
}

// assumes it typechecks (use Term.infer to typecheck)
fun elab(ctx: Ctx, tm: Term): Expr = when (tm) {
  is Term.TVar -> Var(ctx.lookupIx(tm.name))
  is Term.TIf -> If(elab(ctx, tm.cond), elab(ctx, tm.thenTerm), elab(ctx, tm.elseTerm))
  is Term.TApp -> App(elab(ctx, tm.trator), map(tm.trands) { elab(ctx, it) })
  is Term.TLam -> {
    val ctx2 = tm.names.fold(ctx) { x, (n, ty) -> ConsEnv(n, NameInfo(ty, null), x) }
    Lam(tm.names.size, elab(ctx2, tm.body))
  }
  is Term.TLitNat -> Const(tm.it)
}

fun parse(source: Source): Expr =
  when (val result = source.parse { grammar }) {
    is Failure -> {
      print(result)
      throw SyntaxError(result)
    }
    is Success -> {
      elab(initialCtx, result.value)
    }
  }

fun Expr.subst(i: Int, v: Expr): Expr = when (this) {
  is Lam -> Lam(n, b.subst(i + n, v))
  is Var -> when {
    n == i -> v
    n < i -> Var(n)
    else -> Var(n - 1)
  }
  is App -> App(f.subst(i, v), map(args) { it.subst(i, v) })
  is If -> If(cond.subst(i, v), then.subst(i, v), else_.subst(i, v))
  is Const -> this
}

// initialEnv : initialCtx
val initialEnv: Env by lazy {
  val r: ArrayList<Any> = ArrayList()
  var x = initialCtx
  while (x != null) {
    val b = x.value.builtin
    if (b != null) {
      r.add(BuiltinClosure(b, arrayOf()))
    } else {
      TODO()
    }
    x = x.next
  }
  r.foldRight(null as Env) { v, e -> InterpConsEnv(v, e) }
}

fun Env.consMany(ls: Array<Any>): Env = ls.fold(this) { r, x -> InterpConsEnv(x, r) }

fun call(fn: Any, args: Array<Any>): Any = when(fn) {
  is Closure -> fn.call(args)
  is BuiltinClosure -> fn.call(args)
  else -> TODO()
}

fun Closure.call(args: Array<Any>): Any = when {
  args.size == n -> b.eval(env.consMany(args))
  args.size < n -> Closure(n - args.size, b, env.consMany(args))
  else -> call(
    b.eval(env.consMany(take(n, args))),
    drop(n, args) as Array<Any>
  )
}

fun BuiltinClosure.call(args: Array<Any>): Any {
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

fun callBuiltin(builtin: Builtin, ys: Array<Any>): Any = when (builtin) {
  is FixNatF -> {
    val selfApp = BuiltinClosure(builtin, arrayOf(ys[0]))
    call(ys[0], arrayOf(selfApp, ys[1]))
  }
  else -> builtin.run(ys as Array<Any?>)!!
}

fun Expr.eval(env: Env): Any = when (this) {
  is Var -> env.lookup(n)!!
  is App -> call(f.eval(env), map(args) { it.eval(env) })
  is Const -> x
  is If -> if (cond.eval(env) as Boolean) then.eval(env) else else_.eval(env)
  is Lam -> Closure(n, b, env)
}

