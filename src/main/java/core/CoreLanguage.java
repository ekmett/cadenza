package core;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import core.node.CoreExecutableNode;
import core.node.CoreRootNode;
import core.node.FrameBuilder;
import core.node.FunctionBody;
import core.node.expr.*;
import core.values.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.TruffleLanguage.*;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.source.SourceSection;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Context;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@TruffleLanguage.Registration(
  id = CoreLanguage.ID,
  name = CoreLanguage.NAME,
  version = CoreLanguage.VERSION,
  defaultMimeType = CoreLanguage.MIME_TYPE,
  characterMimeTypes = CoreLanguage.MIME_TYPE,
  contextPolicy = ContextPolicy.SHARED,
  fileTypeDetectors = Detector.class
)
@ProvidedTags(value={})
public class CoreLanguage extends TruffleLanguage<CoreContext> {
  public final static String ID = "core";
  public final static String NAME = "Core";
  public final static String VERSION = "0";
  public final static String MIME_TYPE = "application/x-core";
  public final static String EXTENSION = "core";

  public CoreLanguage() {}

  public final Assumption singleContextAssumption = Truffle.getRuntime().createAssumption("Only a single context is active");
  
  @Override public CoreContext createContext(Env env) {
    return new CoreContext(this, env);
  } // cheap and easy
  
  @Override public void initializeContext(@SuppressWarnings("unused") CoreContext ctx) {} // TODO: any expensive init here, like stdlib loading
  
  @Override public void finalizeContext(CoreContext ctx) {
    ctx.shutdown(); 
  } // TODO: any expensive shutdown here
  
  @Override public CoreExecutableNode parse(@SuppressWarnings("unused") InlineParsingRequest request) {
    System.out.println("parse0");
    Expression body = K();
    return CoreExecutableNode.create(this,body);
  }

  @Override public CallTarget parse(@SuppressWarnings("unused") ParsingRequest request) {
    FrameDescriptor fd = new FrameDescriptor();
    FrameSlot[] argSlots = request.getArgumentNames().stream().map(x -> fd.addFrameSlot(x)).toArray(n -> new FrameSlot[n]);
    //if (argSlots.length == 0) {
//      Expression content = Expressions.booleanLiteral(false); // no arguments -- maybe this should build a sequence of statements?
//      // we then need to wrap all of this up in a CoreRootNode
//      CoreRootNode root = CoreRootNode.create(this, content, fd);
//      return Truffle.getRuntime().createCallTarget(root);
//    } else {
      // we're building a function and the outer lambda is described by us
    // if we're asked for no arguments, we return a 0-closure.
      FrameBuilder[] preamble = IntStream.range(0, argSlots.length).mapToObj(i -> put(argSlots[i], arg(i))).toArray(n -> new FrameBuilder[n]);
      int arity = argSlots.length;
      // now we need to parse the body
      Expression content = Expressions.booleanLiteral(true); // do something better here
      FunctionBody body = FunctionBody.create(this, arity, preamble, content);
      return Truffle.getRuntime().createCallTarget(body);
  //  }
  }

  @Override public boolean isObjectOfLanguage(Object obj) {
    if (!(obj instanceof TruffleObject)) return false;
    return (obj instanceof BigNumber) || (obj instanceof Closure);
  }

  @Override public void initializeMultipleContexts() {
    singleContextAssumption.invalidate(); 
  }

  @Override public boolean areOptionsCompatible(@SuppressWarnings("unused") OptionValues a, @SuppressWarnings("unused") OptionValues b) {
    return true; 
  } // no options!

  @Override
  protected OptionDescriptors getOptionDescriptors() { return Options.DESCRIPTORS; }

  @Override public void initializeMultiThreading(CoreContext ctx) {
    ctx.singleThreadedAssumption.invalidate(); 
  }
  
  @Override public boolean isThreadAccessAllowed(@SuppressWarnings("unused") Thread thread, @SuppressWarnings("unused") boolean singleThreaded) {
    return true; 
  }

  @Override public void initializeThread(@SuppressWarnings("unused") CoreContext ctx, @SuppressWarnings("unused") Thread thread) {}

  @Override public void disposeThread(@SuppressWarnings("unused") CoreContext ctx, @SuppressWarnings("unused") Thread thread) {}
  
  // TODO: return types?
  @Override public Object findMetaObject(@SuppressWarnings("unused") CoreContext ctx, Object value) {
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
  
  @Override public SourceSection findSourceLocation(@SuppressWarnings("unused") CoreContext ctx, @SuppressWarnings("unused") Object value) {
    return null; 
  }
  
  @Override public boolean isVisible(@SuppressWarnings("unused") CoreContext ctx, @SuppressWarnings("unused") Object value) {
    return true; 
  }
  
  @Override public String toString(@SuppressWarnings("unused") CoreContext ctx, Object value) {
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
  
  @Override public boolean patchContext(CoreContext ctx, TruffleLanguage.Env env) {
    ctx.env = env;
    return true;
  }

  public Object findExportedSymbol(Context context, String globalName, boolean onlyExplicit) {
    switch (globalName) {
      case "S": return S();
      case "K": return K();
      case "I": return I();
      case "main": return 42;
      default: return null;
    }
  }


  // build a convenient edsl
  static App app(Expression x, Expression... xs) { return Expressions.app(x,xs); }
  static Arg arg(int i) { return Expressions.arg(i); }
  static Var var(FrameSlot x) { return Expressions.var(x); }
  static FrameBuilder put(FrameSlot x, Expression v) { return Expressions.put(x,v); }

  // for testing
  Expression K() { return J(0,2); }
  Expression I() { return J(0,1); }

  // construct the identify function by hand
  // note we only copy one thing out of the surrounding frame
  Expression J(final int i, final int j) {
    FrameDescriptor fd = new FrameDescriptor();
    FrameSlot x = fd.addFrameSlot("x");
    FunctionBody body = FunctionBody.create(this, j, new FrameBuilder[]{put(x,arg(i))},var(x));
    RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(body);
    return Expressions.lam(j, callTarget);
  }

  Expression S() {
    FrameDescriptor fd = new FrameDescriptor();
    FrameSlot x = fd.addFrameSlot("x");
    FrameSlot y = fd.addFrameSlot("y");
    FrameSlot z = fd.addFrameSlot("z");
    Var vx = var(x);
    Var vy = var(y);
    Var vz = var(z);
    FunctionBody body = FunctionBody.create(
      this,
      3,
      new FrameBuilder[]{
        put(x,arg(0)),
        put(y,arg(1)),
        put(z,arg(2))
      },
      app(vx, vz, app(vy, vz))
    );
    return Expressions.lam(3, Truffle.getRuntime().createCallTarget(body));
  }

  public Expression unary(Function<Expression,Expression> f) {
    FrameDescriptor fd = new FrameDescriptor();
    FrameSlot x = fd.addFrameSlot("x");
    FunctionBody body = FunctionBody.create(
      this,
      1,
      new FrameBuilder[]{put(x,arg(0)),},
      f.apply(var(x))
    );
    RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(body);
    return Expressions.lam(1, callTarget);
  }
  // construct a binary function
  public Expression binary(BiFunction<Expression,Expression,Expression> f) {
    FrameDescriptor fd = new FrameDescriptor();
    FrameSlot x = fd.addFrameSlot("x");
    FrameSlot y = fd.addFrameSlot("y");
    FunctionBody body = FunctionBody.create(
      this,
      1,
      new FrameBuilder[]{put(x,arg(0)),put(y,arg(1))},
      f.apply(var(x),var(y))
    );
    RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(body);
    return Expressions.lam(2, callTarget);
  }


  // testing
//
//  // manufacture a node
//  TailExpression I() {
//    return Expressions.lam(Truffle.getRuntime().createCallTarget(CoreRootNode.create(this, new ArgExpression(0))));
//  }
//
//  // manufacture a node, notice no arity, todo: fix arity
//  TailExpression K() {
//    return Expressions.lam(Truffle.getRuntime().createCallTarget(CoreRootNode.create(this, new ArgExpression(0))));
//  }

}