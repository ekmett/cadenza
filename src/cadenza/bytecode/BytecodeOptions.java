package cadenza.bytecode;

import com.oracle.truffle.api.Option;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionType;
import org.graalvm.options.OptionDescriptors;

@Option.Group("cadenza")
public final class BytecodeOptions {
    private BytecodeOptions() {}

    public static OptionDescriptors descriptors() {
        return new BytecodeOptionsOptionDescriptors();
    }

    @Option(name = "Backend", help = "Execution backend: ast (default) or experimental bytecode.",
            category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL)
    public static final OptionKey<String> BACKEND = new OptionKey<>("ast",
            new OptionType<>("Cadenza backend", value -> {
                if (!value.equals("ast") && !value.equals("bytecode")) {
                    throw new IllegalArgumentException("cadenza.Backend must be ast or bytecode");
                }
                return value;
            }));
}
