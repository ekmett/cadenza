package cadenza;

import com.oracle.truffle.api.TruffleFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public class Detector implements TruffleFile.FileTypeDetector {
  private static final Pattern SHEBANG_REGEXP = Pattern.compile("^#! ?/usr/bin/(env +ccc|ccc).*");
  @Override public String findMimeType(TruffleFile file) {
    String name = file.getName();
    if (name == null) return null;
    if (name.endsWith(Language.EXTENSION)) return Language.MIME_TYPE;
    try (BufferedReader fileContent = file.newBufferedReader(StandardCharsets.UTF_8)) {
      final String firstLine = fileContent.readLine();
      if (firstLine != null && SHEBANG_REGEXP.matcher(firstLine).matches())
        return Language.MIME_TYPE;
    } catch (IOException | SecurityException e) {
      // ok
    }
    return null;
  }

  @Override public Charset findEncoding(TruffleFile file) {
    return StandardCharsets.UTF_8;
  }
}
