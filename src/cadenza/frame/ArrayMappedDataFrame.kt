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
// then we could use popcount-based indexing like this, reducing the memory pressure by
// and then shaving a bit more by using ints rather than longs in the idata array. saving
// 2-6x. Moving on from here, custom classloader may let us shave the array overheads

// implements the portions of the DataFrame API that I'd need for now
class ArrayMappedDataFrame(
  val adata: Array<Any?>,
  val idata: Array<Int>,
  val obj : Mask // anything in adata
) : DataFrame {
  val int: Mask get() = obj.inv()
  override fun isInteger(slot: Slot) = slot.mask isa int
  override fun isObject(slot: Slot) = slot.mask isa obj
  override fun getValue(slot: Slot): Any? = slot.mask.let {
    if (it isa obj) adata[it prefix obj]
    else idata[it prefix int]
  }
  @Throws(FrameSlotTypeException::class)
  override fun getInteger(slot: Slot): Int = slot.mask.let {
    if (it isa obj) throw FrameSlotTypeException()
    idata[it prefix int]
  }
  @Throws(FrameSlotTypeException::class)
  override fun getObject(slot: Slot): Any? = slot.mask.let {
    if (it isa int) throw FrameSlotTypeException()
    idata[it prefix obj]
  }

  override fun getDouble(slot: Slot) = throw FrameSlotTypeException()
  override fun isDouble(slot: Slot): Boolean = false
  override fun getFloat(slot: Slot): Float = throw FrameSlotTypeException()
  override fun isFloat(slot: Slot): Boolean = false
  override fun getLong(slot: Slot): Long = throw FrameSlotTypeException()
  override fun isLong(slot: Slot): Boolean = false
}