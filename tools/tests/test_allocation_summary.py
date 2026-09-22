#!/usr/bin/env python3
"""End-to-end assertions on SYNTHETIC JFR data, parsed with Python's independent JSON parser."""
from datetime import datetime
import json
from pathlib import Path
import subprocess
import sys


JAVA, CLASSES, TEMP = sys.argv[1:]
TEMP = Path(TEMP)
A = 'SYNTHETIC-A-"\\\n\t\b\f\r\u0001-\u03a9'
B = "SYNTHETIC-B"
C = "SYNTHETIC-FIRST-ONLY"
CLASS_A = "SyntheticAllocationRecording$MarkerA"
CLASS_B = "SyntheticAllocationRecording$MarkerB"
WEIGHTS = [11, 100, 200, 900, 700, 50, 37, 10, 20, 30, 40, 2000, 29]


def run(main, *arguments, jvm_options=()):
    result = subprocess.run(
        [JAVA, "-Xms16m", "-Xmx128m", *jvm_options, "-cp", CLASSES, main, *map(str, arguments)],
        check=False, capture_output=True, text=True, encoding="utf-8", timeout=30,
    )
    if result.returncode:
        raise AssertionError(f"{main} exited {result.returncode}\n{result.stdout}\n{result.stderr}")
    return result.stdout


def non_json_number(value):
    raise AssertionError(f"Invalid JSON numeric constant {value}")


def summary(recording, corrected=False):
    options = ["--exclude-first-per-thread"] if corrected else []
    text = run("CadenzaAllocationSummary", *options, recording)
    return json.loads(text, parse_constant=non_json_number), text


def threads(report):
    return {row["javaNames"][0]: row for row in report["threads"]}


def classes(thread):
    return {row["class"]["name"]: row for row in thread["classes"]}


def assert_totals(totals, count, weight):
    assert totals["count"] == count, totals
    assert totals["weight"] == weight, totals
    if count == 0:
        assert all(totals[key] is None for key in ("minWeight", "maxWeight", "firstTime", "lastTime")), totals
    else:
        assert datetime.fromisoformat(totals["firstTime"].replace("Z", "+00:00")) <= datetime.fromisoformat(totals["lastTime"].replace("Z", "+00:00")), totals


def reconcile(report):
    for metric in ("count", "weight"):
        assert sum(t["samples"][metric] for t in report["threads"]) == report["allocationSamples"][metric]
        for thread in report["threads"]:
            assert sum(c["samples"][metric] for c in thread["classes"]) == thread["samples"][metric]
            for klass in thread["classes"]:
                assert sum(site[metric] for site in klass["sites"]) == klass["samples"][metric]


# JFR's native thread-name encoder can replace supplementary characters. Exercise
# JSON's surrogate-pair escaping through the independently supplied recording path.
synthetic = TEMP / "SYNTHETIC-\U0001f600-allocation-fixture.jfr"
empty = TEMP / "SYNTHETIC-empty-fixture.jfr"
truncated = TEMP / "SYNTHETIC-truncated-fixture.jfr"
fixture_output = run("SyntheticAllocationRecording", synthetic, empty, truncated,
                     jvm_options=["-XX:FlightRecorderOptions=stackdepth=32"])
assert "both workers read newer samples before their chronological first sample" in fixture_output
assert "recorded frames are present and the stack is truncated" in fixture_output
raw, raw_text = summary(synthetic)
corrected, corrected_text = summary(synthetic, True)
assert raw["excludedFirstSamplePerThread"] is False
assert corrected["excludedFirstSamplePerThread"] is True
assert Path(raw["recording"]).resolve() == synthetic.resolve()
assert corrected["recording"] == raw["recording"]
assert raw["eventCounts"] == corrected["eventCounts"] == {
    "jdk.ObjectAllocationSample": 13,
    "jdk.ThreadAllocationStatistics": 4,
    "jdk.ObjectAllocationInNewTLAB": 3,
    "jdk.ObjectAllocationOutsideTLAB": 2,
}
assert_totals(raw["allocationSamples"], 13, 4127)
assert_totals(corrected["allocationSamples"], 10, 4050)
assert raw["rawAllocationSamples"] == corrected["rawAllocationSamples"] == raw["allocationSamples"]
first = {sample["threadName"]: sample for sample in raw["firstSamplePerThread"]}
assert {name: sample["weight"] for name, sample in first.items()} == {A: 11, B: 37, C: 29}
assert raw["firstSamplePerThread"] == corrected["firstSamplePerThread"]
assert sum(sample["weight"] for sample in first.values()) == 4127 - 4050
assert len(first) == 13 - 10
assert raw["largestIndividualSamplesBeforeExclusion"] == corrected["largestIndividualSamplesBeforeExclusion"]
assert [sample["weight"] for sample in raw["largestIndividualSamplesBeforeExclusion"]] == sorted(WEIGHTS, reverse=True)[:12]

raw_threads, corrected_threads = threads(raw), threads(corrected)
assert set(raw_threads) == set(corrected_threads) == {A, B, C}
for name, raw_count, raw_weight, corrected_count, corrected_weight in [
    (A, 6, 1961, 5, 1950), (B, 6, 2137, 5, 2100), (C, 1, 29, 0, 0)
]:
    assert_totals(raw_threads[name]["samples"], raw_count, raw_weight)
    assert_totals(corrected_threads[name]["samples"], corrected_count, corrected_weight)
    assert first[name]["recordedThreadId"] == raw_threads[name]["recordedThreadId"]
for report in (raw, corrected):
    reconcile(report)
assert corrected_threads[C]["classes"] == []
assert corrected_threads[C]["allocationCounters"] == []
assert "allocationCounterDelta" not in corrected_threads[C]

# Class and complete-stack aggregation are separate axes. The same site emits
# two classes; one class appears at two sites; repeated identical sites must merge.
a_classes = classes(corrected_threads[A])
b_classes = classes(corrected_threads[B])
assert_totals(a_classes[CLASS_A]["samples"], 3, 1000)
assert_totals(a_classes[CLASS_B]["samples"], 2, 950)
assert_totals(b_classes[CLASS_A]["samples"], 4, 100)
assert_totals(b_classes[CLASS_B]["samples"], 1, 2000)
assert [site["weight"] for site in a_classes[CLASS_A]["sites"]] == [700, 300]
assert [site["count"] for site in a_classes[CLASS_A]["sites"]] == [1, 2]
for site, method in zip(a_classes[CLASS_A]["sites"], ("stackB", "stackA")):
    assert not site["missingStack"]
    assert not site["truncatedStack"]
    matching = [f for f in site["frames"] if f["class"] == "SyntheticAllocationRecording" and f["method"] == method]
    assert matching, site
    assert matching[0]["descriptor"].endswith("V")
    assert matching[0]["line"] > 0
    assert matching[0]["classId"] is not None
    assert matching[0]["classLoaderId"] is not None
assert [site["weight"] for site in b_classes[CLASS_A]["sites"]] == [100]
assert a_classes[CLASS_A]["class"]["id"] != a_classes[CLASS_B]["class"]["id"]
assert_totals(corrected_threads[A]["missingStackSamples"], 1, 50)
assert_totals(raw_threads[C]["missingStackSamples"], 1, 29)
assert_totals(corrected_threads[C]["missingStackSamples"], 0, 0)
missing_site = next(site for site in a_classes[CLASS_B]["sites"] if site["missingStack"])
assert missing_site["weight"] == 50 and missing_site["frames"] == []

# Synthetic counters are also committed out of chronological order. Their delta
# is independent of sample correction and the recording's JMH/non-JMH boundaries.
for name, start, end in ((A, 1000, 1600), (B, 3000, 5000)):
    before, after = raw_threads[name], corrected_threads[name]
    assert before["allocationCounters"] == after["allocationCounters"]
    assert before["allocationCounterDelta"] == after["allocationCounterDelta"]
    assert [point["allocated"] for point in after["allocationCounters"]] == [start, end]
    delta = after["allocationCounterDelta"]
    assert (delta["observations"], delta["firstAllocated"], delta["lastAllocated"], delta["bytes"]) == (2, start, end, end - start)

# TLAB capacity must never become class sample weight or the triggering object's size.
assert raw["allocationSizeEvents"] == corrected["allocationSizeEvents"]
size_rows = {(row["eventType"], row["allocationSize"]): row for row in corrected["allocationSizeEvents"]}
assert len(size_rows) == len(corrected["allocationSizeEvents"]) == 3
for allocation_size, count, object_sum, capacity, minimum, maximum in (
    (24, 2, 48, 12288, 4096, 8192), (32, 1, 32, 16384, 16384, 16384)
):
    row = size_rows["jdk.ObjectAllocationInNewTLAB", allocation_size]
    assert (row["count"], row["sumRecordedObjectSizes"], row["tlabSizeSum"], row["tlabSizeMin"], row["tlabSizeMax"]) == (count, object_sum, capacity, minimum, maximum)
    assert row["class"]["name"] == CLASS_A
    assert row["recordedThreadId"] == corrected_threads[A]["recordedThreadId"]
outside = size_rows["jdk.ObjectAllocationOutsideTLAB", 1_000_000]
assert (outside["count"], outside["sumRecordedObjectSizes"]) == (2, 2_000_000)
assert outside["class"]["name"] == CLASS_B
assert all(outside[key] is None for key in ("tlabSizeSum", "tlabSizeMin", "tlabSizeMax"))

# Parsing alone rejects unescaped JSON controls; exact round-trip preserves all
# controls and quote/backslash in a JFR name, plus the supplementary path character.
for text in (raw_text, corrected_text):
    for escape in ('\\"', '\\\\', '\\n', '\\t', '\\b', '\\f', '\\r', '\\u0001', '\\ud83d\\ude00'):
        assert escape in text, (escape, text)
for corrected_mode in (False, True):
    empty_report, _ = summary(empty, corrected_mode)
    assert_totals(empty_report["allocationSamples"], 0, 0)
    assert_totals(empty_report["rawAllocationSamples"], 0, 0)
    for field in ("threads", "allocationSizeEvents", "firstSamplePerThread", "largestIndividualSamplesBeforeExclusion"):
        assert empty_report[field] == [], empty_report
    assert empty_report["eventCounts"] == {}
    assert empty_report["firstEventTime"] is None and empty_report["lastEventTime"] is None

# Real allocation profiles often have truncated stacks. Preserve the available
# frames and weights rather than treating truncation as a missing stack.
truncated_raw, _ = summary(truncated)
truncated_corrected, _ = summary(truncated, True)
assert truncated_raw["eventCounts"] == {"jdk.ObjectAllocationSample": 1}
assert_totals(truncated_raw["allocationSamples"], 1, 123)
assert_totals(truncated_corrected["allocationSamples"], 0, 0)
assert truncated_raw["rawAllocationSamples"] == truncated_corrected["rawAllocationSamples"]
truncated_thread = threads(truncated_raw)["SYNTHETIC-TRUNCATED"]
assert_totals(truncated_thread["truncatedStackSamples"], 1, 123)
assert_totals(truncated_thread["missingStackSamples"], 0, 0)
truncated_site = classes(truncated_thread)[CLASS_A]["sites"][0]
assert truncated_site["truncatedStack"] and not truncated_site["missingStack"]
assert 0 < len(truncated_site["frames"]) <= 32
assert any(frame["method"] == "deepSample" for frame in truncated_site["frames"])
assert truncated_raw["firstSamplePerThread"] == truncated_corrected["firstSamplePerThread"]
assert truncated_raw["firstSamplePerThread"][0]["frames"] == truncated_site["frames"]
assert truncated_corrected["firstSamplePerThread"][0]["truncatedStack"]
corrected_truncated_thread = threads(truncated_corrected)["SYNTHETIC-TRUNCATED"]
assert_totals(corrected_truncated_thread["truncatedStackSamples"], 0, 0)
assert corrected_truncated_thread["classes"] == []
reconcile(truncated_raw)
reconcile(truncated_corrected)

print("PASS: synthetic JFR chronology, correction, aggregation, counters, TLAB/object sizes, JSON escaping, empty and truncated recordings")
