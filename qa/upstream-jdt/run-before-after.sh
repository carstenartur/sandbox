#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=pins.env
source "$SCRIPT_DIR/pins.env"

APPLICATION_ID="sandbox_cleanup_application.org.sandbox.jdt.core.ProjectWideJavaCleanup"
PROFILE="$SCRIPT_DIR/junit3-to-jupiter.properties"
OVERLAY="$SCRIPT_DIR/overlays/jdt-core-r4_40-jupiter.patch"
MAPPING="$SCRIPT_DIR/expected-test-mapping.json"
COMPARATOR="$SCRIPT_DIR/compare_test_inventory.py"
CORPUS_CONTRACT="$SCRIPT_DIR/expected-corpus.json"
CORPUS_VERIFIER="$SCRIPT_DIR/verify_corpus_result.py"

JDT_CORE=""
JDT_UI=""
JDT_CORE_BINARIES=""
OOMPH_WORKSPACE=""
SANDBOX_ECLIPSE=""
OUTPUT=""
MAVEN_BIN="${MAVEN_BIN:-mvn}"
KEEP_CHANGES=false
ALLOW_CLEAN_WORKSPACE=false

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
  [--keep-changes] \
  [--allow-clean-workspace]

The normal path requires the repositories and workspace provisioned by
sandbox_oomph/jdt-migration-qa.configuration.setup. One project-wide cleanup
refactoring then sees every source compilation unit of the pinned project.
Check and apply evidence, named real-corpus expectations, Maven tests and the
complete JUnit XML inventory must all agree.

--allow-clean-workspace is reserved for the explicitly labelled manual CI
mirror. It imports the same pinned project into an empty workspace but is not a
replacement for the Advanced-Mode/Oomph release evidence.
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
    --allow-clean-workspace) ALLOW_CLEAN_WORKSPACE=true; shift ;;
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
if [[ "$ALLOW_CLEAN_WORKSPACE" == true ]]; then
  mkdir -p "$OOMPH_WORKSPACE"
fi
OOMPH_WORKSPACE=$(canonical_dir "$OOMPH_WORKSPACE")
if [[ ! -d "$OOMPH_WORKSPACE/.metadata" && "$ALLOW_CLEAN_WORKSPACE" != true ]]; then
  fail "Not an Oomph-provisioned Eclipse workspace: $OOMPH_WORKSPACE"
fi
[[ -x "$SANDBOX_ECLIPSE" ]] || fail "Sandbox Eclipse launcher is not executable: $SANDBOX_ECLIPSE"
SANDBOX_ECLIPSE="$(cd -- "$(dirname -- "$SANDBOX_ECLIPSE")" && pwd -P)/$(basename -- "$SANDBOX_ECLIPSE")"

if [[ -z "$OUTPUT" ]]; then
  OUTPUT="$SCRIPT_DIR/../../target/upstream-jdt-qa/$(date -u +%Y%m%dT%H%M%SZ)"
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
[[ -f "$JDT_CORE/$PIN_PRIMARY_PROJECT/.project" ]] || fail "Missing pinned Eclipse project description"
[[ -z "$(git -C "$JDT_CORE" status --porcelain=v1 --untracked-files=all)" ]] \
  || fail "JDT Core checkout must be clean before QA"

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
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise SystemExit(f"{path}:{number}: expected NAME=value")
        key, value = line.split("=", 1)
        if key.startswith("PIN_"):
            result[key] = value
    return result


expected = parse(Path(sys.argv[1]))
actual = parse(Path(sys.argv[2]))
if actual != expected:
    missing = sorted(expected.keys() - actual.keys())
    extra = sorted(actual.keys() - expected.keys())
    changed = sorted(key for key in expected.keys() & actual.keys() if expected[key] != actual[key])
    raise SystemExit(
        f"Oomph pin evidence differs from pins.env: missing={missing}, extra={extra}, changed={changed}"
    )
PY
}
verify_workspace_pins

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
cp "$CORPUS_CONTRACT" "$OUTPUT/expected-corpus.json"
if [[ -f "$OOMPH_WORKSPACE/.sandbox-jdt-migration-qa-pins.env" ]]; then
  cp "$OOMPH_WORKSPACE/.sandbox-jdt-migration-qa-pins.env" "$OUTPUT/oomph-workspace-pins.env"
fi

# The identical build overlay is committed before both test runs. Applying with
# --index stages every touched file, so the later diff is migration-only even if
# the overlay grows beyond its current manifest change.
git -C "$JDT_CORE" apply --check "$OVERLAY"
git -C "$JDT_CORE" apply --index "$OVERLAY"
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

copy_corpus_sources() {
  local destination=$1
  python3 - "$CORPUS_CONTRACT" "$JDT_CORE" "$destination" <<'PY'
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
        raise SystemExit(f"Required corpus source is missing: {relative}")
    target = destination / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)
PY
}

run_tests() {
  local phase=$1 destination=$2
  local tmp="$OUTPUT/tmp/$phase"
  mkdir -p "$tmp"
  printf '%s\n' "${MAVEN_BIN} --batch-mode --no-transfer-progress -U -f ${PIN_PRIMARY_TEST_POM} clean verify" \
    > "$destination-command.txt"
  local -a display_prefix=()
  if [[ -z "${DISPLAY:-}" ]] && command -v xvfb-run >/dev/null; then
    display_prefix=(xvfb-run --auto-servernum)
  fi
  (
    cd "$JDT_CORE"
    "${display_prefix[@]}" "$MAVEN_BIN" --batch-mode --no-transfer-progress -U \
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
    --project "$PIN_PRIMARY_PROJECT"
    --project-location "$JDT_CORE/$PIN_PRIMARY_PROJECT"
    --config "$PROFILE"
    --report "$report"
  )
  if [[ -n "$patch" ]]; then
    command+=(--patch "$patch")
  fi
  printf '%q ' "${command[@]}" > "$OUTPUT/logs/$mode-cleanup-command.txt"
  printf '\n' >> "$OUTPUT/logs/$mode-cleanup-command.txt"

  local -a display_prefix=()
  if [[ -z "${DISPLAY:-}" ]] && command -v xvfb-run >/dev/null; then
    display_prefix=(xvfb-run --auto-servernum)
  fi
  "${display_prefix[@]}" "${command[@]}" >"$OUTPUT/logs/$mode-cleanup.stdout.log" \
    2>"$OUTPUT/logs/$mode-cleanup.stderr.log"
}

printf 'BASELINE_TESTS\n' > "$OUTPUT/run-state.txt"
copy_corpus_sources "$OUTPUT/corpus/baseline"
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
  || fail "Project-wide cleanup check must report required changes with exit code 2, got $CHECK_STATUS"
[[ -s "$OUTPUT/cleanup-check-report.json" ]] || fail "Cleanup check produced no JSON report"
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
[[ "$APPLY_STATUS" -eq 0 ]] || fail "Project-wide cleanup apply failed with exit code $APPLY_STATUS"
[[ -s "$OUTPUT/cleanup-apply-report.json" ]] || fail "Cleanup apply produced no JSON report"

git -C "$JDT_CORE" diff --check "$OVERLAY_COMMIT" --
git -C "$JDT_CORE" diff --binary "$OVERLAY_COMMIT" -- > "$OUTPUT/migration.patch"
git -C "$JDT_CORE" diff --name-only "$OVERLAY_COMMIT" -- > "$OUTPUT/changed-files.txt"
JAVA_CHANGE_COUNT=$(grep -cE '\.java$' "$OUTPUT/changed-files.txt" || true)
((JAVA_CHANGE_COUNT > 0)) || fail "Cleanup apply changed no Java source files"

printf 'VERIFYING_REAL_CORPUS\n' > "$OUTPUT/run-state.txt"
copy_corpus_sources "$OUTPUT/corpus/migrated"
python3 "$CORPUS_VERIFIER" \
  --repository "$JDT_CORE" \
  --project "$PIN_PRIMARY_PROJECT" \
  --contract "$CORPUS_CONTRACT" \
  --changed-files "$OUTPUT/changed-files.txt" \
  --check-report "$OUTPUT/cleanup-check-report.json" \
  --apply-report "$OUTPUT/cleanup-apply-report.json" \
  --baseline-corpus "$OUTPUT/corpus/baseline" \
  --migrated-corpus "$OUTPUT/corpus/migrated" \
  --output "$OUTPUT/corpus-result.json" \
  > "$OUTPUT/logs/corpus-result.log"

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
export ALLOW_CLEAN_WORKSPACE
python3 - <<'PY'
import hashlib
import json
import os
from pathlib import Path

out = Path(os.environ["OUTPUT"])


def digest(name: str) -> str:
    return hashlib.sha256((out / name).read_bytes()).hexdigest()


corpus = json.loads((out / "corpus-result.json").read_text(encoding="utf-8"))
inventory = json.loads((out / "test-inventory-comparison.json").read_text(encoding="utf-8"))
provenance = {
    "result": "PASS",
    "provisioning": "clean-workspace-mirror"
    if os.environ["ALLOW_CLEAN_WORKSPACE"] == "true"
    else "oomph-advanced-mode",
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
    "eclipseWorkspace": os.environ["OOMPH_WORKSPACE"],
    "javaFilesChanged": int(os.environ["JAVA_CHANGE_COUNT"]),
    "remainingLegacyJUnit3FileCount": corpus["remainingLegacyJUnit3FileCount"],
    "baselineTestCount": inventory["baseline"]["tests"],
    "migratedTestCount": inventory["migrated"]["tests"],
    "artifacts": {
        "cleanupProfileSha256": digest("junit3-to-jupiter.properties"),
        "buildOverlaySha256": digest("jdt-core-r4_40-jupiter.patch"),
        "checkReportSha256": digest("cleanup-check-report.json"),
        "applyReportSha256": digest("cleanup-apply-report.json"),
        "migrationPatchSha256": digest("migration.patch"),
        "corpusResultSha256": digest("corpus-result.json"),
        "testInventorySha256": digest("test-inventory-comparison.json"),
    },
}
(out / "provenance.json").write_text(
    json.dumps(provenance, indent=2, sort_keys=True) + "\n",
    encoding="utf-8",
)
PY

printf 'PASS\n' > "$OUTPUT/run-state.txt"
printf 'Upstream JDT migration QA passed. Evidence: %s\n' "$OUTPUT"
if [[ "$KEEP_CHANGES" == true ]]; then
  RESTORE_REQUIRED=false
  printf 'The overlay commit and migrated source tree were retained for documentation capture.\n'
fi
