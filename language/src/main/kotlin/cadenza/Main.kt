package cadenza


import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import kotlin.io.use

fun main(_args: Array<String>) {
  println("java")
  //Context.newBuilder("cadenza").allowAllAccess(true).build().use {
  //  val text = Source.create("cadenza", "foo")
  //  println(it.eval(text).toString())
  //}
}