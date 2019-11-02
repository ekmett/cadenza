package cadenza

import com.oracle.truffle.api.source.Source
import com.oracle.truffle.api.source.SourceSection

const val NO_SOURCE = -1
const val UNAVAILABLE_SOURCE = -2

// Source + Section = SourceSection -- we are in java
data class Section(val sourceCharIndex: Int, val sourceLength: Int) {
  companion object {
    val default = Section(NO_SOURCE, 0)
    val unavailable = Section(UNAVAILABLE_SOURCE, 0)
  }
}

fun Source.section(section: Section): SourceSection? = when (section.sourceCharIndex) {
  UNAVAILABLE_SOURCE -> createUnavailableSection()
  NO_SOURCE -> null
  else -> createSection(section.sourceCharIndex, section.sourceLength)
}