package cadenza.semantics

// traditional functional programmer environment: the singly linked list map
typealias Name = String
typealias Env<T> = ConsEnv<T>?
data class ConsEnv<out T>(val name: Name, val value: T, val next: ConsEnv<T>?) // next: Env<T> triggers Kotlin/dokka#520
@Suppress("unused") val NilEnv: Env<Nothing> = null

@Throws(TypeError::class)
fun <T> Env<T>.lookup(name: String): T {
  var current: Env<T> = this
  while (current != null) {
    if (name == current.name) return current.value
    current = current.next
  }
  throw TypeError("unknown variable $name")
}
