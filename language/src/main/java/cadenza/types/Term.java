package cadenza.types;

import cadenza.nodes.Code;
import com.oracle.truffle.api.frame.FrameDescriptor;

import java.util.Arrays;

import static cadenza.nodes.Code.*;

// terms can be checked and inferred. The result is an expression.
public abstract class Term {
  // expected is optional for some types, giving us bidirectional type checking.
  public abstract Witness check(Ctx ctx, Type expected) throws TypeError;
  public final Witness infer(Ctx ctx) throws TypeError { return check(ctx, null); }

    public static Term tname(String name) {
    return new Term() {
      @Override
      public Witness check(Ctx ctx, Type _expectedType) throws TypeError {
        return new Witness(lookup(ctx,name)) {
          @Override public Code compile(FrameDescriptor fd) {
            return Code.var(fd.findOrAddFrameSlot(name));
          }
        };
      }
    };
  }

  public static Term tif(Term body, Term thenTerm, Term elseTerm) {
    return new Term() {
      @Override
      public Witness check(Ctx ctx, Type expectedType) throws TypeError {
        var bodyWitness = body.check(ctx, Type.bool);
        var thenWitness = thenTerm.check(ctx, expectedType);
        var actualType = thenWitness.type;
        var elseWitness = elseTerm.check(ctx, actualType);
        return new Witness(actualType) {
          @Override public Code compile(FrameDescriptor fd) {
            return new Code.If(actualType, bodyWitness.compile(fd), thenWitness.compile(fd), elseWitness.compile(fd));
          }
        };
      }
    };
  }

  public static Term tapp(Term trator, Term... trands) {
    return new Term() {
      @Override
      public Witness check(Ctx ctx, Type expectedType) throws TypeError {
        var wrator = trator.check(ctx, expectedType);
        var currentType = wrator.type;
        int len = trands.length;
        var wrands = new Witness[len]; // can't make into a nice stream, throws type errors
        for (int i = 0; i < len; ++i) {
          var arr = (Type.Arr) currentType;
          if (arr == null) throw new TypeError("not a fun type");
          wrands[i] = trands[i].check(ctx, arr.argument);
          currentType = arr.result;
        }
        return new Witness(currentType) {
          @Override public Code compile(FrameDescriptor fd) {
            return app(wrator.compile(fd), Arrays.stream(wrands).map(w -> w.compile(fd)).toArray(Code[]::new));
          }
        };
      }
    };
  }

  public static Term tlam(String[] _names, Term _body) {
    return null;
  }

  // provides an expression with a given type in a given frame
  public static abstract class Witness {
    public final Type type;
    Witness(Type type) { this.type = type; }
    public abstract Code compile(FrameDescriptor fd); // take a frame descriptor and emit an expression

    // builder style
    public Witness match(Type expectedType) throws TypeError {
      if (expectedType != null && type != expectedType)
        throw new TypeError("type mismatch", type, expectedType);
      return this;
    }
  }

  // singly linked list
  public static final class Ctx {
    public final String name;
    public final Type type;
    public final Ctx next;

    public Ctx(final String name, final Type type, final Ctx next) {
      this.name = name;
      this.type = type;
      this.next = next;
    }

    public static Ctx nil = null;
    public static Ctx cons(String name, Type type, Ctx next) {
      return new Ctx(name,type,next);
    }
  }

  static Type lookup(final Ctx ctx, final String name) throws TypeError {
    for(Ctx current = ctx; current != null; current = current.next) {
      if (name.equals(current.name)) return current.type;
    }
    throw new TypeError("unknown variable");
  }

}
