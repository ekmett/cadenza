package cadenza.frame

import com.oracle.truffle.api.frame.FrameSlotTypeException
import java.lang.Long.bitCount

typealias Mask = Long
typealias PowerMask = Long
val Slot.mask: PowerMask get() = 1L shl this
infix fun PowerMask.isa(other: Mask) = (this and other) != 0L
infix fun PowerMask.prefix(other: Mask) = bitCount((this-1L) and other)

// proof of concept

// if we're not concerned with mutation like
// {@link org.graalvm.compiler.truffle.runtime.FrameWithoutBoxing}
// then we could use popcount-based indexing like this, reducing the memory pressure by ~2-4
// the custom loader can let us shave the array overheads

// TODO: extend this to the entire DataFrame API
abstract class ArrayMappedDataFrame(
  val adata: Array<Any?>,
  val idata: Array<Int>,
  val obj : Mask // set for Object
) {
  fun isInteger(slot: Slot) = slot.mask isa obj.inv()
  fun isObject(slot: Slot) = slot.mask isa obj

  fun getValue(slot: Slot): Any? = slot.mask.let {
    if (it isa obj) adata[it prefix obj]
    else idata[it prefix obj.inv()]
  }

  @Throws(FrameSlotTypeException::class)
  fun getInteger(slot: Slot): Int = slot.mask.let {
    if (it isa obj) throw FrameSlotTypeException()
    idata[it prefix obj.inv()]
  }

  @Throws(FrameSlotTypeException::class)
  fun getObject(slot: Slot): Any? = slot.mask.let {
    if (it isa obj.inv()) throw FrameSlotTypeException()
    idata[it prefix obj]
  }

}