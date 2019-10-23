package cadenza

import com.oracle.truffle.api.TruffleFile

import java.io.BufferedReader
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

class Detector : TruffleFile.FileTypeDetector {
  override fun findMimeType(file: TruffleFile): String? {
    val name = file.name ?: return null
    if (name.endsWith(Language.EXTENSION)) return Language.MIME_TYPE
    try {
      file.newBufferedReader(StandardCharsets.UTF_8).use { fileContent ->
        val firstLine = fileContent.readLine()
        if (firstLine != null && SHEBANG_REGEXP.matcher(firstLine).matches())
          return Language.MIME_TYPE
      }
    } catch (e: IOException) {
      // ok
    } catch (e: SecurityException) {
    }

    return null
  }

  override fun findEncoding(_file: TruffleFile): Charset {
    return StandardCharsets.UTF_8
  }

  companion object {
    private val SHEBANG_REGEXP = Pattern.compile("^#! ?/usr/bin/(env +ccc|ccc).*")
  }
}
