#!/bin/sh
set -eu

tools_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -n "${JAVA_HOME:-}" ]; then
  test_java="$JAVA_HOME/bin/java"
  test_javac="$JAVA_HOME/bin/javac"
else
  test_java=$(command -v java)
  test_javac=$(command -v javac)
fi
test_dir=$(mktemp -d "${TMPDIR:-/tmp}/cadenza-jfr-tests.XXXXXX")
trap 'rm -rf "$test_dir"' EXIT HUP INT TERM
mkdir "$test_dir/classes"
"$test_javac" -J-Xmx128m --release 25 -encoding UTF-8 -d "$test_dir/classes" \
  "$tools_dir/CadenzaAllocationSummary.java" "$tools_dir/tests/SyntheticAllocationRecording.java"
python3 -I "$tools_dir/tests/test_allocation_summary.py" "$test_java" "$test_dir/classes" "$test_dir"
