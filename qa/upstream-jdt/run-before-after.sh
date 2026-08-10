#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=pins.env
source "$SCRIPT_DIR/pins.env"

APPLICATION_ID="sandbox_cleanup_application.org.sandbox.jdt.core.JavaCleanup"
PROFILE="$SCRIPT_DIR/junit3-to-jupiter.properties"
OVERLAY="$SCRIPT_DIR/overlays/jdt-core-r4_40-jupiter.patch"
MAPPING="$SCRIPT_DIR/expected-test-mapping.json"
COMPARATOR="$SCRIPT_DIR/compare_test_inventory.py"

JDT_CORE=""
JDT_UI=""
JDT_CORE_BINARIES=""
OOMPH_WORKSPACE=""
SANDBOX_ECLIPSE=""
OUTPUT=""
MAVEN_BIN="${MAVEN_BIN:-mvn}"
KEEP_CHANGES=false

usage() {
  cat <<'EOF'
Usage: run-before-after.sh \
  --jdt-core <Oomph clone> \
  --workspace <closed Oomph workspace> \
  --sandbox-eclipse <Sandbox product launcher> \
  [--jdt-ui <Oomph clone>] \
  [--jdt-core-binaries <Oomph clone>] \
  [--output <evidence directory>] \
  [--maven <mvn executable>] \
  [--keep-changes]

The JDT repositories and workspace must have been provisioned by
sandbox_oomph/jdt-migration-qa.configuration.setup. The runner verifies every
pinned commit, requires a clean JDT Core checkout, runs the same Maven test
command before and after the Sandbox migration, compares the complete JUnit XML
inventory, and restores the checkout unless --keep-changes is supplied.
EOF
}

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

while (($#)); do
  case "$1" in
    --jdt-core) JDT_CORE=${2:?missing value}; shift 2 ;;
    --jdt-ui) JDT_UI=${2:?missing value}; shift 2 ;;
    --jdt-core-binaries) JDT_CORE_BINARIES=${2:?missing value}; shift 2 ;;
    --workspace) OOMPH_WORKSPACE=${2:?missing value}; shift 2 ;;
    --sandbox-eclipse) SANDBOX_ECLIPSE=${2:?missing value}; shift 2 ;;
    --output) OUTPUT=${2:?missing value}; shift 2 ;;
    --maven) MAVEN_BIN=${2:?missing value}; shift 2 ;;
    --keep-changes) KEEP_CHANGES=true; shift ;;
    --help|-h) usage; exit 0 ;;
    *) fail "Unknown argument: $1" ;;
  esac
done

[[ -n "$JDT_CORE" ]] || fail "--jdt-core is required"
[[ -n "$OOMPH_WORKSPACE" ]] || fail "--workspace is required"
[[ -n "$SANDBOX_ECLIPSE" ]] || fail "--sandbox-eclipse is required"
command -v git >/dev/null || fail "git is required"
command -v python3 >/dev/null || fail "python3 is required"
command -v "$MAVEN_BIN" >/dev/null || fail "Maven executable not found: $MAVEN_BIN"

canonical_dir() {
  (cd -- "$1" && pwd -P)
}

JDT_CORE=$(canonical_dir "$JDT_CORE")
OOMPH_WORKSPACE=$(canonical_dir "$OOMPH_WORKSPACE")
[[ -d "$OOMPH_WORKSPACE/.metadata" ]] || fail "Not an Eclipse workspace: $OOMPH_WORKSPACE"
[[ -x "$SANDBOX_ECLIPSE" ]] || fail "Sandbox Eclipse launcher is not executable: $SANDBOX_ECLIPSE"
SANDBOX_ECLIPSE="$(cd -- "$(dirname -- "$SANDBOX_ECLIPSE")" && pwd -P)/$(basename -- "$SANDBOX_ECLIPSE")"

if [[ -z "$OUTPUT" ]]; then
  OUTPUT="$SCRIPT_DIR/../../target/upstream-jdt-qa/$(date -u +%Y%m%dT%H%M%SZ)"
fi
mkdir -p "$OUTPUT" "$OUTPUT/logs" "$OUTPUT/baseline" "$OUTPUT/migrated" "$OUTPUT/tmp"
OUTPUT=$(canonical_dir "$OUTPUT")
printf 'INITIALIZING\n' > "$OUTPUT/run-state.txt"

normalize_remote() {
  local value=$1
  value=${value%.git}
  value=${value#git@github.com:}
  value=${value#ssh://git@github.com/}
  value=${value#https://github.com/}
  value=${value#http://github.com/}
  printf '%s\n' "$value"
}

verify_repository() {
  local label=$1 path=$2 expected_repository=$3 expected_ref=$4 expected_commit=$5
  [[ -d "$path/.git" || -f "$path/.git" ]] || fail "$label is not a Git worktree: $path"
  local actual_commit ref_commit remote
  actual_commit=$(git -C "$path" rev-parse 'HEAD^{commit}')
  ref_commit=$(git -C "$path" rev-parse "${expected_ref}^{commit}")
  remote=$(git -C "$path" remote get-url origin)
  [[ "$actual_commit" == "$expected_commit" ]] \
    || fail "$label HEAD is $actual_commit, expected $expected_commit"
  [[ "$ref_commit" == "$expected_commit" ]] \
    || fail "$label ref $expected_ref resolves to $ref_commit, expected $expected_commit"
  [[ "$(normalize_remote "$remote")" == "$(normalize_remote "$expected_repository")" ]] \
    || fail "$label origin is $remote, expected $expected_repository"
}

verify_repository "JDT Core" "$JDT_CORE" "$PIN_JDT_CORE_REPOSITORY" "$PIN_JDT_CORE_REF" "$PIN_JDT_CORE_COMMIT"
if [[ -n "$JDT_UI" ]]; then
  JDT_UI=$(canonical_dir "$JDT_UI")
  verify_repository "JDT UI" "$JDT_UI" "$PIN_JDT_UI_REPOSITORY" "$PIN_JDT_UI_REF" "$PIN_JDT_UI_COMMIT"
fi
if [[ -n "$JDT_CORE_BINARIES" ]]; then
  JDT_CORE_BINARIES=$(canonical_dir "$JDT_CORE_BINARIES")
  verify_repository "JDT Core binaries" "$JDT_CORE_BINARIES" \
    "$PIN_JDT_CORE_BINARIES_REPOSITORY" "$PIN_JDT_CORE_BINARIES_REF" "$PIN_JDT_CORE_BINARIES_COMMIT"
fi

[[ -f "$JDT_CORE/$PIN_PRIMARY_TEST_POM" ]] || fail "Missing pinned test POM"
[[ -d "$JDT_CORE/$PIN_PRIMARY_SOURCE" ]] || fail "Missing pinned source directory"
[[ -z "$(git -C "$JDT_CORE" status --porcelain=v1 --untracked-files=all)" ]] \
  || fail "JDT Core checkout must be clean before QA"

ORIGINAL_HEAD=$(git -C "$JDT_CORE" rev-parse HEAD)
RESTORE_REQUIRED=true
restore_checkout() {
  local status=$?
  if [[ "$RESTORE_REQUIRED" == true && "$KEEP_CHANGES" != true ]]; then
    git -C "$JDT_CORE" reset --hard "$ORIGINAL_HEAD" >/dev/null 2>&1 || true
    git -C "$JDT_CORE" clean -fd -- "$PIN_PRIMARY_PROJECT" >/dev/null 2>&1 || true
  fi
  if ((status != 0)); then
    printf 'FAIL\n' > "$OUTPUT/run-state.txt"
  fi
  exit "$status"
}
trap restore_checkout EXIT INT TERM

cp "$SCRIPT_DIR/pins.env" "$OUTPUT/pins.env"
cp "$PROFILE" "$OUTPUT/junit3-to-jupiter.properties"
cp "$OVERLAY" "$OUTPUT/jdt-core-r4_40-jupiter.patch"
cp "$MAPPING" "$OUTPUT/expected-test-mapping.json"

# The overlay is committed locally so the later Git diff contains only the
# migration. It is applied identically before both test executions.
git -C "$JDT_CORE" apply --check "$OVERLAY"
git -C "$JDT_CORE" apply "$OVERLAY"
git -C "$JDT_CORE" add org.eclipse.jdt.apt.tests/META-INF/MANIFEST.MF
git -C "$JDT_CORE" -c user.name='Sandbox QA' -c user.email='qa@example.invalid' \
  commit -m 'Apply the Sandbox JDT migration QA build overlay' >/dev/null
OVERLAY_COMMIT=$(git -C "$JDT_CORE" rev-parse HEAD)
printf 'OVERLAY_APPLIED\n' > "$OUTPUT/run-state.txt"

copy_reports() {
  local destination=$1
  mkdir -p "$destination"
  local count=0 file relative target
  while IFS= read -r -d '' file; do
    relative=${file#"$JDT_CORE"/}
    target="$destination/$relative"
    mkdir -p "$(dirname -- "$target")"
    cp "$file" "$target"
    count=$((count + 1))
  done < <(find "$JDT_CORE" -type f -path '*/target/surefire-reports/*.xml' -print0)
  ((count > 0)) || fail "No Surefire XML reports were produced"
}

run_tests() {
  local phase=$1 destination=$2
  local tmp="$OUTPUT/tmp/$phase"
  mkdir -p "$tmp"
  printf '%s\n' "${MAVEN_BIN} --batch-mode --no-transfer-progress -U -f ${PIN_PRIMARY_TEST_POM} clean verify" \
    > "$destination-command.txt"
  (
    cd "$JDT_CORE"
    "$MAVEN_BIN" --batch-mode --no-transfer-progress -U \
      -Djava.io.tmpdir="$tmp" \
      -f "$PIN_PRIMARY_TEST_POM" clean verify
  ) 2>&1 | tee "$OUTPUT/logs/$phase-maven.log"
  copy_reports "$destination"
}

run_cleanup() {
  local mode=$1 report=$2 patch=${3:-}
  local -a command=(
    "$SANDBOX_ECLIPSE"
    -nosplash
    -consoleLog
    -clean
    -refresh
    -data "$OOMPH_WORKSPACE"
    -application "$APPLICATION_ID"
    --mode "$mode"
    --scope both
    --config "$PROFILE"
    --report "$report"
  )
  if [[ -n "$patch" ]]; then
    command+=(--patch "$patch")
  fi
  command+=("$JDT_CORE/$PIN_PRIMARY_SOURCE")
  printf '%q ' "${command[@]}" > "$OUTPUT/logs/$mode-cleanup-command.txt"
  printf '\n' >> "$OUTPUT/logs/$mode-cleanup-command.txt"
  "${command[@]}" >"$OUTPUT/logs/$mode-cleanup.stdout.log" \
    2>"$OUTPUT/logs/$mode-cleanup.stderr.log"
}

printf 'BASELINE_TESTS\n' > "$OUTPUT/run-state.txt"
run_tests baseline "$OUTPUT/baseline"
[[ -z "$(git -C "$JDT_CORE" status --porcelain=v1 --untracked-files=all)" ]] \
  || fail "Baseline test modified tracked or untracked source files"

printf 'CLEANUP_CHECK\n' > "$OUTPUT/run-state.txt"
if run_cleanup check "$OUTPUT/cleanup-check-report.json" "$OUTPUT/cleanup-check.patch"; then
  CHECK_STATUS=0
else
  CHECK_STATUS=$?
fi
printf '%s\n' "$CHECK_STATUS" > "$OUTPUT/cleanup-check-exit-code.txt"
[[ "$CHECK_STATUS" -eq 2 ]] \
  || fail "Cleanup check must report required changes with exit code 2, got $CHECK_STATUS"
[[ -s "$OUTPUT/cleanup-check.patch" ]] || fail "Cleanup check produced no patch evidence"
[[ -z "$(git -C "$JDT_CORE" status --porcelain=v1 --untracked-files=all)" ]] \
  || fail "Cleanup check mode did not restore the checkout"

printf 'CLEANUP_APPLY\n' > "$OUTPUT/run-state.txt"
if run_cleanup apply "$OUTPUT/cleanup-apply-report.json"; then
  APPLY_STATUS=0
else
  APPLY_STATUS=$?
fi
printf '%s\n' "$APPLY_STATUS" > "$OUTPUT/cleanup-apply-exit-code.txt"
[[ "$APPLY_STATUS" -eq 0 ]] || fail "Cleanup apply failed with exit code $APPLY_STATUS"

git -C "$JDT_CORE" diff --check "$OVERLAY_COMMIT" --
git -C "$JDT_CORE" diff --binary "$OVERLAY_COMMIT" -- > "$OUTPUT/migration.patch"
git -C "$JDT_CORE" diff --name-only "$OVERLAY_COMMIT" -- > "$OUTPUT/changed-files.txt"
JAVA_CHANGE_COUNT=$(grep -cE '\.java$' "$OUTPUT/changed-files.txt" || true)
((JAVA_CHANGE_COUNT > 0)) || fail "Cleanup apply changed no Java source files"

printf 'MIGRATED_TESTS\n' > "$OUTPUT/run-state.txt"
run_tests migrated "$OUTPUT/migrated"

printf 'COMPARING_TEST_INVENTORY\n' > "$OUTPUT/run-state.txt"
python3 "$COMPARATOR" \
  --baseline "$OUTPUT/baseline" \
  --migrated "$OUTPUT/migrated" \
  --mapping "$MAPPING" \
  --output "$OUTPUT/test-inventory-comparison.json" \
  > "$OUTPUT/logs/test-inventory-comparison.log"

export OUTPUT ORIGINAL_HEAD OVERLAY_COMMIT JAVA_CHANGE_COUNT JDT_CORE JDT_UI JDT_CORE_BINARIES OOMPH_WORKSPACE
export PIN_ECLIPSE_RELEASE PIN_ECLIPSE_PLATFORM_VERSION PIN_JDT_CORE_REF PIN_JDT_CORE_COMMIT
export PIN_JDT_UI_REF PIN_JDT_UI_COMMIT PIN_JDT_CORE_BINARIES_REF PIN_JDT_CORE_BINARIES_COMMIT
python3 - <<'PY'
import hashlib
import json
import os
from pathlib import Path

out = Path(os.environ["OUTPUT"])

def digest(name: str) -> str:
    return hashlib.sha256((out / name).read_bytes()).hexdigest()

provenance = {
    "result": "PASS",
    "eclipseRelease": os.environ["PIN_ECLIPSE_RELEASE"],
    "eclipsePlatformVersion": os.environ["PIN_ECLIPSE_PLATFORM_VERSION"],
    "jdtCore": {
        "path": os.environ["JDT_CORE"],
        "ref": os.environ["PIN_JDT_CORE_REF"],
        "commit": os.environ["PIN_JDT_CORE_COMMIT"],
        "originalHead": os.environ["ORIGINAL_HEAD"],
        "overlayCommit": os.environ["OVERLAY_COMMIT"],
    },
    "jdtUi": {
        "path": os.environ.get("JDT_UI", ""),
        "ref": os.environ["PIN_JDT_UI_REF"],
        "commit": os.environ["PIN_JDT_UI_COMMIT"],
    },
    "jdtCoreBinaries": {
        "path": os.environ.get("JDT_CORE_BINARIES", ""),
        "ref": os.environ["PIN_JDT_CORE_BINARIES_REF"],
        "commit": os.environ["PIN_JDT_CORE_BINARIES_COMMIT"],
    },
    "oomphWorkspace": os.environ["OOMPH_WORKSPACE"],
    "javaFilesChanged": int(os.environ["JAVA_CHANGE_COUNT"]),
    "artifacts": {
        "cleanupProfileSha256": digest("junit3-to-jupiter.properties"),
        "buildOverlaySha256": digest("jdt-core-r4_40-jupiter.patch"),
        "migrationPatchSha256": digest("migration.patch"),
        "testInventorySha256": digest("test-inventory-comparison.json"),
    },
}
(out / "provenance.json").write_text(json.dumps(provenance, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY

printf 'PASS\n' > "$OUTPUT/run-state.txt"
printf 'Upstream JDT migration QA passed. Evidence: %s\n' "$OUTPUT"
if [[ "$KEEP_CHANGES" == true ]]; then
  RESTORE_REQUIRED=false
  printf 'The overlay commit and migrated source tree were retained for documentation capture.\n'
fi
