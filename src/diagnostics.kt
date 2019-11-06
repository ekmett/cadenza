package org.intelligence.diagnostics

import com.oracle.truffle.api.source.Source
import org.fusesource.jansi.Ansi
import org.intelligence.pretty.*

enum class Severity { info, warning, error, todo, panic }

fun Pretty.error(severity: Severity = Severity.error, file: String, line: Int, col: Int? = null, sourceLine: CharSequence? = null, message: String? = null, vararg expected: Any) {
  bold {
    text(file); char(':'); simple(line); char(':');
    col?.also { simple(it); char(':') };
    space
    when (severity) {
      Severity.error -> red { text("error:") }
      Severity.panic -> fg(Ansi.Color.RED, true) { text("panic:") }
      Severity.warning -> magenta { text("warning:") }
      Severity.info -> blue { text("info:") }
      Severity.todo -> yellow { text("todo:")}
    }
    space
    nest(2) {
      if (expected.isEmpty()) text(message ?: "expected nothing")
      else {
        if (message != null) {
          text(message);text(",");space
        }
        text("expected")
        space
        oxfordBy(by = Pretty::simple, conjunction = "or", docs = *expected)
      }
    }
  }
  sourceLine?.also {
    hardLine
    text(it)
    col?.also { nest(it - 1) { newline; cyan { char('^') } } }
  }
}

// convenient pretty printer
fun Pretty.error(severity: Severity = Severity.error, source: Source, pos: Int, message: String? = null, vararg expected: Any) {
  val line = source.getLineNumber(pos)
  val ls = source.getLineStartOffset(line)
  val ll = source.getLineLength(line)
  val sourceText = source.characters.subSequence(ls, ls+ll)
  error(severity, source.name, line, source.getColumnNumber(pos), sourceText, message, *expected)
}