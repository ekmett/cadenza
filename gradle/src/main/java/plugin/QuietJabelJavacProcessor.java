package plugin;

import com.github.bsideup.jabel.JabelJavacProcessor;
import javax.annotation.processing.ProcessingEnvironment;

public class QuietJabelJavacProcessor extends JabelJavacProcessor {
  // mute init
  @Override
  public void init(ProcessingEnvironment _processingEnv) {
    // System.out.println("QUIET!");
  }
}
