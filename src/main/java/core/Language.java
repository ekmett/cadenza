package core;

import com.oracle.truffle.api.frame.FrameDescriptor;
import core.node.CoreExecutableNode;
import core.node.CoreRootNode;
import core.node.expr.*;
import core.values.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.TruffleLanguage.*;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.source.SourceSection;
import org.graalvm.options.OptionValues;

@TruffleLanguage.Registration(
  id = Language.ID,
  name = Language.NAME,
  version = Language.VERSION,
  defaultMimeType = Language.MIME_TYPE,
  characterMimeTypes = Language.MIME_TYPE,
  contextPolicy = ContextPolicy.SHARED,
  fileTypeDetectors = Detector.class
)
public class Language extends TruffleLanguage<Context> {

  public final static String ID = "core";
  public final static String NAME = "Core";
  public final static String VERSION = "0";
  public final static String MIME_TYPE = "application/x-core";
  public final static String EXTENSION = "core";

  public Language() {}

  public final Assumption singleContextAssumption = Truffle.getRuntime().createAssumption("Only a single context is active");
  
  @Override public Context createContext(Env env) {
    return new Context(this, env);
  } // cheap and easy
  
  @Override public void initializeContext(@SuppressWarnings("unused") Context ctx) {} // TODO: any expensive init here, like stdlib loading
  
  @Override public void finalizeContext(Context ctx) {
    ctx.shutdown(); 
  } // TODO: any expensive shutdown here
  
  @Override public CoreExecutableNode parse(@SuppressWarnings("unused") InlineParsingRequest request) {
    CoreExpressionNode body = null; // todo: fake a body
    return new CoreExecutableNode(this, body);
  }

  @Override public CallTarget parse(@SuppressWarnings("unused") ParsingRequest request) {
    FrameDescriptor fd = new FrameDescriptor();
    CoreExpressionNode body = null; // todo: fake a body
    CoreRootNode root = new CoreRootNode(this, body, fd);
    return Truffle.getRuntime().createCallTarget(root);
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

  // testing

  // manufacture a node
  CoreExpressionNode I() {
    FrameDescriptor fd = new FrameDescriptor();
    return new LamNode(Truffle.getRuntime().createCallTarget(new CoreRootNode(this, new ArgNode(0), fd)));
  }

  // manufacture a node, notice no arity
  CoreExpressionNode K() {
    FrameDescriptor fd = new FrameDescriptor();
    return new LamNode(Truffle.getRuntime().createCallTarget(new CoreRootNode(this, new ArgNode(0), fd)));
  }

}