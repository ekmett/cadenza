package cadenza.launcher

import cadenza.*

import org.graalvm.launcher.*
import org.graalvm.options.OptionCategory
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value

import java.io.File
import java.io.IOException
import java.nio.file.Paths
import java.util.*
import kotlin.io.*

class Launcher : AbstractLanguageLauncher() {
  internal var programArgs: Array<String> = emptyArray()
  private var versionAction: VersionAction = VersionAction.None
  internal var file: File? = null

  override fun getLanguageId(): String {
    return Language.ID
  }


  override fun launch(contextBuilder: Context.Builder) {
    System.exit(execute(contextBuilder))
  }

  protected fun execute(contextBuilder: Context.Builder): Int {
    contextBuilder.arguments(languageId, programArgs)
    try {
      contextBuilder.build().use { // context ->
          runVersionAction(versionAction, it.getEngine())
          val library = it.eval(Source.newBuilder(languageId, file).build())
          if (!library.canExecute()) throw abort("no main function found")
          return library.execute().asInt()
      }
    } catch (e: PolyglotException) {
      if (e.isExit) throw e
      if (e.isInternalError) throw e
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
          if (equalsIndex > 0) {
            argument = option.substring(equalsIndex + 1)
            optionName = option.substring(0, equalsIndex)
          } else if (iterator.hasNext())
            argument = iterator.next()
          else
            argument = null
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
    if (!path.isEmpty())
      polyglotOptions["cadenza.libraryPath"] = path.joinToString(":")

    if (!path.isEmpty())
      polyglotOptions["cadenza.libraries"] = libs.joinToString(":")

    if (file == null && iterator.hasNext())
      file = Paths.get(iterator.next()).toFile()

    val programArgumentsList = arguments.subList(iterator.nextIndex(), arguments.size)
    programArgs = programArgumentsList.toTypedArray<String>()
    return unrecognizedOptions
  }

  override fun validateArguments(_polyglotOptions: Map<String, String>?) {
    if (file == null && versionAction != VersionAction.PrintAndExit)
      throw abort("no file provided", 6)
  }

  override fun printHelp(_maxCategory: OptionCategory) {
    println()
    println("Usage: cadenza [OPTION]... [FILE] [PROGRAM ARGS]")
    println("Run cadenza programs on GraalVM\n")
    println("Mandatory arguments to long options are mandatory for short options too.\n")
    println("Options:")
    printOption("-L <path>", "set the path to search for cadenza libraries")
    printOption("--lib <library>", "add a library")
    printOption("--version", "print the version and exit")
    printOption("--show-version", "print the version and continue")
  }

  override fun collectArguments(args: MutableSet<String>) {
    args.addAll(Arrays.asList("-L", "--lib", "--version", "--show-version"))
  }

  override fun getDefaultLanguages(): Array<String> {
    return arrayOf(Language.ID) // "js","llvm",getLanguageId()};
  }

  companion object {

    @JvmStatic
    fun main(args: Array<String>) {
      println("main.start")
      Launcher().launch(arrayOf("Foo.za")) // args
    }

    protected fun printOption(option: String, description: String) {
      var option = option
      if (option.length >= 22) {
        println(String.format("%s%s", "  ", option))
        option = ""
      }
      println(String.format("  %-22s%s", option, description))
    }

    private fun printStackTraceSkipTrailingHost(e: PolyglotException) {
      val stackTrace = ArrayList<PolyglotException.StackFrame>()
      for (s in e.polyglotStackTrace)
        stackTrace.add(s)
      val iterator = stackTrace.listIterator(stackTrace.size)
      while (iterator.hasPrevious()) {
        val s = iterator.previous()
        if (s.isHostFrame)
          iterator.remove()
        else
          break
      }
      System.err.println(if (e.isHostException) e.asHostException().toString() else e.message)
      for (s in stackTrace) {
        System.err.println("\tat $s")
      }
    }
  }
}
