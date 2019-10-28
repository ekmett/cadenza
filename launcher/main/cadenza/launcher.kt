package cadenza

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

class Launcher : AbstractLanguageLauncher() {
  private var programArgs: Array<String> = emptyArray()
  private var versionAction: VersionAction = VersionAction.None
  private var file: File? = null

  override fun getLanguageId() = LANGUAGE_ID

  override fun launch(contextBuilder: Context.Builder) =
    exitProcess(execute(contextBuilder))

  private fun execute(contextBuilder: Context.Builder): Int {
    contextBuilder.arguments(languageId, programArgs)
    try {
      contextBuilder.build().use { ctx ->
        runVersionAction(versionAction, ctx.engine)
        val v = ctx.eval(Source.newBuilder(languageId, file).build())
        return if (v.canExecute()) v.execute().asInt() else v.asInt()
      }
    } catch (e: PolyglotException) {
      if (e.isExit || e.isInternalError) throw e
      printStackTraceSkipTrailingHost(e)
      return -1
    } catch (e: IOException) {
      throw abort(String.format("Error loading file '%s' (%s)", file, e.message))
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
        "--" -> {
        }
        "--show-version" -> versionAction = VersionAction.PrintAndContinue
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

  override fun printHelp(_maxCategory: OptionCategory) {
    println()
    println("Usage: cadenza [OPTION]... [FILE] [PROGRAM ARGS]\n")
    println("Run cadenza programs on GraalVM\n")
    println("Mandatory arguments to long options are mandatory for short options too.\n")
    println("Options:")
    printOption("-L <path>", "set the path to search for cadenza libraries")
    printOption("--lib <library>", "add a library")
    printOption("--version", "print the version and exit")
    printOption("--show-version", "print the version and continue")
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

internal fun printOption(option: String, description: String) {
  var o = option
  if (option.length >= 22) {
    println(String.format("%s%s", "  ", o))
    o = ""
  }
  println(String.format("  %-22s%s", o, description))
}

internal fun printStackTraceSkipTrailingHost(e: PolyglotException) {
  val stackTrace = ArrayList<PolyglotException.StackFrame>()
  for (s in e.polyglotStackTrace)
    stackTrace.add(s)
  val iterator = stackTrace.listIterator(stackTrace.size)
  while (iterator.hasPrevious()) {
    if (iterator.previous().isHostFrame)
      iterator.remove()
    else
      break
  }
  System.err.println(if (e.isHostException) e.asHostException().toString() else e.message)
  for (s in stackTrace) {
    System.err.println("\tat $s")
  }
}