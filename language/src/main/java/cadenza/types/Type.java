package cadenza.types;

import cadenza.values.Closure;
import cadenza.values.Int;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.interop.UnsupportedTypeException;

import java.util.Objects;

// eventually move to a more hindley-milner style model with quantifiers, but then we need subsumption, unification, etc.
// also this doesn't presuppose if we're heading towards dependently typed languages or towards haskell right now
public abstract class Type {
  public int getArity() { return 0; }
  public abstract void validate(Object t) throws UnsupportedTypeException; // checks the contract for a given type holds, for runtime argument passing, etc.

  public void match(Type expected) throws TypeError {
    if (!equals(expected)) throw new TypeError("type mismatch", this,expected);
  }

  UnsupportedTypeException unsupported(String msg, Object... objects) {
    return UnsupportedTypeException.create(objects,msg);
  }

  public final FrameSlotKind rep; // used to set the starting frameslotkind
  Type(FrameSlotKind rep) { this.rep = rep; }

  @CompilerDirectives.ValueType
  public static class Arr extends Type {
    private final int arity;
    public final Type argument, result;
    public Arr(Type argument, Type result) {
      super(FrameSlotKind.Object);
      this.argument = argument;
      this.result = result;
      this.arity = result.getArity()+1;
    }

    @Override
    public void validate(Object t) throws UnsupportedTypeException {
      if (!(t instanceof Closure)) throw unsupported("expected closure",t);
      Closure c = (Closure)t;
      if (!(this.argument == c.type))
        throw unsupported(
          "expected closure of type: " + this.toString() + ", but received one of type " + c.type.toString(),
          c
        );
    }
    public int getArity() { return arity; }
    @Override
    public int hashCode() {
      return Objects.hash(argument,result);
    }
    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Arr)) return false;
      Arr that = (Arr)o;
      return argument.equals(that.argument) && result.equals(that.result);
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
    public void validate(Object t) throws UnsupportedTypeException {
      throw unsupported("expected io",t);
    }

    @Override
    public int hashCode() {
      return result.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof IO)) return false;
      return result.equals(((IO)o).result);
    }
  }

  public static Type arr(Type a, Type b) { return new Arr(a,b); }
  public static Type io(Type a) { return new IO(a); }
  public static final Type bool = new Type(FrameSlotKind.Boolean) {
    public void validate(Object t) throws UnsupportedTypeException {
      if (!(t instanceof Boolean)) throw unsupported("expected boolean",t);
    }
  };
  public static final Type object = new Type(FrameSlotKind.Object) {
    public void validate(Object t) {}
  };
  public static final Type unit = new Type(FrameSlotKind.Object) {
    public void validate(Object t) throws UnsupportedTypeException {
      if (t != null) throw unsupported("expected unit", t);
    }
  }; // always null. use byte or something else instead?
  public static final Type nat = new Type(FrameSlotKind.Int) {
    public void validate(Object t) throws UnsupportedTypeException {
      if ( ! ((t instanceof Integer && ((int)t)>=0)
         || (t instanceof Int && ((Int)t).isNatural())))
        throw unsupported("expected nat", t);
    }
  };
  public static final Type action = io(unit);
}
