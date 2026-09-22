import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import jdk.jfr.*;
import jdk.jfr.consumer.*;

/** SYNTHETIC TEST DATA: explicit event weights, not measurements of Java allocations. */
public final class SyntheticAllocationRecording {
  static final String A = "SYNTHETIC-A-\"\\\n\t\b\f\r\u0001-\u03a9";
  static final String B = "SYNTHETIC-B";
  static final String C = "SYNTHETIC-FIRST-ONLY";
  static final class MarkerA {}
  static final class MarkerB {}

  @Name("jdk.ObjectAllocationSample") @Label("SYNTHETIC allocation sample")
  static class Sample extends Event {
    public Class<?> objectClass;
    public long weight;
  }
  @Name("jdk.ObjectAllocationSample") @Label("SYNTHETIC sample without a stack")
  static class StacklessSample extends Event {
    public Class<?> objectClass;
    public long weight;
  }
  @Name("jdk.ThreadAllocationStatistics") @Label("SYNTHETIC allocation counter")
  static class Counter extends Event {
    public Thread thread;
    public long allocated;
  }
  @Name("jdk.ObjectAllocationInNewTLAB") @Label("SYNTHETIC TLAB trigger")
  static class NewTlab extends Event {
    public Class<?> objectClass;
    public long allocationSize;
    public long tlabSize;
  }
  @Name("jdk.ObjectAllocationOutsideTLAB") @Label("SYNTHETIC outside-TLAB object")
  static class OutsideTlab extends Event {
    public Class<?> objectClass;
    public long allocationSize;
  }

  static Sample sample(Class<?> type, long weight) {
    var event = new Sample(); event.objectClass = type; event.weight = weight; return event;
  }
  static Counter counter(long allocated) {
    var event = new Counter(); event.thread = Thread.currentThread(); event.allocated = allocated; return event;
  }
  // Repeated commits share the complete call stack, including line and bytecode index.
  static void stackA(Class<?>[] classes, long[] weights) {
    for (int i = 0; i < weights.length; i++) sample(classes[i], weights[i]).commit();
  }
  static void stackB() { sample(MarkerA.class, 700).commit(); }
  static void stackless(Class<?> type, long weight) {
    var event = new StacklessSample(); event.objectClass = type; event.weight = weight; event.commit();
  }
  static void sizes() {
    long[][] triggers = {{24, 4096}, {24, 8192}, {32, 16384}};
    for (long[] trigger : triggers) {
      var event = new NewTlab(); event.objectClass = MarkerA.class;
      event.allocationSize = trigger[0]; event.tlabSize = trigger[1]; event.commit();
    }
    for (int i = 0; i < 2; i++) {
      var event = new OutsideTlab(); event.objectClass = MarkerB.class;
      event.allocationSize = 1_000_000; event.commit();
    }
  }
  static void worker(boolean first) throws Exception {
    var earlyCounter = counter(first ? 1000 : 3000); earlyCounter.begin();
    var earlySample = sample(first ? MarkerA.class : MarkerB.class, first ? 11 : 37);
    earlySample.begin();
    // Separate start timestamps, then commit the earliest event last. Verification
    // below must prove the recording really reads out of timestamp order.
    Thread.sleep(2);
    if (first) {
      stackA(new Class<?>[] {MarkerA.class, MarkerA.class, MarkerB.class}, new long[] {100, 200, 900});
      stackB(); stackless(MarkerB.class, 50); sizes();
    } else {
      stackA(new Class<?>[] {MarkerA.class, MarkerA.class, MarkerA.class, MarkerA.class, MarkerB.class},
          new long[] {10, 20, 30, 40, 2000});
    }
    counter(first ? 1600 : 5000).commit();
    earlySample.end(); earlySample.commit(); earlyCounter.end(); earlyCounter.commit();
  }
  interface Action { void run() throws Exception; }
  static void runThread(String name, Action action) throws Exception {
    var failure = new AtomicReference<Throwable>();
    var thread = new Thread(() -> {
      try { action.run(); } catch (Throwable error) { failure.set(error); }
    }, name);
    thread.setDaemon(true); thread.start(); thread.join(10_000);
    if (thread.isAlive()) throw new AssertionError("Synthetic fixture worker timed out");
    if (failure.get() != null) throw new AssertionError("Synthetic fixture worker failed", failure.get());
  }
  static void verifyFixture(Path path) throws Exception {
    var samples = new HashMap<String,List<RecordedEvent>>();
    var counts = new HashMap<String,Integer>();
    try (var file = new RecordingFile(path)) {
      while (file.hasMoreEvents()) {
        var event = file.readEvent();
        String name = event.getEventType().getName();
        counts.merge(name, 1, Integer::sum);
        if (name.equals("jdk.ObjectAllocationSample"))
          samples.computeIfAbsent(event.getThread().getJavaName(), ignored -> new ArrayList<>()).add(event);
      }
    }
    var expectedCounts = Map.of("jdk.ObjectAllocationSample", 13, "jdk.ThreadAllocationStatistics", 4,
        "jdk.ObjectAllocationInNewTLAB", 3, "jdk.ObjectAllocationOutsideTLAB", 2);
    if (!counts.equals(expectedCounts)) throw new AssertionError("Unexpected fixture events: " + counts);
    for (String name : List.of(A, B)) {
      var events = samples.get(name);
      if (events == null || events.size() != 6) throw new AssertionError("Missing synthetic worker samples: " +
          samples.entrySet().stream().map(entry -> entry.getKey().codePoints().mapToObj(Integer::toHexString).toList() + "=" + entry.getValue().size()).toList());
      var earliest = events.stream().min(Comparator.comparing(RecordedEvent::getStartTime)).orElseThrow();
      long expectedFirst = name.equals(A) ? 11 : 37;
      if (earliest.getLong("weight") != expectedFirst || events.indexOf(earliest) == 0 ||
          !earliest.getStartTime().isBefore(events.getFirst().getStartTime()))
        throw new AssertionError("Fixture did not create the required out-of-order sample timestamps");
    }
    if (!samples.containsKey(C) || samples.get(C).size() != 1)
      throw new AssertionError("Missing first-sample-only synthetic worker");
    System.out.println("SYNTHETIC fixture verified: both workers read newer samples before their chronological first sample.");
  }
  static int deepSample(int remaining) {
    if (remaining == 0) { sample(MarkerA.class, 123).commit(); return 1; }
    return 1 + deepSample(remaining - 1);
  }
  static void truncatedFixture(Path path) throws Exception {
    try (var recording = new Recording()) {
      recording.setName("SYNTHETIC truncated-stack allocation fixture");
      recording.enable(Sample.class).withStackTrace(); recording.start();
      runThread("SYNTHETIC-TRUNCATED", () -> {
        if (deepSample(128) != 129) throw new AssertionError("Incorrect fixture recursion");
      });
      recording.stop(); recording.dump(path);
    }
    try (var file = new RecordingFile(path)) {
      if (!file.hasMoreEvents()) throw new AssertionError("Missing synthetic truncated event");
      var event = file.readEvent(); var stack = event.getStackTrace();
      if (file.hasMoreEvents() || !event.getEventType().getName().equals("jdk.ObjectAllocationSample") ||
          stack == null || !stack.isTruncated() || stack.getFrames().isEmpty() || stack.getFrames().size() > 32)
        throw new AssertionError("Fixture did not produce one sample with an actually truncated stack");
    }
    System.out.println("SYNTHETIC truncated fixture verified: recorded frames are present and the stack is truncated.");
  }
  public static void main(String[] arguments) throws Exception {
    if (arguments.length != 3) throw new IllegalArgumentException("Expected synthetic, empty and truncated recording paths");
    Path synthetic = Path.of(arguments[0]);
    try (var recording = new Recording()) {
      recording.setName("SYNTHETIC allocation-summary regression data; not an allocation measurement");
      // Enable custom event IDs, never the identically named real JDK events.
      recording.enable(Sample.class).withStackTrace();
      recording.enable(StacklessSample.class).withoutStackTrace();
      recording.enable(Counter.class).withoutStackTrace();
      recording.enable(NewTlab.class).withStackTrace();
      recording.enable(OutsideTlab.class).withStackTrace();
      recording.start();
      runThread(A, () -> worker(true)); runThread(B, () -> worker(false));
      runThread(C, () -> stackless(MarkerA.class, 29));
      recording.stop(); recording.dump(synthetic);
    }
    verifyFixture(synthetic);
    try (var empty = new Recording()) {
      empty.setName("SYNTHETIC empty allocation-summary fixture");
      empty.start(); empty.stop(); empty.dump(Path.of(arguments[1]));
    }
    truncatedFixture(Path.of(arguments[2]));
  }
}
