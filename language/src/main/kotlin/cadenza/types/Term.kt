package cadenza.types

import cadenza.nodes.Code
import com.oracle.truffle.api.frame.FrameDescriptor

import java.util.Arrays

import cadenza.nodes.Code.*

// terms can be checked and inferred. The result is an expression.
abstract class Term {
  // expected is optional for some types, giving us bidirectional type checking.
  @Throws(TypeError::class)
  abstract fun check(ctx: Ctx, expected: Type?): Witness

  @Throws(TypeError::class)
  fun infer(ctx: Ctx): Witness {
    return check(ctx, null)
  }

  // provides an expression with a given type in a given frame
  abstract class Witness internal constructor(val type: Type) {
    abstract fun compile(fd: FrameDescriptor): Code  // take a frame descriptor and emit an expression

    // builder style
    @Throws(TypeError::class)
    fun match(expectedType: Type?): Witness {
      if (expectedType != null && type !== expectedType)
        throw TypeError("type mismatch", type, expectedType)
      return this
    }
  }

  // singly linked list
  class Ctx(val name: String, val type: Type, val next: Ctx) {
    companion object {

      var nil: Ctx? = null
      fun cons(name: String, type: Type, next: Ctx): Ctx {
        return Ctx(name, type, next)
      }
    }
  }

  companion object {

    fun tname(name: String): Term {
      return object : Term() {
        @Throws(TypeError::class)
        override fun check(ctx: Ctx, _expectedType: Type?): Witness {
          return object : Witness(lookup(ctx, name)) {
            override fun compile(fd: FrameDescriptor): Code {
              return Code.`var`(fd.findOrAddFrameSlot(name))
            }
          }
        }
      }
    }

    fun tif(body: Term, thenTerm: Term, elseTerm: Term): Term {
      return object : Term() {
        @Throws(TypeError::class)
        override fun check(ctx: Ctx, expectedType: Type?): Witness {
          val bodyWitness = body.check(ctx, Type.Bool)
          val thenWitness = thenTerm.check(ctx, expectedType)
          val actualType = thenWitness.type
          val elseWitness = elseTerm.check(ctx, actualType)
          return object : Witness(actualType) {
            override fun compile(fd: FrameDescriptor): Code {
              return Code.If(actualType, bodyWitness.compile(fd), thenWitness.compile(fd), elseWitness.compile(fd))
            }
          }
        }
      }
    }

    fun tapp(trator: Term, vararg trands: Term): Term {
      return object : Term() {
        @Throws(TypeError::class)
        override fun check(ctx: Ctx, expectedType: Type?): Witness {
          val wrator = trator.check(ctx, expectedType)
          var currentType = wrator.type
          val len = trands.size
          val wrands = trands.map {
            val arr = currentType as Type.Arr ?: throw TypeError("not a fun type")
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
    }

    fun tlam(_names: Array<String>, _body: Term): Term? {
      return null
    }

    @Throws(TypeError::class)
    internal fun lookup(ctx: Ctx, name: String): Type {
      var current: Ctx? = ctx
      while (current != null) {
        if (name == current.name) return current.type
        current = current.next
      }
      throw TypeError("unknown variable")
    }
  }

}
