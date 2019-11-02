package cadenza.frame

import com.oracle.truffle.api.frame.FrameSlotTypeException

class SimpleDataFrame(vararg val data: Any?) {
  fun getValue(slot: Slot) = data[slot]
  fun getSize() = data.size
  @Throws(FrameSlotTypeException::class)
  fun getDouble(slot: Slot) = data[slot] as? Double ?: throw FrameSlotTypeException()
  fun isDouble(slot: Slot) = data[slot] is Double
  @Throws(FrameSlotTypeException::class)
  fun getFloat(slot: Slot) = data[slot] as? Float ?: throw FrameSlotTypeException()
  fun isFloat(slot: Slot) = data[slot] is Float
  @Throws(FrameSlotTypeException::class)
  fun getInteger(slot: Slot) = data[slot] as? Int ?: throw FrameSlotTypeException()
  fun isInteger(slot: Slot) = data[slot] is Int
  @Throws(FrameSlotTypeException::class)
  fun getLong(slot: Slot) = data[slot] as? Long ?: throw FrameSlotTypeException()
  @Throws(FrameSlotTypeException::class)
  fun getObject(slot: Slot) = data[slot]?.takeIf(Companion::isSimpleObject) ?: throw FrameSlotTypeException()
  fun isObject(slot: Slot) = data[slot].let { it != null && isSimpleObject(it) }
  companion object {
    private fun isSimpleObject(it: Any): Boolean = it !is Double && it !is Long && it !is Float && it !is Int
  }
}