package core;

import com.oracle.truffle.api.TruffleFile;
import java.nio.charset.Charset;

public class Detector implements TruffleFile.FileTypeDetector {
  @Override public String findMimeType(TruffleFile file) {
    String name = file.getName();
    if (name != null && name.endsWith(CoreLanguage.EXTENSION)) return CoreLanguage.MIME_TYPE;
    return null;
  }

  @Override public Charset findEncoding(TruffleFile file) {
    return null;
  }
}
