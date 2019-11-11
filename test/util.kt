package org.intelligence

import java.io.ByteArrayOutputStream
import java.io.PrintStream

fun without(body: () -> Unit): String {
  val old = System.out
  val out = ByteArrayOutputStream()
  System.setOut(PrintStream(out))
  try {
    body()
  } finally {
    System.setOut(old)
  }
  return out.toString().filter { it != '\r' }
}
