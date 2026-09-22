import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import jdk.jfr.consumer.*;

/** Streams JFR allocation samples and allocation counters; never retains RecordedEvents. */
public final class CadenzaAllocationSummary {
  record ClassInfo(long id, String name, Long loaderId, String loaderName) {}
  record FrameInfo(String declaringClass, Long classId, Long loaderId, String method,
      String descriptor, int line, int bci, String frameType, boolean javaFrame, Boolean hidden) {}
  record StackInfo(boolean missing, boolean truncated, List<FrameInfo> frames) {}
  record Counter(Instant time, long allocated) {}
  record SizeSite(String eventType, long threadId, ClassInfo type, StackInfo stack, long allocationSize) {}
  static final class SizeTotals {
    long count, tlabSum, tlabMin = Long.MAX_VALUE, tlabMax;
    Instant first, last;
    void add(Instant time, Long tlabSize) {
      count++;
      if (first == null || time.isBefore(first)) first = time;
      if (last == null || time.isAfter(last)) last = time;
      if (tlabSize != null) {
        tlabSum = Math.addExact(tlabSum, tlabSize);
        tlabMin = Math.min(tlabMin, tlabSize); tlabMax = Math.max(tlabMax, tlabSize);
      }
    }
    Map<String,Object> json(SizeSite site) {
      boolean hasTlab = site.eventType().equals("jdk.ObjectAllocationInNewTLAB");
      return obj("eventType", site.eventType(), "recordedThreadId", site.threadId(), "class", classJson(site.type()),
          "allocationSize", site.allocationSize(), "count", count,
          "sumRecordedObjectSizes", Math.multiplyExact(count, site.allocationSize()),
          "tlabSizeSum", hasTlab ? tlabSum : null, "tlabSizeMin", hasTlab ? tlabMin : null,
          "tlabSizeMax", hasTlab ? tlabMax : null, "firstTime", first, "lastTime", last,
          "missingStack", site.stack().missing(), "truncatedStack", site.stack().truncated(),
          "frames", framesJson(site.stack()));
    }
  }
  record SampleInfo(long ordinal, long threadId, String threadName, Instant time,
      long weight, ClassInfo type, StackInfo stack) {
    Map<String,Object> json() {
      return obj("ordinal", ordinal, "recordedThreadId", threadId, "threadName", threadName,
          "time", time, "weight", weight, "class", classJson(type),
          "missingStack", stack.missing(), "truncatedStack", stack.truncated(), "frames", framesJson(stack));
    }
  }
  record SampleIndex(Map<Long,SampleInfo> firstByThread, List<SampleInfo> largest) {}

  // A small first streaming pass locates the chronological first sample of each thread.
  // JFR's first weight may include allocations before recording started. Keep its exact
  // metadata instead of silently treating it as one class's measurement-time allocation.
  static SampleIndex indexSamples(Path path) throws Exception {
    var first = new HashMap<Long,SampleInfo>();
    var largest = new PriorityQueue<SampleInfo>(Comparator.comparingLong(SampleInfo::weight));
    long ordinal = 0;
    try (var file = new RecordingFile(path)) {
      while (file.hasMoreEvents()) {
        var event = file.readEvent();
        if (!event.getEventType().getName().equals("jdk.ObjectAllocationSample")) continue;
        var thread = event.getThread();
        long id = thread == null ? -1L : thread.getId();
        var time = event.getStartTime();
        long weight = event.getLong("weight");
        var previous = first.get(id);
        boolean firstCandidate = previous == null || time.isBefore(previous.time());
        boolean largestCandidate = largest.size() < 12 || weight > largest.peek().weight();
        if (firstCandidate || largestCandidate) {
          var sample = new SampleInfo(ordinal, id, thread == null ? null : thread.getJavaName(), time,
              weight, classInfo(event.getClass("objectClass")), stackInfo(event.getStackTrace()));
          if (firstCandidate) first.put(id, sample);
          if (largestCandidate) {
            largest.add(sample);
            if (largest.size() > 12) largest.remove();
          }
        }
        ordinal++;
      }
    }
    var ordered = new ArrayList<>(largest);
    ordered.sort(Comparator.comparingLong(SampleInfo::weight).reversed());
    return new SampleIndex(first, ordered);
  }

  static final class Totals {
    long count, weight, minWeight = Long.MAX_VALUE, maxWeight;
    Instant first, last;
    void add(long value, Instant time) {
      count++;
      weight = Math.addExact(weight, value);
      minWeight = Math.min(minWeight, value);
      maxWeight = Math.max(maxWeight, value);
      if (first == null || time.isBefore(first)) first = time;
      if (last == null || time.isAfter(last)) last = time;
    }
    Map<String,Object> json() {
      return obj("count", count, "weight", weight, "minWeight", count == 0 ? null : minWeight,
          "maxWeight", count == 0 ? null : maxWeight, "firstTime", first, "lastTime", last);
    }
  }

  static final class ClassSummary {
    final ClassInfo type;
    final Totals totals = new Totals();
    final Map<StackInfo, Totals> sites = new HashMap<>();
    ClassSummary(ClassInfo type) { this.type = type; }
    Map<String,Object> json() {
      var result = obj("class", classJson(type), "samples", totals.json());
      var ordered = new ArrayList<>(sites.entrySet());
      ordered.sort(Comparator.<Map.Entry<StackInfo,Totals>>comparingLong(e -> e.getValue().weight).reversed());
      var rows = new ArrayList<Object>();
      for (var entry : ordered) {
        var stack = entry.getKey();
        var row = new LinkedHashMap<String,Object>(entry.getValue().json());
        row.put("missingStack", stack.missing());
        row.put("truncatedStack", stack.truncated());
        row.put("frames", framesJson(stack));
        rows.add(row);
      }
      result.put("sites", rows);
      return result;
    }
  }

  static final class ThreadSummary {
    final long id;
    Long javaId, osId;
    Boolean virtual;
    final Set<String> javaNames = new LinkedHashSet<>(), osNames = new LinkedHashSet<>();
    final Totals totals = new Totals(), missing = new Totals(), truncated = new Totals();
    final Map<ClassInfo,ClassSummary> classes = new HashMap<>();
    final List<Counter> counters = new ArrayList<>();
    ThreadSummary(long id) { this.id = id; }
    void observe(RecordedThread thread) {
      if (thread == null) return;
      javaId = thread.getJavaThreadId(); osId = thread.getOSThreadId(); virtual = thread.isVirtual();
      if (thread.getJavaName() != null) javaNames.add(thread.getJavaName());
      if (thread.getOSName() != null) osNames.add(thread.getOSName());
    }
    Map<String,Object> json() {
      var result = obj("recordedThreadId", id, "javaThreadId", javaId, "osThreadId", osId,
          "virtual", virtual, "javaNames", javaNames, "osNames", osNames,
          "samples", totals.json(), "missingStackSamples", missing.json(), "truncatedStackSamples", truncated.json());
      counters.sort(Comparator.comparing(Counter::time));
      var observations = new ArrayList<Object>();
      for (var point : counters) observations.add(obj("time", point.time(), "allocated", point.allocated()));
      result.put("allocationCounters", observations);
      if (!counters.isEmpty()) {
        var first = counters.getFirst(); var last = counters.getLast();
        result.put("allocationCounterDelta", obj("observations", counters.size(), "firstTime", first.time(),
            "lastTime", last.time(), "firstAllocated", first.allocated(), "lastAllocated", last.allocated(),
            "bytes", last.allocated() - first.allocated()));
      }
      var ordered = new ArrayList<>(classes.values());
      ordered.sort(Comparator.comparingLong((ClassSummary c) -> c.totals.weight).reversed());
      result.put("classes", ordered.stream().map(ClassSummary::json).toList());
      return result;
    }
  }

  static ClassInfo classInfo(RecordedClass type) {
    if (type == null) return new ClassInfo(-1L, null, null, null);
    var loader = type.getClassLoader();
    return new ClassInfo(type.getId(), type.getName(), loader == null ? null : loader.getId(),
        loader == null ? null : loader.getName());
  }
  static Map<String,Object> classJson(ClassInfo type) {
    return obj("id", type.id(), "name", type.name(), "loaderId", type.loaderId(), "loaderName", type.loaderName());
  }
  static StackInfo stackInfo(RecordedStackTrace stack) {
    if (stack == null) return new StackInfo(true, false, List.of());
    var frames = new ArrayList<FrameInfo>();
    for (var frame : stack.getFrames()) {
      var method = frame.getMethod();
      var type = method == null ? null : method.getType();
      var loader = type == null ? null : type.getClassLoader();
      frames.add(new FrameInfo(type == null ? null : type.getName(), type == null ? null : type.getId(),
          loader == null ? null : loader.getId(), method == null ? null : method.getName(),
          method == null ? null : method.getDescriptor(), frame.getLineNumber(), frame.getBytecodeIndex(),
          frame.getType(), frame.isJavaFrame(), method == null ? null : method.isHidden()));
    }
    return new StackInfo(false, stack.isTruncated(), List.copyOf(frames));
  }

  static List<Object> framesJson(StackInfo stack) {
    var frames = new ArrayList<Object>();
    for (var f : stack.frames()) frames.add(obj("class", f.declaringClass(), "classId", f.classId(),
        "classLoaderId", f.loaderId(), "method", f.method(), "descriptor", f.descriptor(),
        "line", f.line(), "bci", f.bci(), "frameType", f.frameType(), "javaFrame", f.javaFrame(),
        "hiddenMethod", f.hidden()));
    return frames;
  }

  static Map<String,Object> obj(Object... pairs) {
    var map = new LinkedHashMap<String,Object>();
    for (int i = 0; i < pairs.length; i += 2) map.put((String)pairs[i], pairs[i + 1]);
    return map;
  }

  // A tiny dependency-free JSON writer. JFR strings are data; escape every JSON control.
  static void json(PrintWriter out, Object value) {
    if (value == null) { out.print("null"); return; }
    if (value instanceof Number || value instanceof Boolean) { out.print(value); return; }
    if (value instanceof Map<?,?> map) {
      out.print('{'); boolean comma = false;
      for (var entry : map.entrySet()) {
        if (comma) out.print(','); comma = true;
        json(out, entry.getKey().toString()); out.print(':'); json(out, entry.getValue());
      }
      out.print('}'); return;
    }
    if (value instanceof Iterable<?> list) {
      out.print('['); boolean comma = false;
      for (var element : list) { if (comma) out.print(','); comma = true; json(out, element); }
      out.print(']'); return;
    }
    out.print('"');
    for (char c : value.toString().toCharArray()) {
      switch (c) {
        case '"' -> out.print("\\\""); case '\\' -> out.print("\\\\");
        case '\n' -> out.print("\\n"); case '\r' -> out.print("\\r"); case '\t' -> out.print("\\t");
        case '\b' -> out.print("\\b"); case '\f' -> out.print("\\f");
        default -> { if (c < 0x20 || Character.isSurrogate(c)) out.printf("\\u%04x", (int)c); else out.print(c); }
      }
    }
    out.print('"');
  }

  public static void main(String[] arguments) throws Exception {
    boolean excludeFirst = arguments.length == 2 && arguments[0].equals("--exclude-first-per-thread");
    if (arguments.length != 1 && !excludeFirst) throw new IllegalArgumentException(
        "Usage: java CadenzaAllocationSummary.java [--exclude-first-per-thread] recording.jfr > summary.json");
    var path = Path.of(arguments[arguments.length - 1]).toAbsolutePath();
    var index = indexSamples(path);
    long sampleOrdinal = 0;
    var rawTotals = new Totals();
    var counts = new TreeMap<String,Long>();
    var threads = new HashMap<Long,ThreadSummary>();
    var sizeEvents = new HashMap<SizeSite,SizeTotals>();
    var totals = new Totals();
    Instant firstEvent = null, lastEvent = null;
    try (var recording = new RecordingFile(path)) {
      while (recording.hasMoreEvents()) {
        var event = recording.readEvent();
        var name = event.getEventType().getName();
        var time = event.getStartTime();
        counts.merge(name, 1L, Long::sum);
        if (firstEvent == null || time.isBefore(firstEvent)) firstEvent = time;
        if (lastEvent == null || time.isAfter(lastEvent)) lastEvent = time;
        if (!name.equals("jdk.ObjectAllocationSample") && !name.equals("jdk.ThreadAllocationStatistics") &&
            !name.equals("jdk.ObjectAllocationInNewTLAB") && !name.equals("jdk.ObjectAllocationOutsideTLAB")) continue;
        var recordedThread = name.equals("jdk.ThreadAllocationStatistics") ? event.getThread("thread") : event.getThread();
        long id = recordedThread == null ? -1L : recordedThread.getId();
        var thread = threads.computeIfAbsent(id, ThreadSummary::new);
        thread.observe(recordedThread);
        if (name.equals("jdk.ThreadAllocationStatistics")) {
          thread.counters.add(new Counter(time, event.getLong("allocated")));
          continue;
        }
        if (!name.equals("jdk.ObjectAllocationSample")) {
          var site = new SizeSite(name, id, classInfo(event.getClass("objectClass")),
              stackInfo(event.getStackTrace()), event.getLong("allocationSize"));
          Long tlabSize = name.equals("jdk.ObjectAllocationInNewTLAB") ? event.getLong("tlabSize") : null;
          sizeEvents.computeIfAbsent(site, ignored -> new SizeTotals()).add(time, tlabSize);
          continue;
        }
        long weight = event.getLong("weight");
        rawTotals.add(weight, time);
        boolean firstSample = sampleOrdinal++ == index.firstByThread().get(id).ordinal();
        if (excludeFirst && firstSample) continue;
        var type = classInfo(event.getClass("objectClass"));
        var stack = stackInfo(event.getStackTrace());
        totals.add(weight, time); thread.totals.add(weight, time);
        if (stack.missing()) thread.missing.add(weight, time);
        if (stack.truncated()) thread.truncated.add(weight, time);
        var classSummary = thread.classes.computeIfAbsent(type, ClassSummary::new);
        classSummary.totals.add(weight, time);
        classSummary.sites.computeIfAbsent(stack, ignored -> new Totals()).add(weight, time);
      }
    }
    var ordered = new ArrayList<>(threads.values());
    ordered.sort(Comparator.comparingLong((ThreadSummary thread) -> thread.totals.weight).reversed());
    var result = obj("recording", path.toString(), "firstEventTime", firstEvent, "lastEventTime", lastEvent,
        "eventCounts", counts, "rawAllocationSamples", rawTotals.json(), "allocationSamples", totals.json(),
        "excludedFirstSamplePerThread", excludeFirst,
        "firstSamplePerThread", index.firstByThread().values().stream().sorted(Comparator.comparingLong(SampleInfo::threadId))
            .map(SampleInfo::json).toList(),
        "largestIndividualSamplesBeforeExclusion", index.largest().stream().map(SampleInfo::json).toList(),
        "weightMeaning", "Sample weights estimate allocation pressure in bytes; they are not object sizes or exact object counts.",
        "allocationCounterMeaning", "Approximate bytes allocated since thread start; deltas span first/last observed counters, not exact JMH iteration edges.",
        "allocationSizeEventMeaning", "NewTLAB records only its triggering object, not all objects in the TLAB. tlabSizeSum is buffer capacity, never class allocation weight. Counts/sizes belong only to recorded events.",
        "allocationSizeEvents", sizeEvents.entrySet().stream()
            .sorted(Comparator.<Map.Entry<SizeSite,SizeTotals>>comparingLong(e -> e.getValue().count).reversed())
            .map(e -> e.getValue().json(e.getKey())).toList(),
        "threads", ordered.stream().map(ThreadSummary::json).toList());
    var out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8)));
    json(out, result); out.println(); out.flush();
    if (out.checkError()) throw new java.io.IOException("Failed to write summary JSON");
  }
}
