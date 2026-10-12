#!/usr/bin/env bash
# Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0
set -euo pipefail
if [[ $# -lt 7 ]]; then
  echo "Usage: $0 <classpath-file> <binary.jar> <sources.jar> <profile.properties> <fresh-output> <sources-sha256> <target-java> [exact-entry.java ...]" >&2
  exit 2
fi
classpath=$(cat "$1")
shift
repository=$(cd "$(dirname "$0")/../.." && pwd)
output=$4
classes="${output}.runner-classes"
if [[ -e "$output" || -e "$classes" ]]; then
  echo "Choose a fresh result and runner-classes path: $output" >&2
  exit 2
fi
mkdir -p "$classes"
"${MATH_QA_JAVAC:-javac}" --release 25 -cp "$classpath" -d "$classes" \
  "$repository/qa/math-optimization/src/main/java/org/sandbox/math/qa/CorpusFiles.java" \
  "$repository/qa/math-optimization/src/main/java/org/sandbox/math/qa/ProjectMathScan.java"
"${MATH_QA_JAVA:-java}" "-Xmx${MATH_QA_HEAP:-512m}" -cp "$classes:$classpath" \
  org.sandbox.math.qa.ProjectMathScan "$@"
