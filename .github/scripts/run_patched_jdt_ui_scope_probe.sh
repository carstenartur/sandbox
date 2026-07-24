#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
P2_ROOT=${1:?usage: run_patched_jdt_ui_scope_probe.sh P2_OUTPUT INSTALLATION_EVIDENCE [PROBE_EVIDENCE]}
INSTALLATION_EVIDENCE=${2:?usage: run_patched_jdt_ui_scope_probe.sh P2_OUTPUT INSTALLATION_EVIDENCE [PROBE_EVIDENCE]}
EVIDENCE_DIR=${3:-"$ROOT_DIR/target/patched-jdt-ui-runtime-probe"}
PROBE_ROOT="$ROOT_DIR/.github/probes/patched-jdt-ui"
WORK_DIR=${PATCHED_JDT_UI_PROBE_WORK_DIR:-"${RUNNER_TEMP:-${TMPDIR:-/tmp}}/patched-jdt-ui-runtime-probe"}
PROBE_ID=sandbox_patched_jdt_ui_runtime_probe
PROBE_VERSION=1.0.0
PROBE_IU="$PROBE_ID/$PROBE_VERSION"
PROBE_APPLICATION="$PROBE_ID.scopeProbe"
INSTALLATION_JSON="$INSTALLATION_EVIDENCE/installation-verification.json"

for command in find jar java javac mvn paste python3 sha256sum timeout xvfb-run; do
  command -v "$command" >/dev/null || { echo "Missing required command: $command" >&2; exit 1; }
done
[[ -f "$INSTALLATION_JSON" ]] || { echo "Missing patched-product evidence: $INSTALLATION_JSON" >&2; exit 1; }
[[ -f "$PROBE_ROOT/META-INF/MANIFEST.MF" ]] || { echo 'Missing runtime probe manifest' >&2; exit 1; }
[[ -f "$PROBE_ROOT/plugin.xml" ]] || { echo 'Missing runtime probe extension declaration' >&2; exit 1; }

readarray -t product_values < <(python3 - "$INSTALLATION_JSON" <<'PY'
import json
import sys
from pathlib import Path

report = json.loads(Path(sys.argv[1]).read_text(encoding='utf-8'))
if report.get('result') != 'PASS':
    raise SystemExit('Patched-product installation evidence is not PASS')
print(report['productRoot'])
print(report['profileId'])
print(report['patchedBundle']['id'])
print(report['patchedBundle']['version'])
PY
)
PRODUCT_ROOT=${product_values[0]}
PROFILE_ID=${product_values[1]}
PATCHED_BUNDLE_ID=${product_values[2]}
PATCHED_BUNDLE_VERSION=${product_values[3]}
[[ -d "$PRODUCT_ROOT" ]] || { echo "Product root does not exist: $PRODUCT_ROOT" >&2; exit 1; }

mapfile -t launchers < <(find "$PRODUCT_ROOT/plugins" -maxdepth 1 -type f \
  -name 'org.eclipse.equinox.launcher_*.jar' | sort)
if (( ${#launchers[@]} != 1 )); then
  printf 'Expected one Equinox launcher, found %d: %s\n' \
    "${#launchers[@]}" "${launchers[*]:-<none>}" >&2
  exit 1
fi
LAUNCHER=${launchers[0]}
BUNDLES_INFO="$PRODUCT_ROOT/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info"
[[ -f "$BUNDLES_INFO" ]] || { echo "Missing bundles.info: $BUNDLES_INFO" >&2; exit 1; }
mapfile -t patched_lines < <(awk -F, -v id="$PATCHED_BUNDLE_ID" '$1 == id { print }' "$BUNDLES_INFO")
if (( ${#patched_lines[@]} != 1 )) || [[ "$(awk -F, '{print $2}' <<<"${patched_lines[0]:-}")" != "$PATCHED_BUNDLE_VERSION" ]]; then
  echo "Runtime probe requires exactly one selected $PATCHED_BUNDLE_ID $PATCHED_BUNDLE_VERSION" >&2
  exit 1
fi

rm -rf "$WORK_DIR" "$EVIDENCE_DIR"
CLASSES_DIR="$WORK_DIR/classes"
SOURCE_DIR="$WORK_DIR/source"
REPOSITORY_DIR="$WORK_DIR/repository"
mkdir -p "$CLASSES_DIR" "$SOURCE_DIR/plugins" "$REPOSITORY_DIR" "$EVIDENCE_DIR"

mapfile -t product_jars < <(find "$PRODUCT_ROOT/plugins" -maxdepth 1 -type f -name '*.jar' | sort)
if (( ${#product_jars[@]} == 0 )); then
  echo "Materialized product contains no plug-in JARs: $PRODUCT_ROOT" >&2
  exit 1
fi
PRODUCT_CLASSPATH=$(IFS=:; echo "${product_jars[*]}")
mapfile -t probe_sources < <(find "$PROBE_ROOT/src" -type f -name '*.java' | sort)
if (( ${#probe_sources[@]} == 0 )); then
  echo 'Runtime probe contains no Java sources' >&2
  exit 1
fi

javac --release 21 -encoding UTF-8 \
  -classpath "$PRODUCT_CLASSPATH" \
  -d "$CLASSES_DIR" \
  "${probe_sources[@]}" \
  2>&1 | tee "$EVIDENCE_DIR/compile.log"

PROBE_JAR="$SOURCE_DIR/plugins/${PROBE_ID}_${PROBE_VERSION}.jar"
jar --create \
  --file "$PROBE_JAR" \
  --manifest "$PROBE_ROOT/META-INF/MANIFEST.MF" \
  -C "$CLASSES_DIR" . \
  -C "$PROBE_ROOT" plugin.xml
jar --list --file "$PROBE_JAR" > "$EVIDENCE_DIR/probe-jar-entries.txt"
grep -Fq 'org/sandbox/jdt/ui/probe/ScopeExpansionProbeApplication.class' "$EVIDENCE_DIR/probe-jar-entries.txt"
grep -Fq 'plugin.xml' "$EVIDENCE_DIR/probe-jar-entries.txt"
PROBE_SHA256=$(sha256sum "$PROBE_JAR" | awk '{print $1}')

TYCHO_VERSION=$(mvn -q -ntp -f "$ROOT_DIR/pom.xml" help:evaluate \
  -Dexpression=tycho-version -DforceStdout \
  | sed -nE '/^[0-9]+\.[0-9]+\.[0-9]+([.-].*)?$/p' \
  | tail -n 1)
if ! [[ "$TYCHO_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-].*)?$ ]]; then
  echo "Could not determine Tycho version: $TYCHO_VERSION" >&2
  exit 1
fi

cat > "$WORK_DIR/pom.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.sandbox.build</groupId>
  <artifactId>patched-jdt-ui-runtime-probe-publisher</artifactId>
  <version>1.0.0</version>
  <packaging>pom</packaging>
  <build>
    <plugins>
      <plugin>
        <groupId>org.eclipse.tycho.extras</groupId>
        <artifactId>tycho-p2-extras-plugin</artifactId>
        <version>$TYCHO_VERSION</version>
        <configuration>
          <sourceLocation>\${source.location}</sourceLocation>
          <metadataRepositoryLocation>\${repository.location}</metadataRepositoryLocation>
          <artifactRepositoryLocation>\${repository.location}</artifactRepositoryLocation>
          <compress>true</compress>
          <publishArtifacts>true</publishArtifacts>
          <append>false</append>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
EOF

mvn --batch-mode -ntp -f "$WORK_DIR/pom.xml" \
  "org.eclipse.tycho.extras:tycho-p2-extras-plugin:${TYCHO_VERSION}:publish-features-and-bundles" \
  -Dsource.location="$SOURCE_DIR" \
  -Drepository.location="$REPOSITORY_DIR" \
  2>&1 | tee "$EVIDENCE_DIR/publisher.log"

python3 "$ROOT_DIR/.github/scripts/add_p2_sha256_checksums.py" \
  --repository "$REPOSITORY_DIR" \
  > "$EVIDENCE_DIR/probe-checksum-normalization.json"
python3 - "$REPOSITORY_DIR" "$PROBE_ID" "$PROBE_VERSION" "$PROBE_SHA256" "$EVIDENCE_DIR" <<'PY'
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

repository = Path(sys.argv[1])
probe_id = sys.argv[2]
probe_version = sys.argv[3]
expected_sha = sys.argv[4]
evidence = Path(sys.argv[5])


def repository_xml(stem: str) -> ET.Element:
    jar = repository / f'{stem}.jar'
    xml = repository / f'{stem}.xml'
    if jar.is_file():
        with zipfile.ZipFile(jar) as archive:
            return ET.fromstring(archive.read(f'{stem}.xml'))
    if xml.is_file():
        return ET.parse(xml).getroot()
    raise SystemExit(f'Probe repository is missing {stem}.jar and {stem}.xml')


def sha256(path: Path) -> str:
    value = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            value.update(chunk)
    return value.hexdigest()


content = repository_xml('content')
artifacts = repository_xml('artifacts')
matching_units = [
    unit for unit in content.findall('./units/unit')
    if unit.attrib.get('id') == probe_id and unit.attrib.get('version') == probe_version
]
if len(matching_units) != 1:
    raise SystemExit(f'Expected one probe IU, found {len(matching_units)}')
keys = {
    (item.attrib.get('classifier'), item.attrib.get('id'), item.attrib.get('version'))
    for item in artifacts.findall('./artifacts/artifact')
}
expected_key = ('osgi.bundle', probe_id, probe_version)
if keys != {expected_key}:
    raise SystemExit(f'Unexpected probe repository artifacts: {sorted(keys)}')
artifact = artifacts.find(
    f"./artifacts/artifact[@classifier='osgi.bundle'][@id='{probe_id}'][@version='{probe_version}']"
)
if artifact is None:
    raise SystemExit('Probe artifact metadata is missing')
properties = artifact.find('properties')
metadata = {} if properties is None else {
    item.attrib.get('name', ''): item.attrib.get('value', '')
    for item in properties.findall('property')
}
path = repository / 'plugins' / f'{probe_id}_{probe_version}.jar'
if not path.is_file():
    raise SystemExit(f'Probe repository references a missing plug-in: {path}')
if sha256(path) != expected_sha:
    raise SystemExit('Published probe plug-in bytes differ from the compiled JAR')
size = metadata.get('download.size') or metadata.get('artifact.size')
if size and size.isdigit() and path.stat().st_size != int(size):
    raise SystemExit('Published probe plug-in size differs from p2 metadata')
checksums = [
    name for name, value in metadata.items()
    if (re.fullmatch(r'[0-9a-fA-F]{64}', value) and ('sha-256' in name.lower() or 'sha256' in name.lower()))
    or (re.fullmatch(r'[0-9a-fA-F]{32}', value) and 'md5' in name.lower())
]
if not checksums:
    raise SystemExit('Probe p2 metadata contains no verifiable checksum')
payload = {
    'schemaVersion': 1,
    'result': 'PASS',
    'iu': {'id': probe_id, 'version': probe_version},
    'bundleSha256': expected_sha,
    'checksumProperties': sorted(checksums),
}
(evidence / 'probe-repository.json').write_text(json.dumps(payload, indent=2) + '\n', encoding='utf-8')
print(json.dumps(payload, indent=2))
PY

REPOSITORY_URI=$(python3 - "$REPOSITORY_DIR" <<'PY'
import sys
from pathlib import Path
print(Path(sys.argv[1]).resolve().as_uri())
PY
)
(
  cd "$PRODUCT_ROOT"
  timeout 600s xvfb-run -a java -jar "$LAUNCHER" -nosplash -consoleLog \
    -application org.eclipse.equinox.p2.director \
    -repository "$REPOSITORY_URI" \
    -installIU "$PROBE_IU" \
    -destination "$PRODUCT_ROOT" \
    -bundlepool "$PRODUCT_ROOT" \
    -profile "$PROFILE_ID" \
    -p2.os linux -p2.ws gtk -p2.arch x86_64
) > "$EVIDENCE_DIR/install-probe.log" 2>&1

mapfile -t probe_lines < <(awk -F, -v id="$PROBE_ID" '$1 == id { print }' "$BUNDLES_INFO")
if (( ${#probe_lines[@]} != 1 )) || [[ "$(awk -F, '{print $2}' <<<"${probe_lines[0]:-}")" != "$PROBE_VERSION" ]]; then
  echo "Product did not select exactly one runtime probe bundle $PROBE_ID $PROBE_VERSION" >&2
  exit 1
fi
printf '%s\n' "${probe_lines[0]}" > "$EVIDENCE_DIR/probe-bundle-selection.txt"

WORKSPACE_DIR="$EVIDENCE_DIR/workspace"
RESULT_JSON="$EVIDENCE_DIR/probe-result.json"
rm -rf "$WORKSPACE_DIR"
mkdir -p "$WORKSPACE_DIR"
(
  cd "$PRODUCT_ROOT"
  SANDBOX_PATCHED_JDT_UI_PROBE_REPORT="$RESULT_JSON" \
    timeout 300s xvfb-run -a java -jar "$LAUNCHER" -nosplash -consoleLog -clean \
      -application "$PROBE_APPLICATION" \
      -data "$WORKSPACE_DIR"
) > "$EVIDENCE_DIR/runtime.log" 2>&1

grep -Fq 'PATCHED_JDT_UI_SCOPE_PROBE_PASS' "$EVIDENCE_DIR/runtime.log"
python3 - "$RESULT_JSON" "$EVIDENCE_DIR" "$PATCHED_BUNDLE_ID" "$PATCHED_BUNDLE_VERSION" \
  "$PROBE_ID" "$PROBE_VERSION" "$PROBE_SHA256" <<'PY'
import json
import sys
from pathlib import Path

result_path = Path(sys.argv[1])
evidence = Path(sys.argv[2])
patched_id = sys.argv[3]
patched_version = sys.argv[4]
probe_id = sys.argv[5]
probe_version = sys.argv[6]
probe_sha = sys.argv[7]
result = json.loads(result_path.read_text(encoding='utf-8'))
expected_exact = {
    'result': 'PASS',
    'targetCount': 2,
    'plannedCount': 2,
    'previewCount': 2,
    'appliedCount': 2,
    'restoredCount': 2,
}
for key, expected in expected_exact.items():
    if result.get(key) != expected:
        raise SystemExit(f'Unexpected runtime probe value {key}: {result.get(key)!r}, expected {expected!r}')
if not isinstance(result.get('expansionInvocations'), int) or result['expansionInvocations'] < 2:
    raise SystemExit(f"Scope expansion did not reach a fixed point: {result.get('expansionInvocations')!r}")
payload = {
    'schemaVersion': 1,
    'result': 'PASS',
    'patchedBundle': {'id': patched_id, 'version': patched_version},
    'probeBundle': {'id': probe_id, 'version': probe_version, 'sha256': probe_sha},
    'behavior': result,
}
(evidence / 'runtime-verification.json').write_text(
    json.dumps(payload, indent=2) + '\n', encoding='utf-8'
)
lines = [
    '# Patched JDT UI runtime behavior verification',
    '',
    '- Result: **PASS**',
    f'- Patched bundle: `{patched_id} {patched_version}`',
    f'- Probe bundle: `{probe_id} {probe_version}`',
    '- One explicitly selected Java file expanded to two cleanup targets: **PASS**',
    '- Both compilation units reached preconditions and preview: **PASS**',
    '- Both source files changed through one composite cleanup: **PASS**',
    '- Undo restored both files byte-for-byte: **PASS**',
    f"- Scope-expansion invocations: **{result['expansionInvocations']}** (fixed point reached)",
]
(evidence / 'runtime-verification.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')
print(json.dumps(payload, indent=2))
PY

rm -rf "$WORKSPACE_DIR"
