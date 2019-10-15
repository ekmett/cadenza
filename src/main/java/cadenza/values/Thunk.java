package cadenza.values;

import java.util.function.Supplier;

public class Thunk<T> implements Supplier<T> {
  private Supplier<T> func;
  public Thunk(Supplier<T> func) { this.func = func; }
  private T result;
  public T get() {
    if (func != null) {
      synchronized(this) {
        if (func != null) {
          result = func.get();
          func = null;
        }
      }
    }
    return result;
  }
}
