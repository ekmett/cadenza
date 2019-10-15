package cadenza;

import cadenza.nodes.Expr;
import static cadenza.nodes.Expr.*;
import cadenza.types.Type;
import cadenza.types.TypeError;
import com.oracle.truffle.api.frame.FrameDescriptor;


// terms can be checked and inferred
public interface Term {
  // the frame descriptor is my poor man's environment for now.

  // expected is optional for some types, giving us bidirectional type checking.
  Witness check(FrameDescriptor env, Type expected) throws TypeError;

  // provides a proof this expression has this type in this frame.
  public static class Witness {
    public final Expr expr;
    public final Type type;
    Witness(Expr expr, Type type) { this.expr = expr; this.type = type; }
  }

  public static Witness witness(Expr expr, Type type) { return new Witness(expr, type); }

  public static Term tif(Term body, Term thenTerm, Term elseTerm) {
    return (FrameDescriptor env, Type expectedType) -> {
        Witness bodyWitness = body.check(env, Type.bool);
        Witness thenWitness = thenTerm.check(env, expectedType);
        Type actualType = thenWitness.type;
        Witness elseWitness = elseTerm.check(env, actualType);
        return witness(new Expr.If(actualType, bodyWitness.expr, thenWitness.expr, elseWitness.expr), actualType);
      };
  }

  public static Term tapp(Term trator, Term... trands) {
    return (FrameDescriptor env, Type expectedType) -> {
      Witness wrator = trator.check(env, expectedType);
      Type currentType = wrator.type;
      int len = trands.length;
      Expr[] erands = new Expr[len];
      for (int i=0;i<len;++i) {
        Type.Arr arr = (Type.Arr) currentType;
        if (arr == null) throw new TypeError();
        erands[i] = trands[i].check(env, arr.argument).expr;
        currentType = arr.result;
      }
      return witness(app(wrator.expr, erands), currentType);
    };
  }

  //public static Term tlam


}
