#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
SANDBOX_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd -P)"
# shellcheck source=pins.env
source "$SCRIPT_DIR/pins.env"

APPLICATION_ID="org.sandbox.jdt.core.ProjectWideJavaCleanup"
STRICT_PROFILE="$SCRIPT_DIR/junit4-to-jupiter.properties"
BEST_EFFORT_PROFILE="$SCRIPT_DIR/junit4-to-jupiter-best-effort.properties"
CORPUS_CONTRACT="$SCRIPT_DIR/jdt-ui-junit4-corpus.json"
CORPUS_VERIFIER="$SCRIPT_DIR/verify_jdt_ui_corpus.py"
MAPPING="$SCRIPT_DIR/expected-test-mapping.json"
PROJECT="org.eclipse.jdt.ui.tests"
BCOVIEW_PROJECT="org.eclipse.jdt.bcoview"
REACTOR_PROJECTS="$PROJECT,$BCOVIEW_PROJECT"
ROOT_POM="pom.xml"

JDT_UI=""
OOMPH_WORKSPACE=""
SANDBOX_ECLIPSE=""
OUTPUT=""
MODE="strict"
MAVEN_BIN="${MAVEN_BIN:-mvn}"
KEEP_CHANGES=false
ALLOW_CLEAN_WORKSPACE=false

usage() {
  cat <<'EOF'
Usage: run-jdt-ui-before-after.sh \
  --jdt-ui <pinned eclipse.jdt.ui checkout> \
  --workspace <closed Oomph or clean mirror workspace> \
  --sandbox-eclipse <Sandbox product launcher> \
  [--mode strict|best-effort] \
  [--output <evidence directory>] \
  [--maven <mvn executable>] \
  [--keep-changes] \
  [--allow-clean-workspace]

This runner validates JUnit 4 to Jupiter migration against real pinned JDT UI
source. Strict mode requires a passing migrated build and an identical JUnit XML
inventory while quarantining unsupported compilation units. Best-effort mode
keeps independently safe transformations, requires explicit @todo scaffolds and
structured remediation evidence, and records rather than hides any incomplete
build or test inventory.
EOF
}

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

while (($#)); do
  case "$1" in
    --jdt-ui) JDT_UI=${2:?missing value}; shift 2 ;;
    --workspace) OOMPH_WORKSPACE=${2:?missing value}; shift 2 ;;
    --sandbox-eclipse) SANDBOX_ECLIPSE=${2:?missing value}; shift 2 ;;
    --mode) MODE=${2:?missing value}; shift 2 ;;
    --output) OUTPUT=${2:?missing value}; shift 2 ;;
    --maven) MAVEN_BIN=${2:?missing value}; shift 2 ;;
    --keep-changes) KEEP_CHANGES=true; shift ;;
    --allow-clean-workspace) ALLOW_CLEAN_WORKSPACE=true; shift ;;
    --help|-h) usage; exit 0 ;;
    *) fail "Unknown argument: $1" ;;
  esac
done

[[ "$MODE" == strict || "$MODE" == best-effort ]] \
  || fail "--mode must be strict or best-effort"
[[ -n "$JDT_UI" ]] || fail "--jdt-ui is required"
[[ -n "$OOMPH_WORKSPACE" ]] || fail "--workspace is required"
[[ -n "$SANDBOX_ECLIPSE" ]] || fail "--sandbox-eclipse is required"
command -v git >/dev/null || fail "git is required"
command -v python3 >/dev/null || fail "python3 is required"
command -v "$MAVEN_BIN" >/dev/null || fail "Maven executable not found: $MAVEN_BIN"

canonical_dir() {
  (cd -- "$1" && pwd -P)
}

JDT_UI=$(canonical_dir "$JDT_UI")
if [[ "$ALLOW_CLEAN_WORKSPACE" == true ]]; then
  mkdir -p "$OOMPH_WORKSPACE"
fi
OOMPH_WORKSPACE=$(canonical_dir "$OOMPH_WORKSPACE")
if [[ ! -d "$OOMPH_WORKSPACE/.metadata" && "$ALLOW_CLEAN_WORKSPACE" != true ]]; then
  fail "Not an Oomph-provisioned Eclipse workspace: $OOMPH_WORKSPACE"
fi
[[ -x "$SANDBOX_ECLIPSE" ]] || fail "Sandbox Eclipse launcher is not executable: $SANDBOX_ECLIPSE"
SANDBOX_ECLIPSE="$(cd -- "$(dirname -- "$SANDBOX_ECLIPSE")" && pwd -P)/$(basename -- "$SANDBOX_ECLIPSE")"

PROFILE="$STRICT_PROFILE"
if [[ "$MODE" == best-effort ]]; then
  PROFILE="$BEST_EFFORT_PROFILE"
fi
[[ -f "$PROFILE" ]] || fail "Missing cleanup profile: $PROFILE"
[[ -f "$CORPUS_CONTRACT" ]] || fail "Missing JDT UI corpus contract"
[[ -f "$JDT_UI/$PROJECT/.project" ]] || fail "Missing pinned Eclipse project description"
[[ -f "$JDT_UI/$ROOT_POM" ]] || fail "Missing pinned JDT UI reactor POM"
[[ -f "$JDT_UI/$PROJECT/pom.xml" ]] || fail "Missing pinned JDT UI test POM"
[[ -f "$JDT_UI/$BCOVIEW_PROJECT/pom.xml" ]] || fail "Missing pinned bytecode-view POM"

if [[ -z "$OUTPUT" ]]; then
  OUTPUT="$SCRIPT_DIR/../../target/upstream-jdt-ui-qa/$MODE-$(date -u +%Y%m%dT%H%M%SZ)"
fi
mkdir -p "$OUTPUT" "$OUTPUT/logs" "$OUTPUT/baseline" "$OUTPUT/migrated" \
  "$OUTPUT/tmp" "$OUTPUT/corpus/baseline" "$OUTPUT/corpus/migrated"
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

actual_commit=$(git -C "$JDT_UI" rev-parse 'HEAD^{commit}')
ref_commit=$(git -C "$JDT_UI" rev-parse "${PIN_JDT_UI_REF}^{commit}")
remote=$(git -C "$JDT_UI" remote get-url origin)
[[ "$actual_commit" == "$PIN_JDT_UI_COMMIT" ]] \
  || fail "JDT UI HEAD is $actual_commit, expected $PIN_JDT_UI_COMMIT"
[[ "$ref_commit" == "$PIN_JDT_UI_COMMIT" ]] \
  || fail "JDT UI ref $PIN_JDT_UI_REF resolves to $ref_commit, expected $PIN_JDT_UI_COMMIT"
[[ "$(normalize_remote "$remote")" == "$(normalize_remote "$PIN_JDT_UI_REPOSITORY")" ]] \
  || fail "JDT UI origin is $remote, expected $PIN_JDT_UI_REPOSITORY"
[[ -z "$(git -C "$JDT_UI" status --porcelain=v1 --untracked-files=all)" ]] \
  || fail "JDT UI checkout must be clean before QA"

verify_workspace_pins() {
  local generated="$OOMPH_WORKSPACE/.sandbox-jdt-migration-qa-pins.env"
  if [[ ! -f "$generated" ]]; then
    [[ "$ALLOW_CLEAN_WORKSPACE" == true ]] \
      || fail "Oomph workspace pin evidence is missing: $generated"
    return
  fi
  python3 - "$SCRIPT_DIR/pins.env" "$generated" <<'PY'
from pathlib import Path
import sys


def parse(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        if key.startswith("PIN_"):
            result[key] = value
    return result

expected = parse(Path(sys.argv[1]))
actual = parse(Path(sys.argv[2]))
for key in (
    "PIN_ECLIPSE_RELEASE",
    "PIN_ECLIPSE_PLATFORM_VERSION",
    "PIN_JDT_UI_REPOSITORY",
    "PIN_JDT_UI_REF",
    "PIN_JDT_UI_COMMIT",
):
    if actual.get(key) != expected.get(key):
        raise SystemExit(
            f"Oomph pin evidence differs for {key}: {actual.get(key)!r} != {expected.get(key)!r}"
        )
PY
}
verify_workspace_pins

profile_value() {
  local key=$1
  awk -F= -v key="$key" '$1 == key { print $2; found=1 } END { if (!found) exit 1 }' "$PROFILE"
}
[[ "$(profile_value cleanup.junitcleanup)" == true ]] || fail "JUnit 4 cleanup is not enabled"
[[ "$(profile_value cleanup.junit3cleanup)" == false ]] || fail "JUnit 3 cleanup must be disabled"
if [[ "$MODE" == strict ]]; then
  [[ "$(profile_value cleanup.junitcleanup_best_effort)" == false ]] \
    || fail "Strict profile accidentally enables best-effort migration"
else
  [[ "$(profile_value cleanup.junitcleanup_best_effort)" == true ]] \
    || fail "Best-effort profile does not enable best-effort migration"
fi

ORIGINAL_HEAD=$(git -C "$JDT_UI" rev-parse HEAD)
RESTORE_REQUIRED=true
restore_checkout() {
  local status=$?
  if [[ "$RESTORE_REQUIRED" == true && "$KEEP_CHANGES" != true ]]; then
    git -C "$JDT_UI" reset --hard "$ORIGINAL_HEAD" >/dev/null 2>&1 || true
    git -C "$JDT_UI" clean -fd -- "$PROJECT" >/dev/null 2>&1 || true
  fi
  if ((status != 0)); then
    printf 'FAIL\n' > "$OUTPUT/run-state.txt"
  fi
  exit "$status"
}
trap restore_checkout EXIT INT TERM

cp "$SCRIPT_DIR/pins.env" "$OUTPUT/pins.env"
cp "$PROFILE" "$OUTPUT/$(basename -- "$PROFILE")"
cp "$CORPUS_CONTRACT" "$OUTPUT/jdt-ui-junit4-corpus.json"
cp "$MAPPING" "$OUTPUT/expected-test-mapping.json"
if [[ -f "$OOMPH_WORKSPACE/.sandbox-jdt-migration-qa-pins.env" ]]; then
  cp "$OOMPH_WORKSPACE/.sandbox-jdt-migration-qa-pins.env" "$OUTPUT/oomph-workspace-pins.env"
fi

copy_corpus_sources() {
  local destination=$1
  python3 - "$CORPUS_CONTRACT" "$JDT_UI" "$destination" <<'PY'
import json
import shutil
import sys
from pathlib import Path

contract = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
repository = Path(sys.argv[2])
destination = Path(sys.argv[3])
for relative in sorted(contract["requiredFiles"]):
    source = repository / relative
    if not source.is_file():
        raise SystemExit(f"Required JDT UI corpus source is missing: {relative}")
    target = destination / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)
PY
}

copy_reports() {
  local destination=$1 require_reports=$2
  mkdir -p "$destination"
  local count=0 file relative target
  while IFS= read -r -d '' file; do
    relative=${file#"$JDT_UI"/}
    target="$destination/$relative"
    mkdir -p "$(dirname -- "$target")"
    cp "$file" "$target"
    count=$((count + 1))
  done < <(find "$JDT_UI/$PROJECT" -type f -path '*/target/surefire-reports/*.xml' -print0)
  printf '%s\n' "$count" > "$destination-report-count.txt"
  if [[ "$require_reports" == true && "$count" -eq 0 ]]; then
    fail "No JDT UI Surefire XML reports were produced"
  fi
}

verify_reactor_bcoview_runtime() {
  local destination=$1
  local report_root="$JDT_UI/$PROJECT/target/surefire-reports"
  local evidence="$destination-bcoview-runtime.txt"
  python3 - "$report_root" "$JDT_UI/$BCOVIEW_PROJECT" "$evidence" <<'PYRUNTIME'
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

report_root = Path(sys.argv[1])
expected = f"reference:file:{Path(sys.argv[2]).resolve()}"
evidence = Path(sys.argv[3])

entries: set[str] = set()
for report in sorted(report_root.glob("*.xml")):
    try:
        root = ET.parse(report).getroot()
    except ET.ParseError:
        continue
    for prop in root.findall("./properties/property"):
        if prop.get("name") != "osgi.bundles":
            continue
        for entry in (prop.get("value") or "").split(","):
            if "org.eclipse.jdt.bcoview" in entry:
                entries.add(entry.strip())

lines = sorted(entries)
evidence.write_text("\n".join(lines) + ("\n" if lines else ""), encoding="utf-8")
if not any(line.startswith(expected) for line in lines):
    actual = "\n  ".join(lines) if lines else "<no org.eclipse.jdt.bcoview runtime entry>"
    raise SystemExit(
        "JDT UI tests did not use the pinned reactor bytecode-view bundle. "
        f"Expected an entry starting with {expected!r}; found:\n  {actual}"
    )
PYRUNTIME
}

run_tests() {
  local phase=$1 destination=$2 require_reports=$3
  local tmp="$OUTPUT/tmp/$phase"
  mkdir -p "$tmp"
  local -a command=(
    "$MAVEN_BIN"
    --batch-mode
    --no-transfer-progress
    -U
    -DskipTests=false
    -Djava.io.tmpdir="$tmp"
    -Pbuild-individual-bundles
    -f "$ROOT_POM"
    -pl "$REACTOR_PROJECTS"
    -am
    clean verify
  )
  printf '%q ' "${command[@]}" > "$destination-command.txt"
  printf '\n' >> "$destination-command.txt"
  local -a display_prefix=()
  if [[ -z "${DISPLAY:-}" ]] && command -v xvfb-run >/dev/null; then
    display_prefix=(xvfb-run --auto-servernum --server-args="-screen 0 1600x1200x24")
  fi
  set +e
  (
    cd "$JDT_UI"
    "${display_prefix[@]}" "${command[@]}"
  ) 2>&1 | tee "$OUTPUT/logs/$phase-maven.log"
  local status=${PIPESTATUS[0]}
  set -e
  printf '%s\n' "$status" > "$destination-maven-exit-code.txt"
  copy_reports "$destination" "$require_reports"
  if [[ "$(cat "$destination-report-count.txt")" -gt 0 ]]; then
    verify_reactor_bcoview_runtime "$destination"
  fi
  return "$status"
}

compare_test_inventory() {
  local output=$1
  local command_log="$OUTPUT/test-inventory-comparison-command.txt"
  local result_log="$OUTPUT/logs/test-inventory-comparison.log"
  local -a command=(
    "$MAVEN_BIN"
    --batch-mode
    --no-transfer-progress
    "-Dtest=org.sandbox.jdt.triggerpattern.test.policy.JUnitXmlInventoryComparatorTest#configuredUpstreamEvidenceIsComparedByMaven"
    -Dsurefire.failIfNoSpecifiedTests=false
    -DfailIfNoTests=false
    "-Dsandbox.junit.inventory.baseline=$OUTPUT/baseline"
    "-Dsandbox.junit.inventory.migrated=$OUTPUT/migrated"
    "-Dsandbox.junit.inventory.mapping=$MAPPING"
    "-Dsandbox.junit.inventory.output=$output"
    -pl sandbox_target,sandbox_common_test
    -am
    package
  )
  printf '%q ' "${command[@]}" > "$command_log"
  printf '\n' >> "$command_log"
  (
    cd "$SANDBOX_ROOT"
    "${command[@]}"
  ) > "$result_log" 2>&1
}

run_cleanup() {
  local cleanup_mode=$1 report=$2 patch=${3:-}
  local -a command=(
    "$SANDBOX_ECLIPSE"
    -nosplash
    -consoleLog
    -clean
    -data "$OOMPH_WORKSPACE"
    -application "$APPLICATION_ID"
    --mode "$cleanup_mode"
    --project "$PROJECT"
    --project-location "$JDT_UI/$PROJECT"
    --config "$PROFILE"
    --report "$report"
  )
  if [[ -n "$patch" ]]; then
    command+=(--patch "$patch")
  fi
  printf '%q ' "${command[@]}" > "$OUTPUT/logs/$cleanup_mode-cleanup-command.txt"
  printf '\n' >> "$OUTPUT/logs/$cleanup_mode-cleanup-command.txt"
  local -a display_prefix=()
  if [[ -z "${DISPLAY:-}" ]] && command -v xvfb-run >/dev/null; then
    display_prefix=(xvfb-run --auto-servernum --server-args="-screen 0 1600x1200x24")
  fi
  timeout --signal=TERM --kill-after=1m 30m "${display_prefix[@]}" "${command[@]}" >"$OUTPUT/logs/$cleanup_mode-cleanup.stdout.log" 2>"$OUTPUT/logs/$cleanup_mode-cleanup.stderr.log"
}

verify_cleanup_application() {
  local stdout="$OUTPUT/logs/cleanup-application-preflight.stdout.log"
  local stderr="$OUTPUT/logs/cleanup-application-preflight.stderr.log"
  local -a display_prefix=()
  if [[ -z "${DISPLAY:-}" ]] && command -v xvfb-run >/dev/null; then
    display_prefix=(xvfb-run --auto-servernum --server-args="-screen 0 1600x1200x24")
  fi
  if ! timeout --signal=TERM --kill-after=30s 3m "${display_prefix[@]}" "$SANDBOX_ECLIPSE" -nosplash -consoleLog -clean -data "$OOMPH_WORKSPACE" -application "$APPLICATION_ID" --help >"$stdout" 2>"$stderr"; then
    cat "$stdout" >&2 || true
    cat "$stderr" >&2 || true
    fail "The registered project-wide Cleanup application could not be started: $APPLICATION_ID"
  fi
  grep -F -- "-application $APPLICATION_ID" "$stdout" >/dev/null || fail "Cleanup application preflight returned an unexpected usage contract"
}

printf 'CLEANUP_APPLICATION_PREFLIGHT\n' > "$OUTPUT/run-state.txt"
verify_cleanup_application

printf 'BASELINE_TESTS\n' > "$OUTPUT/run-state.txt"
copy_corpus_sources "$OUTPUT/corpus/baseline"
run_tests baseline "$OUTPUT/baseline" true \
  || fail "Pinned JDT UI baseline build or tests failed"
[[ -z "$(git -C "$JDT_UI" status --porcelain=v1 --untracked-files=all)" ]] \
  || fail "Baseline test modified tracked or untracked source files"

printf 'CLEANUP_CHECK\n' > "$OUTPUT/run-state.txt"
if run_cleanup check "$OUTPUT/cleanup-check-report.json" "$OUTPUT/cleanup-check.patch"; then
  CHECK_STATUS=0
else
  CHECK_STATUS=$?
fi
printf '%s\n' "$CHECK_STATUS" > "$OUTPUT/cleanup-check-exit-code.txt"
[[ "$CHECK_STATUS" -eq 2 ]] \
  || fail "Project-wide JDT UI cleanup check must report required changes with exit code 2, got $CHECK_STATUS"
[[ -s "$OUTPUT/cleanup-check-report.json" ]] || fail "Cleanup check produced no JSON report"
[[ -s "$OUTPUT/cleanup-check.patch" ]] || fail "Cleanup check produced no patch evidence"
[[ -z "$(git -C "$JDT_UI" status --porcelain=v1 --untracked-files=all)" ]] \
  || fail "Cleanup check mode did not restore the JDT UI checkout"

printf 'CLEANUP_APPLY\n' > "$OUTPUT/run-state.txt"
if run_cleanup apply "$OUTPUT/cleanup-apply-report.json"; then
  APPLY_STATUS=0
else
  APPLY_STATUS=$?
fi
printf '%s\n' "$APPLY_STATUS" > "$OUTPUT/cleanup-apply-exit-code.txt"
[[ "$APPLY_STATUS" -eq 0 ]] || fail "Project-wide JDT UI cleanup apply failed with exit code $APPLY_STATUS"
[[ -s "$OUTPUT/cleanup-apply-report.json" ]] || fail "Cleanup apply produced no JSON report"

git -C "$JDT_UI" diff --check "$ORIGINAL_HEAD" --
git -C "$JDT_UI" diff --binary "$ORIGINAL_HEAD" -- > "$OUTPUT/migration.patch"
git -C "$JDT_UI" diff --name-only "$ORIGINAL_HEAD" -- > "$OUTPUT/changed-files.txt"
JAVA_CHANGE_COUNT=$(grep -cE '\.java$' "$OUTPUT/changed-files.txt" || true)
((JAVA_CHANGE_COUNT > 0)) || fail "Cleanup apply changed no JDT UI Java source files"

printf 'VERIFYING_REAL_JDT_UI_CORPUS\n' > "$OUTPUT/run-state.txt"
python3 "$CORPUS_VERIFIER" \
  --repository "$JDT_UI" \
  --baseline-sources "$OUTPUT/corpus/baseline" \
  --contract "$CORPUS_CONTRACT" \
  --mode "$MODE" \
  --changed-files "$OUTPUT/changed-files.txt" \
  --check-report "$OUTPUT/cleanup-check-report.json" \
  --apply-report "$OUTPUT/cleanup-apply-report.json" \
  --output "$OUTPUT/corpus-result.json" \
  > "$OUTPUT/logs/corpus-result.log"
copy_corpus_sources "$OUTPUT/corpus/migrated"

printf 'MIGRATED_TESTS\n' > "$OUTPUT/run-state.txt"
if [[ "$MODE" == strict ]]; then
  run_tests migrated "$OUTPUT/migrated" true \
    || fail "Strict JDT UI migration did not compile or pass its tests"
  printf 'COMPARING_TEST_INVENTORY\n' > "$OUTPUT/run-state.txt"
  if compare_test_inventory "$OUTPUT/test-inventory-comparison.json"; then
    INVENTORY_STATUS=0
  else
    INVENTORY_STATUS=$?
  fi
  printf '%s\n' "$INVENTORY_STATUS" > "$OUTPUT/test-inventory-comparison-exit-code.txt"
  if ((INVENTORY_STATUS != 0)); then
    cat "$OUTPUT/logs/test-inventory-comparison.log" >&2 || true
    fail "Strict JDT UI migration changed the JUnit XML inventory"
  fi
else
  if run_tests migrated "$OUTPUT/migrated" false; then
    MIGRATED_STATUS=0
  else
    MIGRATED_STATUS=$?
  fi
  printf '%s\n' "$MIGRATED_STATUS" > "$OUTPUT/migrated-maven-exit-code.txt"
  REPORT_COUNT=$(cat "$OUTPUT/migrated-report-count.txt")
  if [[ "$REPORT_COUNT" -gt 0 ]]; then
    if compare_test_inventory "$OUTPUT/test-inventory-comparison.json"; then
      INVENTORY_STATUS=0
    else
      INVENTORY_STATUS=$?
    fi
  else
    INVENTORY_STATUS=3
    cat > "$OUTPUT/test-inventory-comparison.json" <<EOF
{
  "result": "NOT_AVAILABLE",
  "reason": "The explicit best-effort intermediate tree produced no JUnit XML inventory",
  "mavenExitCode": $MIGRATED_STATUS
}
EOF
  fi
  printf '%s\n' "$INVENTORY_STATUS" > "$OUTPUT/test-inventory-comparison-exit-code.txt"
fi

export OUTPUT MODE ORIGINAL_HEAD JAVA_CHANGE_COUNT JDT_UI OOMPH_WORKSPACE PROFILE
export PIN_ECLIPSE_RELEASE PIN_ECLIPSE_PLATFORM_VERSION PIN_JDT_UI_REF PIN_JDT_UI_COMMIT
python3 - <<'PY'
import hashlib
import json
import os
from pathlib import Path

out = Path(os.environ["OUTPUT"])


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def integer(name: str, default: int | None = None) -> int | None:
    path = out / name
    if not path.is_file():
        return default
    return int(path.read_text(encoding="utf-8").strip())

corpus = json.loads((out / "corpus-result.json").read_text(encoding="utf-8"))
inventory_path = out / "test-inventory-comparison.json"
inventory = json.loads(inventory_path.read_text(encoding="utf-8")) if inventory_path.is_file() else None
profile = Path(os.environ["PROFILE"])
provenance = {
    "result": "PASS",
    "evidenceType": "pinned-real-jdt-ui-junit4-before-after",
    "mode": os.environ["MODE"],
    "intermediateTreeMayRequireManualCompletion": os.environ["MODE"] == "best-effort",
    "eclipseRelease": os.environ["PIN_ECLIPSE_RELEASE"],
    "eclipsePlatformVersion": os.environ["PIN_ECLIPSE_PLATFORM_VERSION"],
    "jdtUi": {
        "path": os.environ["JDT_UI"],
        "ref": os.environ["PIN_JDT_UI_REF"],
        "commit": os.environ["PIN_JDT_UI_COMMIT"],
        "originalHead": os.environ["ORIGINAL_HEAD"],
    },
    "eclipseWorkspace": os.environ["OOMPH_WORKSPACE"],
    "javaFilesChanged": int(os.environ["JAVA_CHANGE_COUNT"]),
    "baselineMavenExitCode": integer("baseline-maven-exit-code.txt"),
    "migratedMavenExitCode": integer("migrated-maven-exit-code.txt", 0),
    "testInventoryComparisonExitCode": integer("test-inventory-comparison-exit-code.txt"),
    "corpus": corpus,
    "testInventory": inventory,
    "artifacts": {
        "cleanupProfile": profile.name,
        "cleanupProfileSha256": digest(profile),
        "checkReportSha256": digest(out / "cleanup-check-report.json"),
        "applyReportSha256": digest(out / "cleanup-apply-report.json"),
        "migrationPatchSha256": digest(out / "migration.patch"),
        "corpusResultSha256": digest(out / "corpus-result.json"),
    },
}
if inventory_path.is_file():
    provenance["artifacts"]["testInventorySha256"] = digest(inventory_path)
(out / "provenance.json").write_text(
    json.dumps(provenance, indent=2, sort_keys=True) + "\n",
    encoding="utf-8",
)
PY

printf 'PASS\n' > "$OUTPUT/run-state.txt"
printf 'Pinned JDT UI JUnit 4 %s migration QA passed. Evidence: %s\n' "$MODE" "$OUTPUT"
if [[ "$KEEP_CHANGES" == true ]]; then
  RESTORE_REQUIRED=false
  printf 'The migrated JDT UI source tree was retained for documentation capture.\n'
fi
