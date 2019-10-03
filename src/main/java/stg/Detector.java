package stg;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.file.StandardOpenOption;

public class Detector implements TruffleFile.FileTypeDetector {
  @Override
  public String findMimeType(TruffleFile file) throws IOException {
    String name = file.getName();
    if (name != null && name.endsWith(Language.EXTENSION)) return Language.MIME_TYPE;

    //long magicWord = readMagicWord(file);
    //if (magicWord == Language.BYTECODE_MAGIC_WORD) return Language.BYTECODE_MIME_TYPE;
    return null;
  }

  @Override
  public Charset findEncoding(TruffleFile file) throws IOException {
    return null;
  }

  private static long readMagicWord(TruffleFile file) {
    try (InputStream is = file.newInputStream(StandardOpenOption.READ)) {
      byte[] buffer = new byte[4];
      if (is.read(buffer) != buffer.length) return 0;
      return Integer.toUnsignedLong(ByteBuffer.wrap(buffer).order(ByteOrder.nativeOrder()).getInt());
    } catch (IOException | SecurityException e) {
      return 0;
    }
  }
}
