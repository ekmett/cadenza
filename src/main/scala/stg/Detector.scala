package stg

import com.oracle.truffle.api.TruffleFile
import java.io.IOException
import java.nio.charset.Charset
import java.nio.{ ByteOrder, ByteBuffer }
import java.nio.file.StandardOpenOption
import scala.util.{ Try, Using }

class Detector extends TruffleFile.FileTypeDetector {
  @throws(classOf[IOException])
  override def findMimeType(file: TruffleFile): String = {
    val fileName = file.getName
    if (fileName == null) null
    else if (fileName.endsWith(Language.EXTENSION)) Language.MIME_TYPE
//    else if ((fileName.endsWith(Language.BYTECODE_EXTENSION) && (readMagicWord(file) == Language.BYTECODE_MAGIC_WORD))) Language.BYTECODE_MIME_TYPE
    else null
  }

  def readMagicWord(file: TruffleFile): Long = {
    val result : Try[Long] = Using (file.newInputStream(StandardOpenOption.READ)) { is => 
      val buffer = Array.fill[Byte](4)(0)
      if (is.read(buffer) != buffer.length) 0L
      else Integer.toUnsignedLong(ByteBuffer.wrap(buffer).order(ByteOrder.nativeOrder).getInt)
    }
    result.recover({
      case (_ : IOException) | (_ : SecurityException) => 0L
    }).get
  }

  @throws(classOf[IOException])
  override def findEncoding(file: TruffleFile): Charset = null
}

