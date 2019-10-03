package core;

import core.values.CoreBigInteger;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.source.SourceSection;
import org.graalvm.options.OptionValues;

@SuppressWarnings("SpellCheckingInspection")
@TruffleLanguage.Registration(
  id = Language.ID,
  name = Language.NAME,
  version = Language.VERSION,
  defaultMimeType = Language.MIME_TYPE,
  characterMimeTypes = Language.MIME_TYPE,
  //byteMimeTypes = Language.BYTECODE_MIME_TYPE,
  contextPolicy = TruffleLanguage.ContextPolicy.SHARED,
  fileTypeDetectors = Detector.class
  //interactive = false,
  //internal = true
)
public class Language extends TruffleLanguage<Context> {

  public final static String ID = "core";
  public final static String NAME = "Core";
  public final static String VERSION = "0";
  public final static String MIME_TYPE = "application/x-core";
  public final static String EXTENSION = "core";
  //public final static String BYTECODE_MIME_TYPE = "application/x-core-binary";
  //public final static String BYTECODE_EXTENSION = "bc";
  //public final static long   BYTECODE_MAGIC_WORD = 0xC0DAC0DEL;

  public Language() {}

  public final Assumption singleContextAssumption = Truffle.getRuntime().createAssumption("Only a single context is active");
  
  @Override
  public Context createContext(TruffleLanguage.Env env) { 
    return new Context(this, env);
  } // cheap and easy
  
  @Override
  public void initializeContext(@SuppressWarnings("unused") Context ctx) {} // TODO: any expensive init here, like stdlib loading
  
  @Override
  public void finalizeContext(Context ctx) { 
    ctx.shutdown(); 
  } // TODO: any expensive shutdown here
  
  @Override
  public ExecutableNode parse(@SuppressWarnings("unused") TruffleLanguage.InlineParsingRequest request) {
    return null; // unsupported
  }

  @Override
  public CallTarget parse(@SuppressWarnings("unused") TruffleLanguage.ParsingRequest request) { 
    return null; // unsupported
  }

  @Override
  public boolean isObjectOfLanguage(Object obj) { 
    if (!(obj instanceof TruffleObject)) return false;
    return obj instanceof CoreBigInteger;
  }

  @Override
  public void initializeMultipleContexts() { 
    singleContextAssumption.invalidate(); 
  }

  @Override 
  public boolean areOptionsCompatible(@SuppressWarnings("unused") OptionValues a, @SuppressWarnings("unused") OptionValues b) {
    return true; 
  } // no options!

  @Override 
  public void initializeMultiThreading(Context ctx) { 
    ctx.singleThreadedAssumption.invalidate(); 
  }
  
  @Override
  public boolean isThreadAccessAllowed(@SuppressWarnings("unused") Thread thread, @SuppressWarnings("unused") boolean singleThreaded) { 
    return true; 
  }

  @Override 
  public void initializeThread(@SuppressWarnings("unused") Context ctx, @SuppressWarnings("unused") Thread thread) {}

  @Override
  public void disposeThread(@SuppressWarnings("unused") Context ctx, @SuppressWarnings("unused") Thread thread) {}
  
  // TODO: return types?
  @Override
  public Object findMetaObject(@SuppressWarnings("unused") Context ctx, Object value) { 
    return getMetaObject(value);
  }

  public static String getMetaObject(Object value) {
    if (value == null) return "ANY";
    InteropLibrary interop = InteropLibrary.getFactory().getUncached(value);
    if (interop.isNumber(value) || value instanceof CoreBigInteger) return "Number";
    if (interop.isBoolean(value)) return "Boolean";
    if (interop.isString(value)) return "String";
    if (interop.isNull(value)) return "NULL" ;
    if (interop.isExecutable(value)) return "Function";
    if (interop.hasMembers(value)) return "Object";
    return "Unsupported";
  }
  
  @Override
  public SourceSection findSourceLocation(@SuppressWarnings("unused") Context ctx, @SuppressWarnings("unused") Object value) { 
    return null; 
  }
  
  @Override
  public boolean isVisible(@SuppressWarnings("unused") Context ctx, @SuppressWarnings("unused") Object value) { 
    return true; 
  }
  
  @Override
  public String toString(@SuppressWarnings("unused") Context ctx, Object value) { 
    return toString(value); 
  }

  public static String toString(Object value) {
    try {
      if (value == null) return "null";
      InteropLibrary interop = InteropLibrary.getFactory().getUncached(value);
      if (interop.fitsInLong(value)) return Long.toString(interop.asLong(value));
      if (interop.isBoolean(value)) return Boolean.toString(interop.asBoolean(value));
      if (interop.isString(value)) return interop.asString(value);
      if (interop.isNull(value)) return "NULL";
      if (interop.isExecutable(value)) return "Function";
      if (interop.hasMembers(value)) return "Object";
      if (value instanceof CoreBigInteger) return value.toString();
      return "Unsupported";
    } catch (UnsupportedMessageException e) {
      CompilerDirectives.transferToInterpreter();
      throw new AssertionError();
    }

  }
  
  @Override
  public boolean patchContext(Context ctx, TruffleLanguage.Env env) { 
    ctx.env = env; return 
    true; 
  }
}
