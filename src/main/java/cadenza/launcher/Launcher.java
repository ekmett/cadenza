package cadenza.launcher;

import cadenza.*;

import org.graalvm.launcher.*;
import org.graalvm.options.OptionCategory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

public class Launcher extends AbstractLanguageLauncher {
  String[] programArgs;
  private VersionAction versionAction = VersionAction.None;
  File file;

  public static void main(String[] args) {
    new Launcher().launch(args);
  }

  @Override
  protected String getLanguageId() { return Language.ID; }


  @Override protected void launch(Context.Builder contextBuilder) {
    System.exit(execute(contextBuilder));
  }

  protected int execute(Context.Builder contextBuilder) {
    contextBuilder.arguments(getLanguageId(), programArgs);
    try (Context context = contextBuilder.build()) {
      runVersionAction(versionAction, context.getEngine());
      Value library = context.eval(Source.newBuilder(getLanguageId(), file).build());
      if (!library.canExecute()) throw abort("no main function found");
      return library.execute().asInt();
    } catch (PolyglotException e) {
      if (e.isExit()) throw e;
      if (e.isInternalError()) throw e;
      printStackTraceSkipTrailingHost(e);
      return -1;
    } catch (IOException e) {
      throw abort(String.format("Error loading file '%s' (%s)", file, e.getMessage()));
    }
  }

  @Override protected List<String> preprocessArguments(List<String> arguments, Map<String, String> polyglotOptions) {
    List<String> unrecognizedOptions = new ArrayList<>();
    List<String> path = new ArrayList<>();
    List<String> libs = new ArrayList<>();
    ListIterator<String> iterator = arguments.listIterator();
    while (iterator.hasNext()) {
      String option = iterator.next();
      if (option.length() < 2 || !option.startsWith("-")) {
        iterator.previous();
        break;
      }
      // Ignore fall through
      switch (option) {
        case "--":
          break;
        case "--show-version":
          versionAction = VersionAction.PrintAndContinue;
          break;
        case "--version":
          versionAction = VersionAction.PrintAndExit;
          break;
        default:
          String optionName = option;
          String argument;
          int equalsIndex = option.indexOf('=');
          if (equalsIndex > 0) {
            argument = option.substring(equalsIndex + 1);
            optionName = option.substring(0, equalsIndex);
          } else if (iterator.hasNext())
            argument = iterator.next();
          else argument = null;
          switch (optionName) {
            case "-L":
              if (argument == null) throw abort("missing argument for " + optionName);
              path.add(argument);
              iterator.remove();
              if (equalsIndex < 0) {
                iterator.previous();
                iterator.remove();
              }
              break;
            case "--lib":
              if (argument == null) throw abort("missing argument for " + optionName);
              libs.add(argument);
              iterator.remove();
              if (equalsIndex < 0) {
                iterator.previous();
                iterator.remove();
              }
              break;
            default:
              unrecognizedOptions.add(option);
              if (equalsIndex < 0 && argument != null) iterator.previous();
              break;
          }
          break;
      }
    }
    if (!path.isEmpty())
      polyglotOptions.put("core.libraryPath", String.join(":", path));

    if (!path.isEmpty())
      polyglotOptions.put("core.libraries", String.join(":", libs));

    if (file == null && iterator.hasNext())
      file = Paths.get(iterator.next()).toFile();

    List<String> programArgumentsList = arguments.subList(iterator.nextIndex(), arguments.size());
    programArgs = programArgumentsList.toArray(new String[0]);
    return unrecognizedOptions;
  }

  @Override protected void validateArguments(Map<String, String> polyglotOptions) {
    if (file == null && versionAction != VersionAction.PrintAndExit)
      throw abort("no file provided", 6);
  }

  @Override protected void printHelp(OptionCategory maxCategory) {
    System.out.println();
    System.out.println("Usage: core [OPTION]... [FILE] [PROGRAM ARGS]");
    System.out.println("Run core programs on GraalVM\n");
    System.out.println("Mandatory arguments to long options are mandatory for short options too.\n");
    System.out.println("Options:");
    printOption("-L <path>", "set the path to search for core libraries");
    printOption("--lib <library>", "add a library");
    printOption("--version", "print the version and exit");
    printOption("--show-version", "print the version and continue");
  }

  @Override protected void collectArguments(Set<String> args) {
    args.addAll(Arrays.asList("-L","--lib","--version","--show-version"));
  }

  protected static void printOption(String option, String description) {
    if (option.length() >= 22) {
      System.out.println(String.format("%s%s", "  ", option));
      option = "";
    }
    System.out.println(String.format("  %-22s%s", option, description));
  }

  private static void printStackTraceSkipTrailingHost(PolyglotException e) {
    List<PolyglotException.StackFrame> stackTrace = new ArrayList<>();
    for (PolyglotException.StackFrame s : e.getPolyglotStackTrace())
      stackTrace.add(s);
    for (ListIterator<PolyglotException.StackFrame> iterator = stackTrace.listIterator(stackTrace.size()); iterator.hasPrevious();) {
      PolyglotException.StackFrame s = iterator.previous();
      if (s.isHostFrame()) iterator.remove();
      else break;
    }
    System.err.println(e.isHostException() ? e.asHostException().toString() : e.getMessage());
    for (PolyglotException.StackFrame s : stackTrace) {
      System.err.println("\tat " + s);
    }
  }

  @Override protected String[] getDefaultLanguages() {
    return new String[]{Language.ID}; // "js","llvm",getLanguageId()};
  }
}
