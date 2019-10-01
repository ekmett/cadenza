package coda.core

import com.oracle.truffle.api.source.SourceSection

case class Syntax[@specialized T](value: T, sourceSection: SourceSection)
