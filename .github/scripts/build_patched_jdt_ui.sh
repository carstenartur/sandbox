#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
CONFIG_FILE=${PATCHED_JDT_UI_CONFIG:-"$ROOT_DIR/.github/patched-jdt-ui.env"}
OUTPUT_DIR=${1:-"$ROOT_DIR/target/patched-jdt-ui"}
WORK_DIR=${PATCHED_JDT_UI_WORK_DIR:-"${RUNNER_TEMP:-${TMPDIR:-/tmp}}/patched-jdt-ui-source"}

if [[ ! -f "$CONFIG_FILE" ]]; then
  echo "Missing patch configuration: $CONFIG_FILE" >&2
  exit 1
fi
# shellcheck disable=SC1090
source "$CONFIG_FILE"

: "${PATCHED_JDT_UI_REPOSITORY:?missing PATCHED_JDT_UI_REPOSITORY}"
: "${PATCHED_JDT_UI_COMMIT:?missing PATCHED_JDT_UI_COMMIT}"
: "${PATCHED_JDT_UI_BUNDLE:?missing PATCHED_JDT_UI_BUNDLE}"
: "${PATCHED_JDT_UI_EXPECTED_BASE_VERSION:?missing PATCHED_JDT_UI_EXPECTED_BASE_VERSION}"

if [[ ! "$PATCHED_JDT_UI_COMMIT" =~ ^[0-9a-f]{40}$ ]]; then
  echo "PATCHED_JDT_UI_COMMIT must be an immutable 40-character SHA" >&2
  exit 1
fi

for command in git mvn javap python3 sha256sum; do
  command -v "$command" >/dev/null || { echo "Missing required command: $command" >&2; exit 1; }
done

rm -rf "$WORK_DIR" "$OUTPUT_DIR"
mkdir -p "$WORK_DIR" "$OUTPUT_DIR/plugins"

git -C "$WORK_DIR" init -q
git -C "$WORK_DIR" remote add origin "$PATCHED_JDT_UI_REPOSITORY"
git -C "$WORK_DIR" fetch --depth=1 origin "$PATCHED_JDT_UI_COMMIT"
git -C "$WORK_DIR" checkout --detach -q FETCH_HEAD
ACTUAL_COMMIT=$(git -C "$WORK_DIR" rev-parse HEAD)
if [[ "$ACTUAL_COMMIT" != "$PATCHED_JDT_UI_COMMIT" ]]; then
  echo "Checked out $ACTUAL_COMMIT instead of $PATCHED_JDT_UI_COMMIT" >&2
  exit 1
fi

PRODUCTION_SOURCE='org.eclipse.jdt.ui/core extension/org/eclipse/jdt/internal/corext/fix/CleanUpRefactoring.java'
SCOPE_TEST='org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/quickfix/MultiFileCleanUpScopeExpansionTest.java'
SYNC_MANIFEST='.github/fork-specific-files.txt'
for path in "$PRODUCTION_SOURCE" "$SCOPE_TEST" "$SYNC_MANIFEST"; do
  [[ -f "$WORK_DIR/$path" ]] || { echo "Pinned source revision is missing $path" >&2; exit 1; }
done
grep -Fq 'EXPAND_CLEAN_UP_SCOPE_METHOD' "$WORK_DIR/$PRODUCTION_SOURCE"
grep -Fq 'MultiFileCleanUpScopeExpansionTest' "$WORK_DIR/$SCOPE_TEST"
grep -Fq "$PRODUCTION_SOURCE" "$WORK_DIR/$SYNC_MANIFEST"
grep -Fq "$SCOPE_TEST" "$WORK_DIR/$SYNC_MANIFEST"

mvn --batch-mode -ntp -f "$WORK_DIR/pom.xml" \
  -Pbuild-individual-bundles \
  -pl "$PATCHED_JDT_UI_BUNDLE" -am \
  -DskipTests \
  clean verify

mapfile -t candidates < <(find "$WORK_DIR/$PATCHED_JDT_UI_BUNDLE/target" -maxdepth 1 -type f \
  -name "$PATCHED_JDT_UI_BUNDLE-*.jar" \
  ! -name '*-sources.jar' ! -name '*-javadoc.jar' | sort)
if (( ${#candidates[@]} != 1 )); then
  printf 'Expected exactly one built %s bundle, found %d:\n' "$PATCHED_JDT_UI_BUNDLE" "${#candidates[@]}" >&2
  printf '  %s\n' "${candidates[@]:-<none>}" >&2
  exit 1
fi
BUNDLE_JAR=${candidates[0]}

python3 - "$BUNDLE_JAR" "$OUTPUT_DIR/MANIFEST.MF" <<'PY'
import sys
import zipfile
from pathlib import Path

with zipfile.ZipFile(sys.argv[1]) as archive:
    manifest = archive.read('META-INF/MANIFEST.MF')
Path(sys.argv[2]).write_bytes(manifest)
PY

readarray -t manifest_values < <(python3 - "$OUTPUT_DIR/MANIFEST.MF" <<'PY'
import sys
from pathlib import Path

logical=[]
for line in Path(sys.argv[1]).read_text(encoding='utf-8').splitlines():
    if line.startswith(' ') and logical:
        logical[-1] += line[1:]
    else:
        logical.append(line)
headers={}
for line in logical:
    if ':' in line:
        key, value=line.split(':', 1)
        headers[key.strip()]=value.strip()
print(headers.get('Bundle-SymbolicName', '').split(';', 1)[0])
print(headers.get('Bundle-Version', ''))
PY
)
SYMBOLIC_NAME=${manifest_values[0]:-}
BUNDLE_VERSION=${manifest_values[1]:-}
if [[ "$SYMBOLIC_NAME" != "$PATCHED_JDT_UI_BUNDLE" ]]; then
  echo "Unexpected Bundle-SymbolicName: $SYMBOLIC_NAME" >&2
  exit 1
fi
if ! grep -Fq 'Bundle-SymbolicName: org.eclipse.jdt.ui; singleton:=true' "$OUTPUT_DIR/MANIFEST.MF"; then
  echo 'Built bundle is not the required singleton org.eclipse.jdt.ui replacement' >&2
  exit 1
fi
if [[ "$BUNDLE_VERSION" == *qualifier* || ! "$BUNDLE_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+\..+$ ]]; then
  echo "Bundle-Version was not reproducibly qualified: $BUNDLE_VERSION" >&2
  exit 1
fi
if [[ "$BUNDLE_VERSION" != "$PATCHED_JDT_UI_EXPECTED_BASE_VERSION".* ]]; then
  echo "Built version $BUNDLE_VERSION does not match expected base $PATCHED_JDT_UI_EXPECTED_BASE_VERSION" >&2
  exit 1
fi

javap -classpath "$BUNDLE_JAR" -private org.eclipse.jdt.internal.corext.fix.CleanUpRefactoring \
  | grep -Fq 'EXPAND_CLEAN_UP_SCOPE_METHOD'

DESTINATION="$OUTPUT_DIR/plugins/${PATCHED_JDT_UI_BUNDLE}_${BUNDLE_VERSION}.jar"
cp "$BUNDLE_JAR" "$DESTINATION"
BUNDLE_SHA256=$(sha256sum "$DESTINATION" | awk '{print $1}')
JAVA_VERSION=$(java -version 2>&1 | head -n 1)
MAVEN_VERSION=$(mvn --version | head -n 1)

export OUTPUT_DIR PATCHED_JDT_UI_REPOSITORY PATCHED_JDT_UI_COMMIT PATCHED_JDT_UI_BUNDLE
export SYMBOLIC_NAME BUNDLE_VERSION BUNDLE_SHA256 JAVA_VERSION MAVEN_VERSION
export PRODUCTION_SOURCE SCOPE_TEST
python3 <<'PY'
import json
import os
from pathlib import Path

bundle = os.environ['PATCHED_JDT_UI_BUNDLE']
version = os.environ['BUNDLE_VERSION']
payload = {
    'schemaVersion': 1,
    'sourceRepository': os.environ['PATCHED_JDT_UI_REPOSITORY'],
    'sourceCommit': os.environ['PATCHED_JDT_UI_COMMIT'],
    'bundleSymbolicName': os.environ['SYMBOLIC_NAME'],
    'bundleVersion': version,
    'bundleFile': f'plugins/{bundle}_{version}.jar',
    'bundleSha256': os.environ['BUNDLE_SHA256'],
    'buildProfile': 'build-individual-bundles',
    'java': os.environ['JAVA_VERSION'],
    'maven': os.environ['MAVEN_VERSION'],
    'scopeExpansionSource': os.environ['PRODUCTION_SOURCE'],
    'scopeExpansionTest': os.environ['SCOPE_TEST'],
}
output = Path(os.environ['OUTPUT_DIR']) / 'provenance.json'
output.write_text(json.dumps(payload, indent=2) + '\n', encoding='utf-8')
PY
python3 -m json.tool "$OUTPUT_DIR/provenance.json" >/dev/null

printf 'Built %s %s from %s\nSHA-256: %s\n' \
  "$SYMBOLIC_NAME" "$BUNDLE_VERSION" "$PATCHED_JDT_UI_COMMIT" "$BUNDLE_SHA256"
