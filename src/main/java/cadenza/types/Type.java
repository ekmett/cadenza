package cadenza.types;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameSlotKind;

import java.util.Objects;

// eventually move to a more hindley-milner style model with quantifiers, but then we need subsumption, unification, etc.
// also this doesn't presuppose if we're heading towards dependently typed languages or towards haskell right now
public class Type {
  public void match(Type expected) throws TypeError {
    if (!equals(expected)) throw new TypeError(this,expected);
  }

  public void after(int n) {

  }

  public FrameSlotKind rep; // used to set the starting frameslotkind
  Type(FrameSlotKind rep) { this.rep = rep; }

  @CompilerDirectives.ValueType
  public static class Arr extends Type {
    public final Type argument, result;
    public Arr(Type argument, Type result) {
      super(FrameSlotKind.Object);
      this.argument = argument;
      this.result = result;
    }
    @Override
    public int hashCode() {
      return Objects.hash(argument,result);
    }
    @Override
    public boolean equals(Object o) {
      Arr that = (Arr)o;
      return that != null && argument.equals(that.argument) && result.equals(that.result);
    }
  }

  // IO actions represented ML-style as nullary functions
  @CompilerDirectives.ValueType
  public static class IO extends Type {
    public final Type result;
    public IO(Type result) {
      super(FrameSlotKind.Object); // closure
      this.result = result;
    }
    @Override
    public int hashCode() {
      return result.hashCode();
    }
    @Override
    public boolean equals(Object o) {
      IO that = (IO)o;
      return that != null && result.equals(that.result);
    }
  }

  public static final Type arr(Type a, Type b) { return new Arr(a,b); }
  public static final Type io(Type a) { return new IO(a); }
  public static final Type bool = new Type(FrameSlotKind.Boolean);
  public static final Type object = new Type(FrameSlotKind.Object);
  public static final Type unit = new Type(FrameSlotKind.Object); // always null. use byte or something else instead?
  public static final Type nat = new Type(FrameSlotKind.Int);
  public static final Type action = io(unit);
}
