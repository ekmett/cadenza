package coda.core

import com.oracle.truffle.api.{ Truffle, TruffleLanguage }

class Context(val language: Language, var env: TruffleLanguage.Env) {
  val singleThreadedAssumption = Truffle.getRuntime.createAssumption("context is single threaded")
  def shutdown: Unit = {}
}
