package cadenza

import com.oracle.truffle.api.source.Source
import com.oracle.truffle.api.source.SourceSection

sealed class Loc() {
  abstract fun section(source: Source): SourceSection
  object Unavailable : Loc() {
    override fun section(source: Source): SourceSection = source.createUnavailableSection()
  }
  data class Range(val start: Int, val length: Int) : Loc() {
    override fun section(source: Source): SourceSection = source.createSection(start, length)
  }
  data class Line(val line: Int) : Loc() {
    override fun section(source: Source): SourceSection = source.createSection(line)
  }
}

fun Source.section(loc: Loc): SourceSection = loc.section(this)