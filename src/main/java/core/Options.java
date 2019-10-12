package core;

import com.oracle.truffle.api.Option;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;

@Option.Group("core")
public abstract class Options {
  public static final OptionDescriptors DESCRIPTORS = new OptionsOptionDescriptors();

  @Option(name="tco", help = "Tail-call optimization", category = OptionCategory.USER, stability = OptionStability.EXPERIMENTAL)
  public static final OptionKey<Boolean> TAIL_CALL_OPTIMIZATION = new OptionKey<>(false);
}
