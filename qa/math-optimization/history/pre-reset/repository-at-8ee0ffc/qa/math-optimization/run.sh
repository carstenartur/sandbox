#!/usr/bin/env bash
# Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0
set -euo pipefail

if [[ $# != 3 ]]; then
  echo "Usage: $0 <test|corpus|benchmark> <classpath-file> <output-directory>" >&2
  exit 2
fi
mode=$1
case "$mode" in test|corpus|benchmark) ;; *) echo "Unknown mode: $mode" >&2; exit 2 ;; esac
classpath=$(cat "$2")
output=$(mkdir -p "$3" && cd "$3" && pwd)
repository=$(cd "$(dirname "$0")/../.." && pwd)
java_command=${MATH_QA_JAVA:-java}
javac_command=${MATH_QA_JAVAC:-javac}
classes="$output/classes"
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
  "$repository"/qa/math-optimization/src/test/java/org/sandbox/math/qa/*.java

cd "$repository"
case "$mode" in
  test)
    options=()
    if [[ -n ${MATH_QA_BC_MIRROR:-} ]]; then
      options+=("-Dmath.qa.bcMirror=$MATH_QA_BC_MIRROR")
    fi
    if [[ -n ${MATH_QA_WRITE_FIXTURES:-} ]]; then
      options+=("-Dmath.qa.fixtureDirectory=$MATH_QA_WRITE_FIXTURES")
    fi
    "$java_command" "${options[@]}" -cp "$classes:$classpath" \
      org.junit.platform.console.ConsoleLauncher execute \
      --select-package org.sandbox.jdt.math.tests --select-package org.sandbox.math.qa \
      --reports-dir "$output/junit" --details=summary
    ;;
  corpus)
    "$java_command" -Xmx2g "-Dmath.qa.sandboxRoot=$repository" -cp "$classes:$classpath" org.sandbox.math.qa.MathCorpusRunner \
      "$output/cache" "$output/corpus"
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
    "$java_command" -cp "$runtime" org.openjdk.jmh.Main '.*MathematicsOptimizationBenchmark.*' \
      -prof gc -rf json -rff "$output/jmh.json" -jvmArgs '-Xms256m -Xmx256m'
    ;;
esac
