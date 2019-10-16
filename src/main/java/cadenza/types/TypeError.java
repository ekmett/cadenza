package cadenza.types;

public class TypeError extends Exception {
  private static final long serialVersionUID = 212674730538525189L;
  public final String message;
  public final Type actual, expected;
  public TypeError(String message, Type actual, Type expected) {
    this.message = message;
    this.actual = actual;
    this.expected = expected;
  }
  public TypeError(String message) {
    this(message, null,null);
  }
  public TypeError(String message, Type actual) {
    this(message, actual,null);
  }
  public TypeError(Type actual) {
    this(null, actual,null);
  }
  public TypeError() { this(null,null); }
}
