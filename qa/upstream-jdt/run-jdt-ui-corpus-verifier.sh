#!/usr/bin/env bash
# Thin process adapter; the corpus verdict belongs to Maven/JUnit.
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
SANDBOX_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd -P)"
MAVEN_BIN="${MAVEN_BIN:-mvn}"
REPOSITORY="" BASELINE="" CONTRACT="" MODE=""
CHANGED_FILES="" CHECK_REPORT="" APPLY_REPORT="" OUTPUT=""

while (($#)); do
  case "$1" in
    --repository) REPOSITORY=${2:?missing value}; shift 2 ;;
    --baseline-sources) BASELINE=${2:?missing value}; shift 2 ;;
    --contract) CONTRACT=${2:?missing value}; shift 2 ;;
    --mode) MODE=${2:?missing value}; shift 2 ;;
    --changed-files) CHANGED_FILES=${2:?missing value}; shift 2 ;;
    --check-report) CHECK_REPORT=${2:?missing value}; shift 2 ;;
    --apply-report) APPLY_REPORT=${2:?missing value}; shift 2 ;;
    --output) OUTPUT=${2:?missing value}; shift 2 ;;
    *) printf 'Unknown corpus-verifier argument: %s\n' "$1" >&2; exit 2 ;;
  esac
done

[[ -n "$MODE" ]] || { echo 'Missing corpus mode' >&2; exit 2; }
for variable in REPOSITORY BASELINE CONTRACT CHANGED_FILES CHECK_REPORT APPLY_REPORT OUTPUT; do
  [[ -n "${!variable}" ]] || { printf 'Missing corpus path: %s\n' "$variable" >&2; exit 2; }
  if [[ "${!variable}" != /* ]]; then
    printf -v "$variable" '%s/%s' "$PWD" "${!variable}"
  fi
done

EVIDENCE_DIR="$(dirname -- "$OUTPUT")"
mkdir -p "$EVIDENCE_DIR/logs"
# A successful Maven process that selected no test must not reuse an old PASS.
rm -f -- "$OUTPUT"
command=(
  "$MAVEN_BIN"
  --batch-mode
  --no-transfer-progress
  -f "$SANDBOX_ROOT/pom.xml"
  -DskipTests=false
  -Dmaven.test.skip=false
  "-Dtest=org.sandbox.jdt.triggerpattern.test.policy.JdtUiCorpusEvidenceVerifierTest#configuredUpstreamEvidenceIsVerifiedByMaven"
  -Dsurefire.failIfNoSpecifiedTests=false
  -DfailIfNoTests=false
  "-Dsandbox.junit.corpus.repository=$REPOSITORY"
  "-Dsandbox.junit.corpus.baseline=$BASELINE"
  "-Dsandbox.junit.corpus.contract=$CONTRACT"
  "-Dsandbox.junit.corpus.mode=$MODE"
  "-Dsandbox.junit.corpus.changedFiles=$CHANGED_FILES"
  "-Dsandbox.junit.corpus.checkReport=$CHECK_REPORT"
  "-Dsandbox.junit.corpus.applyReport=$APPLY_REPORT"
  "-Dsandbox.junit.corpus.output=$OUTPUT"
  -pl sandbox_target,sandbox_common_test
  -am
  package
)
printf '%q ' "${command[@]}" > "$EVIDENCE_DIR/corpus-verification-command.txt"
printf '\n' >> "$EVIDENCE_DIR/corpus-verification-command.txt"
log="$EVIDENCE_DIR/logs/corpus-verification-maven.log"
if "${command[@]}" > "$log" 2>&1; then
  status=0
else
  status=$?
fi
printf '%s\n' "$status" > "$EVIDENCE_DIR/corpus-verification-maven-exit-code.txt"
if ((status != 0)); then
  rm -f -- "$OUTPUT"
  cat "$log" >&2
  exit "$status"
fi
if [[ ! -s "$OUTPUT" ]]; then
  cat "$log" >&2
  echo 'The Maven/JUnit corpus verifier produced no fresh evidence.' >&2
  exit 3
fi
cat -- "$OUTPUT"
