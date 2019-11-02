package cadenza.frame

import com.oracle.truffle.api.frame.FrameSlotTypeException

typealias Slot = Int

// these are all the distinctions the JVM cares about
abstract class DataFrame {
  abstract fun getValue(slot: Slot): Any?

  @Throws(FrameSlotTypeException::class)
  abstract fun getDouble(slot: Slot): Double // D
  abstract fun isDouble(slot: Slot): Boolean

  @Throws(FrameSlotTypeException::class)
  abstract fun getFloat(slot: Slot): Float // F
  abstract fun isFloat(slot: Slot): Boolean

  @Throws(FrameSlotTypeException::class)
  abstract fun getInteger(slot: Slot): Int // I
  abstract fun isInteger(slot: Slot): Boolean

  @Throws(FrameSlotTypeException::class)
  abstract fun getLong(slot: Slot): Long // L
  abstract fun isLong(slot: Slot): Boolean

  @Throws(FrameSlotTypeException::class)
  abstract fun getObject(slot: Slot): Any? // O
  abstract fun isObject(slot: Slot): Boolean
}