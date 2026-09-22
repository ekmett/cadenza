package cadenza.bytecode

import cadenza.Language
import cadenza.Loc
import cadenza.data.Closure
import cadenza.jit.BuiltinRootNode
import cadenza.jit.CadenzaRootNode
import cadenza.jit.DirectCallerNode
import cadenza.jit.initialCtx
import cadenza.semantics.*
import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.bytecode.BytecodeConfig
import com.oracle.truffle.api.bytecode.BytecodeLocal
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.source.Source

/**
 * Lowers the checked surface term directly to Bytecode DSL operations. The builder parser is
 * replayable: lambda targets and constants are prepared once, while locals are recreated per replay.
 */
class BytecodeCompiler(private val language: Language, private val source: Source) {
  private data class Binding(val index: Int, val argument: Boolean, val cell: Boolean = false)
  private class Emission(val builder: BytecodeRootGen.Builder) {
    val locals = mutableMapOf<Int, BytecodeLocal>()
  }
  private fun interface Expression { fun emit(emission: Emission) }
  private var nextLocal = 0

  fun compile(term: Term): RootCallTarget {
    // Use exactly the same type checker as the AST backend.
    term.infer(initialCtx)
    val body = lower(term, emptyMap(), initialCtx, true)
    val target = build(body)
    return BytecodeEntryRoot(language, target, source).callTarget
  }

  private fun build(body: Expression): RootCallTarget =
    BytecodeRootGen.create(language, BytecodeConfig.DEFAULT) { b ->
      b.beginSource(source)
      b.beginSourceSection(0, source.length)
      b.beginRoot()
      b.beginReturn()
      body.emit(Emission(b))
      b.endReturn()
      b.endRoot()
      b.endSourceSection()
      b.endSource()
    }.getNode(0).callTarget

  private fun read(binding: Binding, force: Boolean = true) = Expression { emission ->
    val b = emission.builder
    if (force && binding.cell) b.beginReadCell()
    if (binding.argument) b.emitLoadArgument(binding.index)
    else b.emitLoadLocal(emission.locals.getValue(binding.index))
    if (force && binding.cell) b.endReadCell()
  }

  private fun lower(term: Term, scope: Map<String, Binding>, ctx: Ctx, tail: Boolean): Expression {
    val body = when (term) {
      is Term.TLitNat -> {
        val value = term.it
        Expression { it.builder.emitLoadConstant(value) }
      }
      is Term.TVar -> scope[term.name]?.let { read(it) } ?: run {
        val builtin = ctx.lookup(term.name).builtin!!.invoke()
        val target = BuiltinRootNode(language, builtin).callTarget
        val closure = Closure(null, emptyArray(), builtin.arity, builtin.type, target)
        Expression { it.builder.emitLoadConstant(closure) }
      }
      is Term.TIf -> {
        val condition = lower(term.cond, scope, ctx, false)
        val yes = lower(term.thenTerm, scope, ctx, tail)
        val no = lower(term.elseTerm, scope, ctx, tail)
        Expression {
          it.builder.beginConditional()
          condition.emit(it)
          yes.emit(it)
          no.emit(it)
          it.builder.endConditional()
        }
      }
      is Term.TApp -> {
        val arguments = term.trands.map { lower(it, scope, ctx, false) }
        val name = (term.trator as? Term.TVar)?.name
        val intrinsic = name?.takeIf { it !in scope && arguments.size == 2 && it in intrinsics }
        if (intrinsic != null) {
          Expression {
            beginIntrinsic(it.builder, intrinsic)
            arguments.forEach { argument -> argument.emit(it) }
            endIntrinsic(it.builder, intrinsic)
          }
        } else {
          val function = lower(term.trator, scope, ctx, false)
          Expression {
            it.builder.beginApply(arguments.size, tail)
            function.emit(it)
            arguments.forEach { argument -> argument.emit(it) }
            it.builder.endApply()
          }
        }
      }
      is Term.TLam -> {
        val captures = term.fvs().filter { it in scope }
        val captureLoads = captures.map { read(scope.getValue(it), false) }
        val childScope = captures.mapIndexed { index, name ->
          name to Binding(index + 1, true, scope.getValue(name).cell)
        }.toMap() + term.names.mapIndexed { index, (name, _) ->
          name to Binding(1 + captures.size + index, true)
        }
        val childCtx = term.names.fold(ctx) { context, (name, type) ->
          ConsEnv(name, NameInfo(type, null), context)
        }
        val child = lower(term.body, childScope, childCtx, true)
        val target = build(child)
        val type = term.infer(ctx).type
        val targetType = captures.fold(type) { result, _ -> Type.Arr(Type.Obj, result) }
        val template = BytecodeRoot.ClosureTemplate(target, term.names.size, targetType)
        Expression {
          it.builder.beginMakeClosure(template)
          captureLoads.forEach { capture -> capture.emit(it) }
          it.builder.endMakeClosure()
        }
      }
      is Term.TLet -> {
        val name = term.name
        val type = term.type
        val recursive = term.name in term.value.fvs()
        val binding = Binding(nextLocal++, false, recursive)
        val childScope = scope + (term.name to binding)
        val childCtx = ConsEnv(term.name, NameInfo(term.type, null), ctx)
        val value = lower(term.value, childScope, childCtx, false)
        val result = lower(term.body, childScope, childCtx, tail)
        Expression {
          val b = it.builder
          b.beginBlock()
          val local = b.createLocal(name, type)
          it.locals[binding.index] = local
          b.beginStoreLocal(local)
          if (recursive) b.emitNewCell() else value.emit(it)
          b.endStoreLocal()
          if (recursive) {
            b.beginInitializeCell()
            b.emitLoadLocal(local)
            value.emit(it)
            b.endInitializeCell()
          }
          result.emit(it)
          b.endBlock()
          it.locals.remove(binding.index)
        }
      }
    }
    val loc = when (term) {
      is Term.TLitNat -> term.loc
      is Term.TVar -> term.loc
      is Term.TIf -> term.loc
      is Term.TApp -> term.loc
      is Term.TLam -> term.loc
      is Term.TLet -> term.loc
    }
    return if (loc is Loc.Range) Expression {
      it.builder.beginSourceSection(loc.start, loc.length)
      body.emit(it)
      it.builder.endSourceSection()
    } else body
  }

  private fun beginIntrinsic(b: BytecodeRootGen.Builder, name: String) {
    when (name) {
      "plus" -> b.beginAdd()
      "minus" -> b.beginSubtract()
      "mult" -> b.beginMultiply()
      "div" -> b.beginDivide()
      "mod" -> b.beginRemainder()
      "le" -> b.beginLessOrEqual()
      "eq" -> b.beginEqual()
    }
  }

  private fun endIntrinsic(b: BytecodeRootGen.Builder, name: String) {
    when (name) {
      "plus" -> b.endAdd()
      "minus" -> b.endSubtract()
      "mult" -> b.endMultiply()
      "div" -> b.endDivide()
      "mod" -> b.endRemainder()
      "le" -> b.endLessOrEqual()
      "eq" -> b.endEqual()
    }
  }

  private companion object {
    val intrinsics = setOf("plus", "minus", "mult", "div", "mod", "le", "eq")
  }
}

/** A guest entry owns the trampoline; tail calls must escape individual bytecode function roots. */
private class BytecodeEntryRoot(language: Language, target: RootCallTarget, private val source: Source) :
  CadenzaRootNode(language, FrameDescriptor.newBuilder().build()) {
  @Child private var caller = DirectCallerNode(target)
  override fun execute(frame: VirtualFrame): Any? = caller.call(frame, arrayOf(0L), false)
  override fun getName() = "bytecode program root"
  override fun getSourceSection() = source.createSection(0, source.length)
}
