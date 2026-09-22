package cadenza.bytecode;

import cadenza.Language;
import cadenza.data.BigInt;
import cadenza.data.Closure;
import cadenza.data.DataTypes;
import cadenza.jit.CadenzaRootNode;
import cadenza.jit.Dispatch;
import cadenza.jit.DispatchNodeGen;
import cadenza.jit.Indirection;
import cadenza.semantics.Type;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.Variadic;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;

/** Experimental concrete typed-core interpreter; the AST backend remains the default. */
@GenerateBytecode(languageClass = Language.class, enableUncachedInterpreter = true,
        boxingEliminationTypes = {int.class, boolean.class})
@TypeSystemReference(DataTypes.class)
public abstract class BytecodeRoot extends CadenzaRootNode implements BytecodeRootNode {
    protected BytecodeRoot(Language language, FrameDescriptor descriptor) {
        super(language, descriptor);
    }

    @Override
    public String getName() {
        return "bytecode";
    }

    /** Captures precede explicit parameters in papArgs, using the ordinary guest ABI. */
    public record ClosureTemplate(RootCallTarget target, int arity, Type targetType) {}

    @Operation
    @ConstantOperand(type = ClosureTemplate.class, name = "template")
    public static final class MakeClosure {
        @Specialization
        public static Closure create(ClosureTemplate template, @Variadic Object[] captures) {
            return new Closure(null, captures, template.arity(), template.targetType(), template.target());
        }
    }

    @Operation(forceCached = true)
    @ConstantOperand(type = int.class, name = "arity")
    @ConstantOperand(type = boolean.class, name = "tail")
    public static final class Apply {
        @Specialization
        public static Object apply(VirtualFrame frame, int arity, boolean tail, Closure function,
                @Variadic Object[] arguments,
                @Cached(value = "createDispatch(arity, tail)", neverDefault = true) Dispatch dispatch) {
            return dispatch.executeDispatch(frame, function, arguments);
        }

        public static Dispatch createDispatch(int arity, boolean tail) {
            return DispatchNodeGen.create(arity, tail);
        }
    }

    @Operation
    public static final class NewCell {
        @Specialization
        public static Indirection create() {
            return new Indirection();
        }
    }

    @Operation
    public static final class InitializeCell {
        @Specialization
        public static void initialize(Indirection cell, Object value) {
            cell.setValue(value);
            cell.setSet(true);
        }
    }

    @Operation
    public static final class ReadCell {
        @Specialization
        public static Object read(Indirection cell) {
            if (!cell.getSet()) {
                throw new cadenza.RuntimeError("recursive binding read before initialization");
            }
            return cell.getValue();
        }
    }

    @Operation
    public static final class Add {
        @Specialization(rewriteOn = ArithmeticException.class)
        public static int ints(int left, int right) {
            return Math.addExact(left, right);
        }

        @Specialization
        public static BigInt bigInts(BigInt left, BigInt right) {
            return new BigInt(left.getValue().add(right.getValue()));
        }
    }

    @Operation
    public static final class Subtract {
        @Specialization(rewriteOn = ArithmeticException.class)
        public static int ints(int left, int right) {
            return Math.subtractExact(left, right);
        }

        @Specialization
        public static BigInt bigInts(BigInt left, BigInt right) {
            return new BigInt(left.getValue().subtract(right.getValue()));
        }
    }

    @Operation
    public static final class Multiply {
        @Specialization
        public static int ints(int left, int right) {
            return left * right;
        }
    }

    @Operation
    public static final class Divide {
        @Specialization
        public static int ints(int left, int right) {
            return left / right;
        }
    }

    @Operation
    public static final class Remainder {
        @Specialization
        public static int ints(int left, int right) {
            return left % right;
        }
    }

    @Operation
    public static final class LessOrEqual {
        @Specialization
        public static boolean ints(int left, int right) {
            return left <= right;
        }
    }

    @Operation
    public static final class Equal {
        @Specialization
        public static boolean ints(int left, int right) {
            return left == right;
        }
    }
}
