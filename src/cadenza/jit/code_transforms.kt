package cadenza.jit

fun markTailCalls(code: Code): Code {
  return when (code) {
    is Code.App -> Code.App(code.rator, code.rands, code.loc, true)
    is Code.If -> Code.If(code.type, code.condNode, markTailCalls(code.thenNode), markTailCalls(code.elseNode), code.loc)
    is Code.LetRec -> Code.LetRec(code.slot, code.type, code.value, markTailCalls(code.body), code.loc)
    else -> code
  }
}
