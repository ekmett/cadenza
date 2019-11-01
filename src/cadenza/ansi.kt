package cadenza

import org.fusesource.jansi.Ansi
import org.fusesource.jansi.AnsiConsole
import org.fusesource.jansi.internal.CLibrary.*

fun <A> withAnsi(use_ansi: Boolean? = null, f: () -> A): A {
  AnsiConsole.systemInstall()
  when (use_ansi) {
    true -> Ansi.setEnabled(true)
    false -> Ansi.setEnabled(false)
    else -> {}
  }
  return try {
    f()
  } finally {
    AnsiConsole.systemUninstall()
  }
}