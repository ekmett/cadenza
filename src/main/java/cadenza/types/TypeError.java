package cadenza.types;

public class TypeError extends Exception {
  public final Type actual, expected;
  public TypeError(Type actual, Type expected) {
    this.actual = actual;
    this.expected = expected;
  }
  public TypeError(Type actual) {
    this(actual,null);
  }
  public TypeError() { this(null,null); }
}
