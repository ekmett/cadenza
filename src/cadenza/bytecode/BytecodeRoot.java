package cadenza.bytecode;

import cadenza.Language;
import cadenza.RuntimeError;
import cadenza.data.BigInt;
import cadenza.data.Closure;
import cadenza.data.DataTypes;
import cadenza.jit.CadenzaRootNode;
import cadenza.jit.Dispatch;
import cadenza.jit.DispatchNodeGen;
import cadenza.jit.Indirection;
import cadenza.jit.BuiltinTailCallException;
import cadenza.semantics.Type;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.Variadic;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;

/** Experimental concrete typed-core interpreter; the AST backend remains the default. */
@GenerateBytecode(languageClass = Language.class, enableUncachedInterpreter = true,
        enableTagInstrumentation = true,
        // Shared tail-call exceptions bypass DSL tag exits; avoid misleading root profilers.
        enableRootTagging = false, enableRootBodyTagging = false,
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

    @Override
    public AbstractTruffleException interceptTruffleException(AbstractTruffleException exception,
            VirtualFrame frame, BytecodeNode bytecodeNode, int bytecodeIndex) {
        if (exception instanceof RuntimeError error && error.getEncapsulatingSourceSection() == null) {
            // Source loading may replace the interpreter; the location translates its index.
            error.at(bytecodeNode.getBytecodeLocation(bytecodeIndex).ensureSourceInformation().getSourceLocation());
        }
        return exception;
    }

    @Override
    public Object interceptControlFlowException(ControlFlowException exception, VirtualFrame frame,
            BytecodeNode bytecodeNode, int bytecodeIndex) {
        if (exception instanceof BuiltinTailCallException call) {
            call.atBytecode(bytecodeNode, bytecodeIndex);
        }
        throw exception;
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
        public static Object read(Indirection cell, @Bind("$node") Node node) {
            if (!cell.getSet()) {
                throw new RuntimeError("recursive binding read before initialization", node, null);
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
        @TruffleBoundary
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
        @TruffleBoundary
        public static BigInt bigInts(BigInt left, BigInt right) {
            return new BigInt(left.getValue().subtract(right.getValue()));
        }
    }

    @Operation
    public static final class Multiply {
        @Specialization(rewriteOn = ArithmeticException.class)
        public static int ints(int left, int right) {
            return Math.multiplyExact(left, right);
        }

        @Specialization
        @TruffleBoundary
        public static BigInt bigInts(BigInt left, BigInt right) {
            return new BigInt(left.getValue().multiply(right.getValue()));
        }
    }

    @Operation
    public static final class Divide {
        @Specialization(rewriteOn = ArithmeticException.class)
        public static int ints(int left, int right, @Bind("$node") Node node) {
            if (right == 0) throw new RuntimeError("division by zero", node, null);
            return Math.divideExact(left, right);
        }

        @Specialization
        @TruffleBoundary
        public static BigInt bigInts(BigInt left, BigInt right, @Bind("$node") Node node) {
            if (right.getValue().signum() == 0) throw new RuntimeError("division by zero", node, null);
            return new BigInt(left.getValue().divide(right.getValue()));
        }
    }

    @Operation
    public static final class Remainder {
        @Specialization
        public static int ints(int left, int right, @Bind("$node") Node node) {
            if (right == 0) throw new RuntimeError("modulo by zero", node, null);
            return left % right;
        }

        @Specialization
        @TruffleBoundary
        public static BigInt bigInts(BigInt left, BigInt right, @Bind("$node") Node node) {
            if (right.getValue().signum() == 0) throw new RuntimeError("modulo by zero", node, null);
            return new BigInt(left.getValue().remainder(right.getValue()));
        }
    }

    @Operation
    public static final class LessOrEqual {
        @Specialization
        public static boolean ints(int left, int right) {
            return left <= right;
        }

        @Specialization
        @TruffleBoundary
        public static boolean bigInts(BigInt left, BigInt right) {
            return left.getValue().compareTo(right.getValue()) <= 0;
        }
    }

    @Operation
    public static final class Equal {
        @Specialization
        public static boolean ints(int left, int right) {
            return left == right;
        }

        @Specialization
        @TruffleBoundary
        public static boolean bigInts(BigInt left, BigInt right) {
            return left.getValue().equals(right.getValue());
        }
    }
}
