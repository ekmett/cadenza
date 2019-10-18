package cadenza.util;

import com.oracle.truffle.api.CompilerDirectives;

public abstract class Errors {
  public static RuntimeException panic(String msg) {
    CompilerDirectives.transferToInterpreter();
    return new RuntimeException(msg);
  }

  public static RuntimeException panic(String msg, Exception e) {
    CompilerDirectives.transferToInterpreter();
    return new RuntimeException(msg, e);
  }
}
