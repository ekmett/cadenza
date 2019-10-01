package coda

import com.oracle.truffle.api.{ Truffle, TruffleLanguage }

class CoreContext(val language: CoreLanguage, var env: TruffleLanguage.Env) {
  val singleThreadedAssumption = Truffle.getRuntime.createAssumption("context is single threaded")
  def shutdown: Unit = {}
}
