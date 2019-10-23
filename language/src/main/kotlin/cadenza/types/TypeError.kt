package cadenza.types

class TypeError
@JvmOverloads
constructor(val msg: String? = null, val actual: Type? = null, val expected: Type? = null) : Exception() {

  companion object {
    private val serialVersionUID = 212674730538525189L
  }
}
