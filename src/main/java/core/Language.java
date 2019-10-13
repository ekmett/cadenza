package core;

import com.oracle.truffle.api.dsl.ImplicitCast;
import com.oracle.truffle.api.dsl.TypeSystem;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.nodes.NodeInfo;
import core.nodes.*;
import core.values.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.TruffleLanguage.*;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.source.SourceSection;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;

import java.nio.charset.Charset;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.polyglot.Source;

@SuppressWarnings("SuspiciousNameCombination")
@Option.Group("core")
@TruffleLanguage.Registration(
  id = Language.ID,
  name = Language.NAME,
  version = Language.VERSION,
  defaultMimeType = Language.MIME_TYPE,
  characterMimeTypes = Language.MIME_TYPE,
  contextPolicy = ContextPolicy.SHARED,
  fileTypeDetectors = Language.Detector.class
)
@ProvidedTags(value={})
public class Language extends TruffleLanguage<Language.Context> {
  public final static String ID = "core";
  public final static String NAME = "Core";
  public final static String VERSION = "0";
  public final static String MIME_TYPE = "application/x-core";
  public final static String EXTENSION = "core";

  public static final OptionDescriptors OPTION_DESCRIPTORS = new LanguageOptionDescriptors();

  @Option(name="tco", help = "Tail-call optimization", category = OptionCategory.USER, stability = OptionStability.EXPERIMENTAL)
  public static final OptionKey<Boolean> TAIL_CALL_OPTIMIZATION = new OptionKey<>(false);

  public Language() {}

  public final Assumption singleContextAssumption = Truffle.getRuntime().createAssumption("Only a single context is active");
  
  @Override public Context createContext(Env env) {
    return new Context(this, env);
  } // cheap and easy
  
  @Override public void initializeContext(@SuppressWarnings("unused") Context ctx) {} // TODO: any expensive init here, like stdlib loading
  
  @Override public void finalizeContext(Context ctx) {
    ctx.shutdown(); 
  } // TODO: any expensive shutdown here

  // stubbed: for now inline parsing requests just return 'const'
  @Override public CoreNode.Executable parse(@SuppressWarnings("unused") InlineParsingRequest request) {
    System.out.println("parse0");
    Expr body = K();
    return CoreNode.Executable.create(this,body);
  }

  // stubbed: returns a calculation that adds two numbers
  @Override public CallTarget parse(@SuppressWarnings("unused") ParsingRequest request) {
    FrameDescriptor fd = new FrameDescriptor();
    FrameSlot[] argSlots = request.getArgumentNames().stream().map(fd::addFrameSlot).toArray(FrameSlot[]::new);
      FrameBuilder[] preamble = IntStream.range(0, argSlots.length).mapToObj(i -> put(argSlots[i], arg(i))).toArray(FrameBuilder[]::new);
      int arity = argSlots.length;
      Expr content = Expr.add(Expr.intLiteral(-42), Expr.bigLiteral(new Int(42)));
      Closure.Root body = Closure.Root.create(this, arity, preamble, content);
      return Truffle.getRuntime().createCallTarget(body);
  //  }
  }

  @Override public boolean isObjectOfLanguage(Object obj) {
    if (!(obj instanceof TruffleObject)) return false;
    return (obj instanceof Int) || (obj instanceof Closure);
  }

  @Override public void initializeMultipleContexts() {
    singleContextAssumption.invalidate(); 
  }

  @Override public boolean areOptionsCompatible(@SuppressWarnings("unused") OptionValues a, @SuppressWarnings("unused") OptionValues b) {
    return true; 
  } // no options!

  @Override
  protected OptionDescriptors getOptionDescriptors() { return Language.OPTION_DESCRIPTORS; }

  @Override public void initializeMultiThreading(Context ctx) {
    ctx.singleThreadedAssumption.invalidate(); 
  }
  
  @Override public boolean isThreadAccessAllowed(@SuppressWarnings("unused") Thread thread, @SuppressWarnings("unused") boolean singleThreaded) {
    return true; 
  }

  @Override public void initializeThread(@SuppressWarnings("unused") Context ctx, @SuppressWarnings("unused") Thread thread) {}

  @Override public void disposeThread(@SuppressWarnings("unused") Context ctx, @SuppressWarnings("unused") Thread thread) {}
  
  // TODO: return types?
  @Override public Object findMetaObject(@SuppressWarnings("unused") Context ctx, Object value) {
    return getMetaObject(value);
  }

  public static String getMetaObject(Object value) {
    if (value == null) return "ANY";
    InteropLibrary interop = InteropLibrary.getFactory().getUncached(value);
    if (interop.isNumber(value) || value instanceof Number) return "Number";
    if (interop.isBoolean(value)) return "Boolean";
    if (interop.isString(value)) return "String";
    if (interop.isNull(value)) return "NULL" ;
    if (interop.isExecutable(value)) return "Function";
    if (interop.hasMembers(value)) return "Object";
    return "Unsupported";
  }
  
  @Override public SourceSection findSourceLocation(@SuppressWarnings("unused") Context ctx, @SuppressWarnings("unused") Object value) {
    return null; 
  }
  
  @Override public boolean isVisible(@SuppressWarnings("unused") Context ctx, @SuppressWarnings("unused") Object value) {
    return true; 
  }
  
  @Override public String toString(@SuppressWarnings("unused") Context ctx, Object value) {
    return toString(value); 
  }


  public static String toString(Object value) {
    try {
      if (value == null) return "null";
      if (value instanceof Number) return value.toString();
      if (value instanceof Closure) return value.toString();
      InteropLibrary interop = InteropLibrary.getFactory().getUncached(value);
      if (interop.fitsInLong(value)) return Long.toString(interop.asLong(value));
      if (interop.isBoolean(value)) return Boolean.toString(interop.asBoolean(value));
      if (interop.isString(value)) return interop.asString(value);
      if (interop.isNull(value)) return "NULL";
      if (interop.isExecutable(value)) return "Function";
      if (interop.hasMembers(value)) return "Object";
      return "Unsupported";
    } catch (UnsupportedMessageException e) {
      CompilerDirectives.transferToInterpreter();
      throw new AssertionError();
    }
  }
  
  @Override public boolean patchContext(Context ctx, TruffleLanguage.Env env) {
    ctx.env = env;
    return true;
  }

  public Object findExportedSymbol(@SuppressWarnings("unused") org.graalvm.polyglot.Context context, String globalName, @SuppressWarnings("unused") boolean onlyExplicit) {
    switch (globalName) {
      case "S": return S();
      case "K": return K();
      case "I": return I();
      case "main": return 42;
      default: return null;
    }
  }


  // build a convenient edsl
  static Expr.App app(Expr x, Expr... xs) { return Expr.app(x,xs); }
  static Expr.Arg arg(int i) { return Expr.arg(i); }
  static Expr.Var var(FrameSlot x) { return Expr.var(x); }
  static FrameBuilder put(FrameSlot x, Expr v) { return Expr.put(x,v); }

  // for testing
  Expr K() { return J(0,2); }
  Expr I() { return J(0,1); }

  // construct the identify function by hand
  // note we only copy one thing out of the surrounding frame
  Expr J(@SuppressWarnings("SameParameterValue") final int i, final int j) {
    FrameDescriptor fd = new FrameDescriptor();
    FrameSlot x = fd.addFrameSlot("x");
    Closure.Root body = Closure.Root.create(this, j, new FrameBuilder[]{put(x,arg(i))},var(x));
    RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(body);
    return Expr.lam(j, callTarget);
  }

  Expr S() {
    FrameDescriptor fd = new FrameDescriptor();
    FrameSlot x = fd.addFrameSlot("x");
    FrameSlot y = fd.addFrameSlot("y");
    FrameSlot z = fd.addFrameSlot("z");
    Expr.Var vx = var(x);
    Expr.Var vy = var(y);
    Expr.Var vz = var(z);
    Closure.Root body = Closure.Root.create(
      this,
      3,
      new FrameBuilder[]{
        put(x,arg(0)),
        put(y,arg(1)),
        put(z,arg(2))
      },
      app(vx, vz, app(vy, vz))
    );
    return Expr.lam(3, Truffle.getRuntime().createCallTarget(body));
  }

  public Expr unary(Function<Expr, Expr> f) {
    FrameDescriptor fd = new FrameDescriptor();
    FrameSlot x = fd.addFrameSlot("x");
    Closure.Root body = Closure.Root.create(
      this,
      1,
      new FrameBuilder[]{put(x,arg(0)),},
      f.apply(var(x))
    );
    RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(body);
    return Expr.lam(1, callTarget);
  }
  // construct a binary function
  public Expr binary(BiFunction<Expr, Expr, Expr> f) {
    FrameDescriptor fd = new FrameDescriptor();
    FrameSlot x = fd.addFrameSlot("x");
    FrameSlot y = fd.addFrameSlot("y");
    Closure.Root body = Closure.Root.create(
      this,
      1,
      new FrameBuilder[]{put(x,arg(0)),put(y,arg(1))},
      f.apply(var(x),var(y))
    );
    RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(body);
    return Expr.lam(2, callTarget);
  }

  public static final class Context {
    private static final Source BUILTIN_SOURCE = Source.newBuilder(ID, "", "[core builtin]").buildLiteral();

    public Context(Language language, Env env) {
      this.language = language;
      this.env = env;
    }
    public final Language language;
    public Env env;

    public final Assumption singleThreadedAssumption = Truffle.getRuntime().createAssumption("context is single threaded");

    public void shutdown() { }

    public static NodeInfo lookupNodeInfo(Class<?> clazz) {
      if (clazz == null) return null;
      NodeInfo info = clazz.getAnnotation(NodeInfo.class);
      if (info != null) return info;
      return lookupNodeInfo(clazz.getSuperclass());
    }
  }

  public static class Detector implements TruffleFile.FileTypeDetector {
    @Override public String findMimeType(TruffleFile file) {
      String name = file.getName();
      if (name != null && name.endsWith(EXTENSION)) return MIME_TYPE;
      return null;
    }

    @Override public Charset findEncoding(TruffleFile file) {
      return null;
    }
  }

  @TypeSystem({
    Closure.class,
    boolean.class,
    int.class,
    Int.class
  })
  public static abstract class Types {
    @ImplicitCast
    @CompilerDirectives.TruffleBoundary
    public static Int castBigNumber(int value) { return new Int(value); }
  }
}