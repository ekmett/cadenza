package stg;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;

public class Context {
  public Context(Language language, TruffleLanguage.Env env) {
    this.language = language;
    this.env = env;
  }
  public Language language;
  public TruffleLanguage.Env env;
  
  public final Assumption singleThreadedAssumption = Truffle.getRuntime().createAssumption("context is single threaded");

  public void shutdown() { }
}