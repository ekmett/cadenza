package cadenza.util;

public class Arrays {
  public static Object[] add(final Object[] xs, final Object... ys) {
    Object[] zs = new Object[xs.length + ys.length];
    System.arraycopy(xs, 0, zs, 0, xs.length);
    System.arraycopy(ys, 0, zs,  xs.length, ys.length);
    return zs;
  }
}
