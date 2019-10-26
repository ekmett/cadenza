package cadenza

import com.oracle.truffle.api.Assumption
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.nodes.NodeInfo
import org.graalvm.polyglot.Source

val BUILTIN_SOURCE = Source.newBuilder(Language.ID, "", "[core builtin]").buildLiteral()

fun lookupNodeInfo(clazz: Class<*>?): NodeInfo? {
  if (clazz == null) return null
  val info = clazz.getAnnotation<NodeInfo>(NodeInfo::class.java)
  return info ?: lookupNodeInfo(clazz.superclass)
}

class Context(val language: Language, var env: TruffleLanguage.Env) {
  val singleThreadedAssumption = Truffle.getRuntime().createAssumption("context is single threaded")

  fun shutdown() {}
}
