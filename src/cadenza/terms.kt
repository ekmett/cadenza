package cadenza

import com.oracle.truffle.api.frame.FrameDescriptor

typealias Ctx = Env<Type>

// provides an expression with a given type in a given frame
abstract class Witness internal constructor(val type: Type) {
  abstract fun compile(fd: FrameDescriptor): Code
  @Throws(TypeError::class)
  fun match(expectedType: Type): Witness =
    if (type == expectedType) this
    else throw TypeError("type mismatch", type, expectedType)
}

// terms can be checked and inferred. The result is an expression.
abstract class Term {
  @Throws(TypeError::class) open fun check(ctx: Ctx, expectedType: Type): Witness = infer(ctx).match(expectedType)
  @Throws(TypeError::class) abstract fun infer(ctx: Ctx): Witness
}

@Suppress("unused")
fun tvar(name: String): Term = object : Term() {
  @Throws(TypeError::class)
  override fun infer(ctx: Ctx): Witness = object : Witness(ctx.lookup(name)) {
    override fun compile(fd: FrameDescriptor): Code = `var`(fd.findOrAddFrameSlot(name))
  }
}

@Suppress("unused")
fun tif(cond: Term, thenTerm: Term, elseTerm: Term): Term = object : Term() {
  @Throws(TypeError::class)
  override fun infer(ctx: Ctx): Witness {
    val condWitness = cond.check(ctx, Bool)
    val thenWitness = thenTerm.infer(ctx)
    val actualType = thenWitness.type
    val elseWitness = elseTerm.check(ctx, actualType)
    return object : Witness(actualType) {
      override fun compile(fd: FrameDescriptor): Code {
        return If(actualType, condWitness.compile(fd), thenWitness.compile(fd), elseWitness.compile(fd))
      }
    }
  }
}

@Suppress("unused")
fun tapp(trator: Term, vararg trands: Term): Term = object : Term() {
  @Throws(TypeError::class)
  override fun infer(ctx: Ctx): Witness {
    val wrator = trator.infer(ctx)
    var currentType = wrator.type
    val wrands = trands.map {
      val arr = currentType as Arr? ?: throw TypeError("not a fun type")
      val out = it.check(ctx, arr.argument)
      currentType = arr.result
      return out
    }.toTypedArray<Witness>()
    return object : Witness(currentType) {
      override fun compile(fd: FrameDescriptor): Code {
        return App(
          wrator.compile(fd),
          wrands.map { it.compile(fd) }.toTypedArray()
        )
      }
    }
  }
}

@Suppress("UNUSED_PARAMETER","unused")
fun tlam(names: Array<Name>, body: Term): Term? {
  return null
}