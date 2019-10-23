package cadenza

import com.oracle.truffle.api.Assumption
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.nodes.NodeInfo
import org.graalvm.polyglot.Source

class Context(val language: Language, env: TruffleLanguage.Env) {
  var env: TruffleLanguage.Env
    internal set

  val singleThreadedAssumption = Truffle.getRuntime().createAssumption("context is single threaded")

  init {
    this.env = env
  }

  fun shutdown() {}

  companion object {
    val BUILTIN_SOURCE = Source.newBuilder(Language.ID, "", "[core builtin]").buildLiteral()

    fun lookupNodeInfo(clazz: Class<*>?): NodeInfo? {
      if (clazz == null) return null
      val info = clazz.getAnnotation<NodeInfo>(NodeInfo::class.java)
      return info ?: lookupNodeInfo(clazz.superclass)
    }
  }
}
