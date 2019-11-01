package cadenza

import org.fusesource.jansi.Ansi
import org.fusesource.jansi.Ansi.*
import org.fusesource.jansi.AnsiConsole
import org.graalvm.launcher.*
import org.graalvm.options.OptionCategory
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Source

import java.io.File
import java.io.IOException
import java.nio.file.Paths
import java.util.*
import kotlin.io.*
import kotlin.system.exitProcess


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
internal fun PolyglotException.prettyStackTrace(trim: Boolean = true) {
  val stackTrace = ArrayList<PolyglotException.StackFrame>()
  for (s in polyglotStackTrace)
    stackTrace.add(s)
  if (trim) {
    val iterator = stackTrace.listIterator(stackTrace.size)
    while (iterator.hasPrevious()) {
      if (iterator.previous().isHostFrame) iterator.remove()
      else break
    }
  }
  val out = ansi()
  if (isHostException) out.fgRed().a(asHostException().toString())
  else out.fgBrightYellow().a(message)
  out.reset().a('\n').fgBrightBlack()
  for (s in stackTrace) {
    out.a(Attribute.ITALIC).a("  at ").a(Attribute.ITALIC_OFF).a(s).a('\n')
  }
  out.reset()
  println(out.toString())
}


class Launcher : AbstractLanguageLauncher() {
  private var programArgs: Array<String> = emptyArray()
  private var versionAction: VersionAction = VersionAction.None
  private var file: File? = null
  private var use_ansi: Boolean? = null

  override fun getLanguageId() = LANGUAGE_ID

  override fun launch(contextBuilder: Context.Builder) = exitProcess(execute(contextBuilder))

  private fun execute(contextBuilder: Context.Builder): Int =
    withAnsi(use_ansi) { executeWithAnsi(contextBuilder) }

  private fun executeWithAnsi(contextBuilder: Context.Builder): Int {
    contextBuilder.arguments(languageId, programArgs)
    try {
      contextBuilder.build().use { ctx ->
        runVersionAction(versionAction, ctx.engine)
        val v = ctx.eval(Source.newBuilder(languageId, file).build())
        return if (v.canExecute()) v.execute().asInt()
        else v.asInt()
      }
    } catch (e: PolyglotException) {
      if (e.isExit) return e.exitStatus
      e.prettyStackTrace(!e.isInternalError)
      return -1
    } catch (e: IOException) {
      println(
        ansi().a("Error loading file ")
        .bold().fgBrightBlack().a("'").reset().bold().a(file).boldOff().fgBrightBlack().a("'")
        .fgBrightBlack().a(" (").fgRed().a(e.message).fgBrightBlack().a(")").toString()
      )
      return -1
    }
  }

  override fun preprocessArguments(arguments: MutableList<String>, polyglotOptions: MutableMap<String, String>): List<String> {
    val unrecognizedOptions = ArrayList<String>()
    val path = ArrayList<String>()
    val libs = ArrayList<String>()
    val iterator = arguments.listIterator()
    while (iterator.hasNext()) {
      val option = iterator.next()
      if (option.length < 2 || !option.startsWith("-")) {
        iterator.previous()
        break
      }
      // Ignore fall through
      when (option) {
        "--" -> { }
        "--show-version" -> versionAction = VersionAction.PrintAndContinue
        "--color" -> use_ansi = true
        "--no-color" -> use_ansi = false
        "--version" -> versionAction = VersionAction.PrintAndExit
        else -> {
          var optionName = option
          val argument: String?
          val equalsIndex = option.indexOf('=')
          when {
            equalsIndex > 0 -> {
              argument = option.substring(equalsIndex + 1)
              optionName = option.substring(0, equalsIndex)
            }
            iterator.hasNext() -> argument = iterator.next()
            else -> argument = null
          }
          when (optionName) {
            "-L" -> {
              if (argument == null) throw abort("missing argument for $optionName")
              path.add(argument)
              iterator.remove()
              if (equalsIndex < 0) {
                iterator.previous()
                iterator.remove()
              }
            }
            "--lib" -> {
              if (argument == null) throw abort("missing argument for $optionName")
              libs.add(argument)
              iterator.remove()
              if (equalsIndex < 0) {
                iterator.previous()
                iterator.remove()
              }
            }
            else -> {
              unrecognizedOptions.add(option)
              if (equalsIndex < 0 && argument != null) iterator.previous()
            }
          }
        }
      }
    }
    if (path.isNotEmpty())
      polyglotOptions["cadenza.libraryPath"] = path.joinToString(":")

    if (path.isNotEmpty())
      polyglotOptions["cadenza.libraries"] = libs.joinToString(":")

    if (file == null && iterator.hasNext())
      file = Paths.get(iterator.next()).toFile()

    val programArgumentsList = arguments.subList(iterator.nextIndex(), arguments.size)
    programArgs = programArgumentsList.toTypedArray()
    return unrecognizedOptions
  }

  override fun validateArguments(_polyglotOptions: Map<String, String>?) {
    if (file == null && versionAction != VersionAction.PrintAndExit)
      throw abort("no file provided", 6)
  }

  internal fun Ansi.ansiOption(option: String, description: String) {
    var o = option
    if (option.length >= 22) {
      format("%s%s\n", "  ", o)
      o = ""
    }
    format("  %-22s%s\n", o, description)
  }

  override fun printHelp(_maxCategory: OptionCategory) {
    Ansi.ansi().run {
      newline()
      render("Usage: @|italic,blue cadenza|@ @|bold [OPTION]|@... @|bold [FILE]|@ @|bold [PROGRAM ARGS]|@\n\n")
      a("Run cadenza programs on GraalVM\n\n")
      a("Mandatory arguments to long options are mandatory for short options too.\n\n")
      a("Options:\n")
      ansiOption("-L <path>", "set the path to search for cadenza libraries")
      ansiOption("--lib <library>", "add a library")
      ansiOption("--version", "print the version and exit")
      ansiOption("--show-version", "print the version and continue")
      println(toString())
    }
  }

  override fun collectArguments(args: MutableSet<String>) {
    args.addAll(listOf("-L", "--lib", "--version", "--show-version"))
  }

  override fun getDefaultLanguages(): Array<String> = arrayOf(LANGUAGE_ID) // "js","llvm",getLanguageId()};

  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      Launcher().launch(if (args.isEmpty()) arrayOf("main.za") else args)
    }
  }
}


