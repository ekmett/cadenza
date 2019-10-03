package stg;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
//import java.io.IOException;
//import java.lang.UnsupportedOperationException;
import org.graalvm.options.OptionValues;

@TruffleLanguage.Registration(
  id = Language.ID,
  name = Language.NAME,
  version = Language.VERSION,
  defaultMimeType = Language.MIME_TYPE,
  characterMimeTypes = Language.MIME_TYPE,
  byteMimeTypes = Language.BYTECODE_MIME_TYPE,
  contextPolicy = TruffleLanguage.ContextPolicy.SHARED,
  fileTypeDetectors = Detector.class,
  interactive = true, // eventually this will be false
  internal = false // eventually this will be true
)
public class Language extends TruffleLanguage<Context> {

  public final static String ID = "stg";
  public final static String NAME = "STG";
  public final static String VERSION = "0";
  public final static String MIME_TYPE = "application/x-stg";
  public final static String EXTENSION = "stg";
  public final static String BYTECODE_MIME_TYPE = "application/x-stg-binary";
  public final static String BYTECODE_EXTENSION = "stgc";
  public final static long   BYTECODE_MAGIC_WORD = 0xC0DAC0DEL;

  public Language() {}

  public final Assumption singleContextAssumption = Truffle.getRuntime().createAssumption("Only a single context is active");
  
  @Override
  public Context createContext(TruffleLanguage.Env env) { 
    return new Context(this, env) {}; 
  } // cheap and easy
  
  @Override
  public void initializeContext(Context ctx) {} // TODO: any expensive init here, like stdlib loading
  
  @Override
  public void finalizeContext(Context ctx) { 
    ctx.shutdown(); 
  } // TODO: any expensive shutdown here
  
  @Override
  public ExecutableNode parse(TruffleLanguage.InlineParsingRequest request) {
    return null; // unsupported
  }

  @Override
  public CallTarget parse(TruffleLanguage.ParsingRequest request) { 
    return null; // unsupported
  }

  @Override
  public boolean isObjectOfLanguage(Object obj) { 
    return false; 
  }

  @Override
  public void initializeMultipleContexts() { 
    singleContextAssumption.invalidate(); 
  }

  @Override 
  public boolean areOptionsCompatible(OptionValues a, OptionValues b) {
    return true; 
  } // no options!

  @Override 
  public void initializeMultiThreading(Context ctx) { 
    ctx.singleThreadedAssumption.invalidate(); 
  }
  
  @Override
  public boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) { 
    return true; 
  }

  @Override 
  public void initializeThread(Context ctx, Thread thread) {}

  @Override
  public void disposeThread(Context ctx, Thread thread) {}
  
  @Override
  public Object findMetaObject(Context ctx, Object value) { 
    return null; 
  }
  
  @Override
  public SourceSection findSourceLocation(Context ctx, Object value) { 
    return null; 
  }
  
  @Override
  public boolean isVisible(Context ctx, Object value) { 
    return true; 
  }
  
  @Override
  public String toString(Context ctx, Object value) { 
    return value.toString(); 
  }
  
  @Override
  public boolean patchContext(Context ctx, TruffleLanguage.Env env) { 
    ctx.env = env; return 
    true; 
  }
}
