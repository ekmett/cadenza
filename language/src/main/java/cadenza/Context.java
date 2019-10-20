package cadenza;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.graalvm.polyglot.Source;

public final class Context {
  public static final Source BUILTIN_SOURCE = Source.newBuilder(Language.ID, "", "[core builtin]").buildLiteral();

  public Context(Language language, TruffleLanguage.Env env) {
    this.language = language;
    this.env = env;
  }
  public final Language language;
  TruffleLanguage.Env env;

  public TruffleLanguage.Env getEnv() { return env; }

  public final Assumption singleThreadedAssumption = Truffle.getRuntime().createAssumption("context is single threaded");

  public void shutdown() { }

  public static NodeInfo lookupNodeInfo(Class<?> clazz) {
    if (clazz == null) return null;
    NodeInfo info = clazz.getAnnotation(NodeInfo.class);
    if (info != null) return info;
    return lookupNodeInfo(clazz.getSuperclass());
  }
}
