package cadenza;

import cadenza.nodes.ClosureRootNode;
import cadenza.nodes.Code;
import cadenza.nodes.FrameBuilder;
import cadenza.nodes.InlineCode;
import cadenza.types.Term;
import cadenza.types.Type;
import cadenza.values.Closure;
import cadenza.values.Int;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.debug.DebuggerTags;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags.*;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.source.SourceSection;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import org.graalvm.options.*;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.IntStream;

import static cadenza.nodes.Code.*;
import static cadenza.types.Type.arr;
import static cadenza.types.Type.nat;

@Option.Group("cadenza")
@TruffleLanguage.Registration(
  id = Language.ID,
  name = Language.NAME,
  version = Language.VERSION,
  defaultMimeType = Language.MIME_TYPE,
  characterMimeTypes = Language.MIME_TYPE,
  contextPolicy = ContextPolicy.SHARED,
  fileTypeDetectors = Detector.class
)
@ProvidedTags({
  CallTag.class,
  StatementTag.class,
  RootTag.class,
  RootBodyTag.class,
  ExpressionTag.class,
  DebuggerTags.AlwaysHalt.class
})
public class Language extends TruffleLanguage<Context> {
  public final static String ID = "cadenza";
  public final static String NAME = "Cadenza";
  public final static String VERSION = "0";
  public final static String MIME_TYPE = "application/x-cadenza";
  public final static String EXTENSION = "za";

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
  @Override public InlineCode parse(@SuppressWarnings("unused") InlineParsingRequest request) {
    System.out.println("parse0");
    var body = K(nat,nat);
    return InlineCode.create(this,body);
  }

  // stubbed: returns a calculation that adds two numbers
  @Override public CallTarget parse(@SuppressWarnings("unused") ParsingRequest request) {
    FrameDescriptor fd = new FrameDescriptor();
    FrameSlot[] argSlots = request.getArgumentNames().stream().map(fd::addFrameSlot).toArray(FrameSlot[]::new);
      FrameBuilder[] preamble = IntStream.range(0, argSlots.length).mapToObj(i -> put(argSlots[i], arg(i))).toArray(FrameBuilder[]::new);
      int arity = argSlots.length;
      Code content = null; // Expr.intLiteral(-42);
      var body = ClosureRootNode.create(this, arity, preamble, content);
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
    var interop = InteropLibrary.getFactory().getUncached(value);
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
      var interop = InteropLibrary.getFactory().getUncached(value);
      if (interop.fitsInLong(value)) return Long.toString(interop.asLong(value));
      if (interop.isBoolean(value)) return Boolean.toString(interop.asBoolean(value));
      if (interop.isString(value)) return interop.asString(value);
      if (interop.isNull(value)) return "NULL";
      if (interop.isExecutable(value)) return "Function";
      if (interop.hasMembers(value)) return "Object";
      return "Unsupported";
    } catch (UnsupportedMessageException e) {
      CompilerDirectives.transferToInterpreter();
      throw new SafeRuntimeException("unknown type");
    }
  }
  
  @Override public boolean patchContext(Context ctx, TruffleLanguage.Env env) {
    ctx.env = env;
    return true;
  }

  public Object findExportedSymbol(@SuppressWarnings("unused") org.graalvm.polyglot.Context context, String globalName, @SuppressWarnings("unused") boolean onlyExplicit) {
    switch (globalName) {
      case "S": return S(arr(nat,arr(nat,nat)),arr(nat,nat), nat);
      case "K": return K(nat,nat);
      case "I": return I(nat);
      case "main": return 42;
    }
    return null;
  }

  // for testing

  Code I(Type tx) { return unary(x -> x, tx); }
  Code K(Type tx, Type ty) { return binary((x, y) -> x, tx, ty); }
  Code S(Type _tx, Type _ty, Type _tz) {
    return null;
    /*
    FrameDescriptor fd = new FrameDescriptor();
    FrameSlot x = fd.addFrameSlot("x", tx, tx.rep);
    FrameSlot y = fd.addFrameSlot("y", ty, ty.rep);
    FrameSlot z = fd.addFrameSlot("z", tz, tz.rep);
    Expr.Var vx = var(x);
    Expr.Var vy = var(y);
    Expr.Var vz = var(z);
    Expr impl = app(vx, vz, app(vy, vz));
    Type result;
    try {
      result = impl.infer(fd);
    } catch (TypeError e) {
      throw new RuntimeException(e);
    }
    Closure.Root body = Closure.Root.create(
      this,
      3,
      new FrameBuilder[]{
        put(x,arg(0)),
        put(y,arg(1)),
        put(z,arg(2))
      },
      impl
    );
    return Expr.lam(3, Truffle.getRuntime().createCallTarget(body), arr(tx,arr(ty,arr(tz,result))));
     */
  }

  public Code unary(Function<Term, Term> _f, Type _argument) {
    return null; /*
    FrameDescriptor fd = new FrameDescriptor();
    FrameSlot x = fd.addFrameSlot("x", argument, argument.rep);

    Term impl = f.apply(tvar(x));
    try {
      Witness result = impl.infer(fd);

    }

    Type result;
    try {
      result = impl.infer(fd);
    } catch (TypeError e) {
      throw new RuntimeException(e);
    }
    Closure.Root body = Closure.Root.create(
      this,
      1,
      new FrameBuilder[]{put(x,arg(0)),},
      impl
    );
    RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(body);
    return Expr.lam(1, callTarget, arr(argument,result));
    */
  }
  // construct a binary function
  public Code binary(BiFunction<Code, Code, Code> _f, Type _tx, Type _ty) {
    return null;
    /*
    FrameDescriptor fd = new FrameDescriptor();
    FrameSlot x = fd.addFrameSlot("x", tx, tx.rep);
    FrameSlot y = fd.addFrameSlot("y", ty, ty.rep);
    Expr impl = f.apply(var(x),var(y));
    Type result;
    try {
      result = impl.infer(fd);
    } catch (TypeError e) {
      throw new RuntimeException(e);
    }
    Closure.Root body = Closure.Root.create(
      this,
      2,
      new FrameBuilder[]{put(x,arg(0)),put(y,arg(1))},
      impl
    );
    RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(body);

    return Expr.lam(2, callTarget, arr(tx, arr(ty, result)));
     */
  }

}
