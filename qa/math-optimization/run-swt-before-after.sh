#!/usr/bin/env bash
# Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0
# Process adapter only: mathematical/source checks and XML comparison stay in JUnit.
set -euo pipefail
if (($# != 3)); then
  echo 'Usage: run-swt-before-after.sh SWT_CHECKOUT OUTPUT_DIRECTORY MAVEN' >&2
  exit 2
fi
SANDBOX_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)
SWT_CHECKOUT=$(cd "$1" && pwd -P)
mkdir -p "$2"
OUTPUT=$(cd "$2" && pwd -P)
MAVEN=$3
COMMIT=221d1de66c33292400c91cbd17e3121f72b04ea3
PATCH="$SANDBOX_ROOT/qa/math-optimization/target/swt-current-patch/swt-source-refinements.patch"
[[ "$OUTPUT/" != "$SWT_CHECKOUT/"* ]]
[[ ! -e "$OUTPUT/baseline" && ! -e "$OUTPUT/generated" ]]
[[ $(git -C "$SWT_CHECKOUT" rev-parse HEAD) == "$COMMIT" ]]
git -C "$SWT_CHECKOUT" diff --exit-code HEAD --
test -s "$PATCH"
git -C "$SWT_CHECKOUT" apply --check "$PATCH"
git -C "$SWT_CHECKOUT" rev-parse HEAD > "$OUTPUT/upstream-commit.txt"
git -C "$SANDBOX_ROOT" rev-parse HEAD > "$OUTPUT/sandbox-commit.txt"
java -version > "$OUTPUT/java-version.txt" 2>&1
sha256sum "$PATCH" > "$OUTPUT/patch.sha256"
cp "$PATCH" "$OUTPUT/applied.patch"
printf '{"renames":{},"allowedMissing":[],"allowedAdded":[]}\n' > "$OUTPUT/mapping.json"

run_upstream() {
  local phase=$1
  local destination="$OUTPUT/$phase"
  mkdir -p "$destination"
  local -a command=(
    dbus-run-session -- xvfb-run -a -s '-screen 0 1024x768x24 -noreset'
    "$MAVEN" --batch-mode --no-transfer-progress --no-snapshot-updates
    -Pbuild-individual-bundles -DforkCount=1 -DskipNativeTests=false
    -Dtycho.baseline.replace=none --fail-at-end clean verify
  )
  # Maven's no-snapshot-updates flag does not freeze the upstream P2 composite.
  # Use only the baseline's resolved artifacts for the generated build.
  if [[ "$phase" == generated ]]; then command+=(--offline); fi
  printf '%q ' "${command[@]}" > "$OUTPUT/$phase-command.txt"
  printf '\n' >> "$OUTPUT/$phase-command.txt"
  local status=0
  (cd "$SWT_CHECKOUT" && "${command[@]}") > "$OUTPUT/$phase-maven.log" 2>&1 || status=$?
  printf '%s\n' "$status" > "$OUTPUT/$phase-exit-code.txt"
  # Retain all upstream JUnit reports with their module paths, even after failure.
  while IFS= read -r -d '' report; do
    mkdir -p "$destination/$(dirname "$report")"
    cp "$SWT_CHECKOUT/$report" "$destination/$report"
  done < <(cd "$SWT_CHECKOUT" && rg --files --hidden --no-ignore -0 -g 'TEST-*.xml')
}

run_upstream baseline
git -C "$SWT_CHECKOUT" apply "$PATCH"
git -C "$SWT_CHECKOUT" diff --check
git -C "$SWT_CHECKOUT" diff --binary > "$OUTPUT/observed.patch"
run_upstream generated

comparison_status=0
(cd "$SANDBOX_ROOT" && "$MAVEN" --batch-mode --no-transfer-progress \
  -f qa/math-optimization/pom.xml \
  '-Dtest=JUnitXmlInventoryComparatorTest#configuredUpstreamEvidenceIsComparedByMaven,SwtUpstreamInventoryTest#configuredSwtModulesPreserveExecutedTests' \
  -Dsandbox.math.swt.upstream=true \
  "-Dsandbox.junit.inventory.baseline=$OUTPUT/baseline" \
  "-Dsandbox.junit.inventory.migrated=$OUTPUT/generated" \
  "-Dsandbox.junit.inventory.mapping=$OUTPUT/mapping.json" \
  "-Dsandbox.junit.inventory.output=$OUTPUT/test-inventory-comparison.json" \
  test) > "$OUTPUT/inventory-comparison.log" 2>&1 || comparison_status=$?
printf '%s\n' "$comparison_status" > "$OUTPUT/inventory-comparison-exit-code.txt"
[[ $(cat "$OUTPUT/baseline-exit-code.txt") == 0 ]]
[[ $(cat "$OUTPUT/generated-exit-code.txt") == 0 ]]
[[ "$comparison_status" == 0 ]]
