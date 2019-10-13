package core;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.graalvm.polyglot.Source;

public final class CoreContext {
  private static final Source BUILTIN_SOURCE = Source.newBuilder(CoreLanguage.ID, "", "[core builtin]").buildLiteral();

  public CoreContext(CoreLanguage language, TruffleLanguage.Env env) {
    this.language = language;
    this.env = env;
  }
  public final CoreLanguage language;
  public TruffleLanguage.Env env;
  
  public final Assumption singleThreadedAssumption = Truffle.getRuntime().createAssumption("context is single threaded");

  public void shutdown() { }

  public static NodeInfo lookupNodeInfo(Class<?> clazz) {
    if (clazz == null) return null;
    NodeInfo info = clazz.getAnnotation(NodeInfo.class);
    if (info != null) return info;
    return lookupNodeInfo(clazz.getSuperclass());
  }
}
