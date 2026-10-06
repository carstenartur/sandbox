#!/usr/bin/env bash
# Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0
set -euo pipefail

if [[ $# != 3 ]]; then
  echo "Usage: $0 <test|references|corpus|benchmark> <classpath-file> <fresh-output-directory>" >&2
  exit 2
fi
mode=$1
case "$mode" in test|references|corpus|benchmark) ;; *) echo "Unknown mode: $mode" >&2; exit 2 ;; esac
classpath=$(cat "$2")
output=$(mkdir -p "$3" && cd "$3" && pwd)
repository=$(cd "$(dirname "$0")/../.." && pwd)
java_command=${MATH_QA_JAVA:-java}
javac_command=${MATH_QA_JAVAC:-javac}
classes="$output/classes"
if [[ -e "$classes" ]]; then
  echo "Refusing to replace compiled evidence; choose a fresh output directory: $classes" >&2
  exit 2
fi
mkdir -p "$classes"

# Reuse the existing fixture source; do not duplicate it or shell out to Git.
"$javac_command" --release 25 -cp "$classpath" -d "$classes" \
  -processor org.openjdk.jmh.generators.BenchmarkProcessor \
  "$repository"/sandbox_math_cleanup/src/org/sandbox/jdt/internal/corext/fix/math/*.java \
  "$repository"/sandbox_common_test/src/org/sandbox/jdt/triggerpattern/test/policy/PinnedGitRepository.java \
  "$repository"/sandbox_math_cleanup_test/src/org/sandbox/jdt/math/tests/MathTestSupport.java \
  "$repository"/sandbox_math_cleanup_test/src/org/sandbox/jdt/math/tests/MathCorpusIntegrationTest.java \
  "$repository"/sandbox-benchmarks/src/main/java/org/sandbox/benchmarks/MathematicsOptimizationBenchmark.java \
  "$repository"/qa/math-optimization/src/main/java/org/sandbox/math/qa/*.java \
  "$repository"/qa/math-optimization/src/test/java/org/sandbox/math/qa/*.java 2>&1 | tee "$output/compile.log"

cd "$repository"
case "$mode" in
  test)
    options=()
    if [[ -n ${MATH_QA_BC_MIRROR:-} ]]; then
      options+=("-Dmath.qa.bcMirror=$MATH_QA_BC_MIRROR")
      options+=("-Dmath.qa.pinnedReportDirectory=$output/pinned-reference")
    fi
    if [[ -n ${MATH_QA_WRITE_FIXTURES:-} ]]; then
      options+=("-Dmath.qa.fixtureDirectory=$MATH_QA_WRITE_FIXTURES")
    fi
    "$java_command" "-Xmx${MATH_QA_HEAP:-512m}" "-Dmath.qa.sandboxRoot=$repository" -cp "$classes:$classpath" \
      org.sandbox.math.qa.CaptureMathReceipts "$output/receipts-before"
    "$java_command" "-Xmx${MATH_QA_HEAP:-512m}" "${options[@]}" -cp "$classes:$classpath" \
      org.junit.platform.console.ConsoleLauncher execute \
      --select-class org.sandbox.jdt.math.tests.MathCorpusIntegrationTest --select-package org.sandbox.math.qa \
      --reports-dir "$output/junit" --details=summary 2>&1 | tee "$output/test.log"
    "$java_command" "-Xmx${MATH_QA_HEAP:-512m}" "-Dmath.qa.sandboxRoot=$repository" -cp "$classes:$classpath" \
      org.sandbox.math.qa.CaptureMathReceipts "$output/receipts-after" "$output/receipts-before/implementation.properties"
    ;;
  references)
    "$java_command" "-Xmx${MATH_QA_HEAP:-512m}" -cp "$classes:$classpath" org.sandbox.math.qa.MathCorpusRunner \
      references "${MATH_QA_CACHE:-$output/cache}" "$output/upstream" 2>&1 | tee "$output/references.log"
    ;;
  corpus)
    "$java_command" "-Xmx${MATH_QA_HEAP:-512m}" "-Dmath.qa.sandboxRoot=$repository" -cp "$classes:$classpath" org.sandbox.math.qa.MathCorpusRunner \
      "${MATH_QA_CACHE:-$output/cache}" "$output/corpus" 2>&1 | tee "$output/corpus.log"
    ;;
  benchmark)
    # Exclude Eclipse and Regelsuche dependencies from the measured runtime.
    runtime="$classes"
    IFS=: read -r -a entries <<< "$classpath"
    for entry in "${entries[@]}"; do
      case "$(basename "$entry")" in
        jmh-core-*.jar|jopt-simple-*.jar|commons-math3-*.jar) runtime="$runtime:$entry" ;;
      esac
    done
    parameters=()
    if [[ -n ${MATH_QA_BENCHMARK_FIXTURES:-} ]]; then
      parameters+=("-p" "fixtureDirectory=$MATH_QA_BENCHMARK_FIXTURES/small,$MATH_QA_BENCHMARK_FIXTURES/wide")
    fi
    fixture_root=${MATH_QA_BENCHMARK_FIXTURES:-$repository/qa/math-optimization/fixtures/local}
    "$java_command" -Xmx384m -cp "$runtime" org.sandbox.math.qa.CaptureFixtureReceipts \
      "$fixture_root" "$output/fixtures-before.properties"
    "$java_command" -Xmx384m -cp "$runtime" org.openjdk.jmh.Main '.*MathematicsOptimizationBenchmark.*' \
      "${parameters[@]}" -prof gc -rf json -rff "$output/jmh.json" -jvmArgs '-Xms256m -Xmx256m' 2>&1 | tee "$output/jmh.log"
    "$java_command" -Xmx384m -cp "$runtime" org.sandbox.math.qa.CaptureFixtureReceipts \
      "$fixture_root" "$output/fixtures-after.properties" "$output/fixtures-before.properties"
    ;;
esac
