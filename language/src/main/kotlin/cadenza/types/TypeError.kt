package cadenza.types

class TypeError
constructor(val msg: String? = null, val actual: Type? = null, val expected: Type? = null) : Exception() {
  companion object {
    private const val serialVersionUID: Long = 212674730538525189L
  }
}