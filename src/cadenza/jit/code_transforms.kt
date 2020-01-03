package cadenza.jit

fun markTailCalls(code: Code): Code {
  return when (code) {
    is Code.App -> Code.App(code.rator, code.rands, code.loc, true)
    is Code.If -> Code.If(code.type, code.condNode, markTailCalls(code.thenNode), markTailCalls(code.elseNode))
    else -> code
  }
}
