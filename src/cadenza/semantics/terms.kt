package cadenza.semantics

import cadenza.*
import cadenza.jit.*
import cadenza.jit.Code.Companion.lam
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.source.Source

// TODO: should be data NameInfo = Local | Global GlobalNameInfo | Builtin Builtin
// (builtin a case of GlobalNameInfo)
data class NameInfo(val type: Type, val builtin: Builtin?)

typealias Ctx = Env<NameInfo>

data class CompileInfo(
  val source: Source,
  val language: Language
)

// terms can be checked and inferred. The result is an expression.
abstract class Term {
  @Throws(TypeError::class) open fun check(ctx: Ctx, expectedType: Type): Witness = infer(ctx).match(expectedType)
  @Throws(TypeError::class) abstract fun infer(ctx: Ctx): Witness

  // provides an expression with a given type in a given frame
  abstract class Witness internal constructor(val type: Type) {
    abstract fun compile(ci: CompileInfo, fd: FrameDescriptor): Code
    @Throws(TypeError::class)
    fun match(expectedType: Type): Witness =
      if (type == expectedType) this
      else throw TypeError("type mismatch", type, expectedType)
  }

  companion object {
    @Suppress("unused")
    fun tvar(name: String, loc: Loc? = null): Term = object : Term() {
      @Throws(TypeError::class)
      override fun infer(ctx: Ctx): Witness {
        val info = ctx.lookup(name)
        return object : Witness(info.type) {
          override fun compile(ci: CompileInfo, fd: FrameDescriptor): Code {
            if (info.builtin != null) {
              val builtin = info.builtin
              // TODO: statically cook this?
              val target = Truffle.getRuntime().createCallTarget(
                BuiltinRootNode(ci.language, builtin)
              )
              return lam(builtin.arity, target, builtin.type, loc)
            } else {
              return Code.`var`(fd.findOrAddFrameSlot(name), loc)
            }
          }
        }
      }
    }

    @Suppress("unused")
    fun tif(cond: Term, thenTerm: Term, elseTerm: Term, loc: Loc? = null): Term = object : Term() {
      @Throws(TypeError::class)
      override fun infer(ctx: Ctx): Witness {
        val condWitness = cond.check(ctx, Type.Bool)
        val thenWitness = thenTerm.infer(ctx)
        val actualType = thenWitness.type
        val elseWitness = elseTerm.check(ctx, actualType)
        return object : Witness(actualType) {
          override fun compile(ci: CompileInfo, fd: FrameDescriptor): Code {
            return Code.If(actualType, condWitness.compile(ci,fd), thenWitness.compile(ci,fd), elseWitness.compile(ci,fd), loc)
          }
        }
      }
    }

    @Suppress("unused")
    fun tapp(trator: Term, trands: Array<Term>, loc: Loc? = null): Term = object : Term() {
      @Throws(TypeError::class)
      override fun infer(ctx: Ctx): Witness {
        val wrator = trator.infer(ctx)
        var currentType = wrator.type
        val wrands = trands.map {
          val arr = currentType as Type.Arr? ?: throw TypeError("not a fun type")
          val out = it.check(ctx, arr.argument)
          currentType = arr.result
          out
        }.toTypedArray<Witness>()
        return object : Witness(currentType) {
          override fun compile(ci: CompileInfo, fd: FrameDescriptor): Code {
            return Code.App(
              wrator.compile(ci,fd),
              wrands.map { it.compile(ci,fd) }.toTypedArray(),
              loc
            )
          }
        }
      }
    }

    @Suppress("UNUSED_PARAMETER","unused")
    fun tlam(names: Array<Pair<Name,Type>>, body: Term, loc: Loc? = null): Term = object : Term() {
      override fun infer(ctx: Ctx): Witness {
        val ctx2 = names.fold(ctx) { x, (n, ty) -> ConsEnv(n, NameInfo(ty, null), x) }
        val bodyw = body.infer(ctx2)
        val aty = names.foldRight(bodyw.type) { (_,ty), x -> Type.Arr(ty, x) }
        return object : Witness(aty) {
          // looks at what vars the body adds to it's FrameDescriptor to decide what to capture
          // TODO: maybe should calculate fvs instead?
          override fun compile(ci: CompileInfo, fd: FrameDescriptor): Code {
            val bodyFd = FrameDescriptor()
            val bodyCode = bodyw.compile(ci, bodyFd)
            val closureFd = FrameDescriptor()
            val closureCaptures = arrayListOf<FrameBuilder>();
            val envPreamble = arrayListOf<FrameBuilder>();
            val argPreamble = arrayListOf<FrameBuilder>();
            val captures = bodyFd.slots.any { slot -> names.find { it.first == slot.identifier } == null }
            for (slot in bodyFd.slots) {
              val name = slot.identifier
              val ix = names.indexOfLast { it.first == name }
              if (ix == -1) {
                val closureSlot = closureFd.addFrameSlot(name)
                val parentSlot = fd.findOrAddFrameSlot(name)
                closureCaptures += put(closureSlot, Code.`var`(parentSlot))
                envPreamble += put(slot, Code.`var`(closureSlot))
              } else {
                argPreamble += put(slot, Code.Arg(if (captures) ix+1 else ix))
              }
            }

            assert((!captures) || envPreamble.isNotEmpty())

            return Code.lam(
              if (captures) closureFd else null,
              closureCaptures.toTypedArray(),
              names.size,
              Truffle.getRuntime().createCallTarget(
                ClosureRootNode(
                  ci.language,
                  bodyFd,
                  names.size,
                  envPreamble.toTypedArray(),
                  argPreamble.toTypedArray(),
                  ClosureBody(bodyCode),
                  ci.source,
                  loc
                )
              ),
              aty,
              loc
            )
          }
        }
      }
    }

    fun tlitNat(it: Int, loc: Loc? = null)  = object : Term () {
      override fun infer(ctx: Ctx): Witness {
        val ty = Type.Nat
        ty.validate(it)
        return object : Witness(ty) {
          override fun compile(ci: CompileInfo, fd: FrameDescriptor): Code {
            return Code.LitInt(it, loc)
          }
        }
      }
    }
  }
}