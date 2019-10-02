package stg

import com.oracle.truffle.api.source.SourceSection

case class Syntax[@specialized T](value: T, sourceSection: SourceSection)
