package core.values;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import core.CoreTypesGen;
import core.node.CoreRootNode;

@CompilerDirectives.ValueType // screw your reference equality
@ExportLibrary(InteropLibrary.class)
public abstract class Closure implements TruffleObject {
  @ExportMessage
  public boolean isExecutable() {
    return true;
  }

  @ExportMessage
  @CompilerDirectives.TruffleBoundary
  public abstract Object execute(Object... arguments);

  public long executeLong(Object... arguments) throws UnexpectedResultException {
    return CoreTypesGen.expectLong(execute(arguments));
  }

  public boolean executeBoolean(Object... arguments) throws UnexpectedResultException {
    return CoreTypesGen.expectBoolean(execute(arguments));
  }

  public Closure executeClosure(Object... arguments) throws UnexpectedResultException {
    return CoreTypesGen.expectClosure(execute(arguments));
  }

  public static Closure create(MaterializedFrame env, CoreRootNode body) {
    return new Closure() {
      // brute force copy of the environment with our arguments added.
      // TODO: do we want to add the arguments to the frame rather than leaving them untyped?
      @ExplodeLoop
      private VirtualFrame virtual(Object... arguments) {
        FrameDescriptor fd = env.getFrameDescriptor();
        CompilerAsserts.partialEvaluationConstant(fd);
        VirtualFrame result = Truffle.getRuntime().createVirtualFrame(arguments, fd);
        try {
          for (FrameSlot slot : fd.getSlots()) {
            switch (fd.getFrameSlotKind(slot)) {
              case Object:
                result.setObject(slot, env.getObject(slot));
                break;
              case Long:
                result.setLong(slot, env.getLong(slot));
                break;
              case Boolean:
                result.setBoolean(slot, env.getBoolean(slot));
                break;
              default:
                throw new FrameSlotTypeException();
            }
          }
        } catch (FrameSlotTypeException e) {
          throw new RuntimeException("bad frame",e);
        }
        return result;
      }

      public Object execute(Object... arguments) {
        return body.execute(virtual(arguments));
      }

      @Override public long executeLong(Object... arguments) throws UnexpectedResultException {
        return body.executeLong(virtual(arguments));
      }

      @Override public Closure executeClosure(Object... arguments) throws UnexpectedResultException {
        return body.executeClosure(virtual(arguments));
      }

      @Override public boolean executeBoolean(Object... arguments) throws UnexpectedResultException {
        return body.executeBoolean(virtual(arguments));
      }
    };
  }
}