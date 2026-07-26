#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
CONFIG_FILE=${PATCHED_JDT_UI_CONFIG:-"$ROOT_DIR/.github/patched-jdt-ui.env"}
PATCH_DIR=${1:?usage: publish_patched_jdt_feature_repository.sh PATCH_DIR COMPATIBILITY_DIR [OUTPUT_DIR]}
COMPATIBILITY_DIR=${2:?usage: publish_patched_jdt_feature_repository.sh PATCH_DIR COMPATIBILITY_DIR [OUTPUT_DIR]}
OUTPUT_DIR=${3:-"$ROOT_DIR/target/patched-jdt-feature-p2"}
WORK_DIR=${PATCHED_JDT_UI_P2_WORK_DIR:-"${RUNNER_TEMP:-${TMPDIR:-/tmp}}/patched-jdt-feature-publisher"}
JDT_FEATURE_ID=org.eclipse.jdt

# shellcheck disable=SC1090
source "$CONFIG_FILE"
: "${PATCHED_JDT_UI_BUNDLE:?missing PATCHED_JDT_UI_BUNDLE}"

for command in find jar java python3 sed sha256sum; do
  command -v "$command" >/dev/null || { echo "Missing required command: $command" >&2; exit 1; }
done

PROVENANCE="$PATCH_DIR/provenance.json"
COMPATIBILITY="$COMPATIBILITY_DIR/compatibility.json"
[[ -f "$PROVENANCE" ]] || { echo "Missing bundle provenance: $PROVENANCE" >&2; exit 1; }
[[ -f "$COMPATIBILITY" ]] || { echo "Missing target compatibility report: $COMPATIBILITY" >&2; exit 1; }

readarray -t patch_values < <(python3 - "$PROVENANCE" "$COMPATIBILITY" "$PATCHED_JDT_UI_BUNDLE" <<'PY'
import json
import re
import sys
from pathlib import Path

provenance = json.loads(Path(sys.argv[1]).read_text(encoding='utf-8'))
compatibility = json.loads(Path(sys.argv[2]).read_text(encoding='utf-8'))
expected_id = sys.argv[3]
if compatibility.get('compatibleForReplacement') is not True:
    raise SystemExit('Compatibility report does not authorize p2 publication')
if provenance.get('bundleSymbolicName') != expected_id:
    raise SystemExit('Bundle provenance has an unexpected symbolic name')
version = str(provenance.get('bundleVersion', ''))
digest = str(provenance.get('bundleSha256', ''))
patched = compatibility.get('patchedBundle') or {}
stock = compatibility.get('stockBundle') or {}
if patched.get('version') != version or patched.get('sha256') != digest:
    raise SystemExit('Compatibility report and provenance refer to different bundle bytes')
if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+\.[A-Za-z0-9_-]+', version):
    raise SystemExit(f'Unsupported qualified bundle version: {version}')
print(version)
print(digest)
print(version.split('.', 3)[3])
print(stock.get('version', ''))
PY
)
BUNDLE_VERSION=${patch_values[0]}
BUNDLE_SHA256=${patch_values[1]}
BUNDLE_QUALIFIER=${patch_values[2]}
STOCK_BUNDLE_VERSION=${patch_values[3]}

mapfile -t bundle_candidates < <(find "$PATCH_DIR/plugins" -maxdepth 1 -type f \
  -name "${PATCHED_JDT_UI_BUNDLE}_${BUNDLE_VERSION}.jar" | sort)
if (( ${#bundle_candidates[@]} != 1 )); then
  printf 'Expected exactly one patched bundle %s_%s.jar, found %d\n' \
    "$PATCHED_JDT_UI_BUNDLE" "$BUNDLE_VERSION" "${#bundle_candidates[@]}" >&2
  exit 1
fi
BUNDLE_JAR=${bundle_candidates[0]}
[[ "$(sha256sum "$BUNDLE_JAR" | awk '{print $1}')" == "$BUNDLE_SHA256" ]] \
  || { echo 'Patched bundle bytes do not match provenance' >&2; exit 1; }

PRODUCTS_DIR="$ROOT_DIR/sandbox_product/target/products"
mapfile -t stock_feature_jars < <(find "$PRODUCTS_DIR" -type f \
  -path '*/features/org.eclipse.jdt_*.jar' | sort -u)
if (( ${#stock_feature_jars[@]} == 0 )); then
  echo "No materialized org.eclipse.jdt feature found below $PRODUCTS_DIR; build -Pproduct first" >&2
  exit 1
fi

# Products for several platforms contain byte-identical copies. Select one only
# after proving that every copy has the same SHA-256.
readarray -t feature_digests < <(for feature in "${stock_feature_jars[@]}"; do sha256sum "$feature"; done \
  | awk '{print $1}' | sort -u)
if (( ${#feature_digests[@]} != 1 )); then
  printf 'Materialized products contain different org.eclipse.jdt feature bytes: %s\n' \
    "${feature_digests[*]}" >&2
  exit 1
fi
STOCK_FEATURE_JAR=${stock_feature_jars[0]}

# Reuse the p2 publisher from the already materialized Linux product. This is
# the same Equinox application that tycho-p2-extras launches, but it avoids a
# second Maven/plugin-resolution layer after the product has been built.
mapfile -t publisher_candidates < <(find "$PRODUCTS_DIR" -type f -name eclipse -perm -u+x \
  -path '*/linux/gtk/x86_64/*' | sort -u)
if (( ${#publisher_candidates[@]} != 1 )); then
  printf 'Expected exactly one Linux Eclipse p2 publisher below %s, found %d\n' \
    "$PRODUCTS_DIR" "${#publisher_candidates[@]}" >&2
  printf 'Candidates: %s\n' "${publisher_candidates[*]:-(none)}" >&2
  exit 1
fi
P2_PUBLISHER=${publisher_candidates[0]}

rm -rf "$WORK_DIR" "$OUTPUT_DIR"
SOURCE_DIR="$WORK_DIR/source"
FEATURE_STAGING="$WORK_DIR/stock-feature"
REPOSITORY_DIR="$OUTPUT_DIR/repository"
EVIDENCE_DIR="$OUTPUT_DIR/evidence"
mkdir -p "$SOURCE_DIR/plugins" "$FEATURE_STAGING" "$REPOSITORY_DIR" "$EVIDENCE_DIR"
cp "$BUNDLE_JAR" "$SOURCE_DIR/plugins/${PATCHED_JDT_UI_BUNDLE}_${BUNDLE_VERSION}.jar"
(
  cd "$FEATURE_STAGING"
  jar --extract --file "$STOCK_FEATURE_JAR"
)
[[ -f "$FEATURE_STAGING/feature.xml" ]] || { echo 'Stock JDT feature JAR contains no feature.xml' >&2; exit 1; }

readarray -t feature_values < <(python3 - "$FEATURE_STAGING/feature.xml" "$BUNDLE_VERSION" "$BUNDLE_QUALIFIER" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

path = Path(sys.argv[1])
patched_bundle_version = sys.argv[2]
qualifier = sys.argv[3]
tree = ET.parse(path)
root = tree.getroot()
if root.get('id') != 'org.eclipse.jdt':
    raise SystemExit(f"Unexpected stock feature id: {root.get('id')}")
stock_version = root.get('version', '')
match = re.fullmatch(r'(\d+)\.(\d+)\.(\d+)(?:\.([A-Za-z0-9_-]+))?', stock_version)
if not match:
    raise SystemExit(f'Unsupported stock feature version: {stock_version}')
major, minor, micro = map(int, match.group(1, 2, 3))
patched_feature_version = f'{major}.{minor}.{micro + 1}.{qualifier}'
plugins = [plugin for plugin in root.findall('plugin') if plugin.get('id') == 'org.eclipse.jdt.ui']
if len(plugins) != 1:
    raise SystemExit(f'Expected exactly one org.eclipse.jdt.ui entry in stock feature, found {len(plugins)}')
plugins[0].set('version', patched_bundle_version)
root.set('version', patched_feature_version)
ET.indent(tree, space='   ')
tree.write(path, encoding='UTF-8', xml_declaration=True)
print(stock_version)
print(patched_feature_version)
PY
)
STOCK_FEATURE_VERSION=${feature_values[0]}
PATCHED_FEATURE_VERSION=${feature_values[1]}
FEATURE_DIR="$SOURCE_DIR/features/${JDT_FEATURE_ID}_${PATCHED_FEATURE_VERSION}"
mkdir -p "$FEATURE_DIR"
cp -a "$FEATURE_STAGING"/. "$FEATURE_DIR/"
cp "$FEATURE_DIR/feature.xml" "$EVIDENCE_DIR/patched-feature.xml"
(
  cd "$WORK_DIR"
  jar --extract --file "$STOCK_FEATURE_JAR" feature.xml
  mv feature.xml "$EVIDENCE_DIR/stock-feature.xml"
)

PUBLISHER_LOG="$EVIDENCE_DIR/publisher.log"
set +e
env -u http_proxy -u https_proxy -u HTTP_PROXY -u HTTPS_PROXY \
  "$P2_PUBLISHER" \
  -nosplash \
  -consolelog \
  -clean \
  -application org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher \
  -metadataRepository "file:$REPOSITORY_DIR" \
  -artifactRepository "file:$REPOSITORY_DIR" \
  -source "$SOURCE_DIR" \
  -compress \
  -publishArtifacts \
  > "$PUBLISHER_LOG" 2>&1
publisher_status=$?
set -e
cat "$PUBLISHER_LOG"
if (( publisher_status != 0 )); then
  printf 'Equinox p2 publisher failed with exit code %d\n' "$publisher_status" >&2
  exit "$publisher_status"
fi

python3 "$ROOT_DIR/.github/scripts/add_p2_sha256_checksums.py" \
  --repository "$REPOSITORY_DIR" \
  > "$EVIDENCE_DIR/checksum-normalization.json"
cp "$PROVENANCE" "$EVIDENCE_DIR/bundle-provenance.json"
cp "$COMPATIBILITY" "$EVIDENCE_DIR/target-compatibility.json"

export REPOSITORY_DIR EVIDENCE_DIR JDT_FEATURE_ID STOCK_FEATURE_VERSION PATCHED_FEATURE_VERSION
export PATCHED_JDT_UI_BUNDLE BUNDLE_VERSION BUNDLE_SHA256 STOCK_BUNDLE_VERSION
python3 <<'PY'
import hashlib
import json
import os
import re
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

repository = Path(os.environ['REPOSITORY_DIR'])
evidence = Path(os.environ['EVIDENCE_DIR'])
feature_id = os.environ['JDT_FEATURE_ID']
feature_version = os.environ['PATCHED_FEATURE_VERSION']
bundle_id = os.environ['PATCHED_JDT_UI_BUNDLE']
bundle_version = os.environ['BUNDLE_VERSION']
expected_bundle_sha = os.environ['BUNDLE_SHA256']


def metadata(stem: str) -> ET.Element:
    jar = repository / f'{stem}.jar'
    xml = repository / f'{stem}.xml'
    if jar.is_file():
        with zipfile.ZipFile(jar) as archive:
            return ET.fromstring(archive.read(f'{stem}.xml'))
    if xml.is_file():
        return ET.parse(xml).getroot()
    raise SystemExit(f'Missing {stem} metadata')


def digest(path: Path) -> str:
    value = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            value.update(chunk)
    return value.hexdigest()


content = metadata('content')
artifacts = metadata('artifacts')
units = content.findall('./units/unit')
by_key = {(unit.get('id'), unit.get('version')): unit for unit in units}
feature_group_key = (f'{feature_id}.feature.group', feature_version)
feature_jar_key = (f'{feature_id}.feature.jar', feature_version)
bundle_key = (bundle_id, bundle_version)
for key in (feature_group_key, feature_jar_key, bundle_key):
    if key not in by_key:
        raise SystemExit(f'Missing expected IU {key}')

feature_group = by_key[feature_group_key]
requirements = feature_group.findall('./requires/required')
matching = [item for item in requirements if item.get('name') == bundle_id]
if len(matching) != 1:
    raise SystemExit(f'Expected one feature requirement on {bundle_id}, found {len(matching)}')
required_range = matching[0].get('range', '')
if bundle_version not in required_range:
    raise SystemExit(f'Feature requirement does not pin patched bundle {bundle_version}: {required_range}')

bundle_path = repository / 'plugins' / f'{bundle_id}_{bundle_version}.jar'
feature_path = repository / 'features' / f'{feature_id}_{feature_version}.jar'
if not bundle_path.is_file() or not feature_path.is_file():
    raise SystemExit('Published repository is missing bundle or replacement feature artifact')
if digest(bundle_path) != expected_bundle_sha:
    raise SystemExit('Published bundle bytes differ from pinned provenance')

artifact_entries = artifacts.findall('./artifacts/artifact')
checksums = {}
for artifact in artifact_entries:
    key = (artifact.get('classifier'), artifact.get('id'), artifact.get('version'))
    props = artifact.find('properties')
    values = {} if props is None else {
        item.get('name', ''): item.get('value', '') for item in props.findall('property')
    }
    if not any(re.fullmatch(r'[0-9a-fA-F]{64}', value) and 'sha' in name.lower()
               for name, value in values.items()):
        raise SystemExit(f'Artifact has no SHA-256 metadata: {key}')
    checksums['/'.join(key)] = values

payload = {
    'schemaVersion': 1,
    'result': 'PASS',
    'stockFeature': {'id': feature_id, 'version': os.environ['STOCK_FEATURE_VERSION']},
    'replacementFeature': {
        'id': feature_id,
        'version': feature_version,
        'groupIU': feature_group_key[0],
        'jarIU': feature_jar_key[0],
        'sha256': digest(feature_path),
    },
    'stockBundle': {'id': bundle_id, 'version': os.environ['STOCK_BUNDLE_VERSION']},
    'patchedBundle': {'id': bundle_id, 'version': bundle_version, 'sha256': expected_bundle_sha},
    'artifactMetadata': checksums,
}
(evidence / 'repository-verification.json').write_text(json.dumps(payload, indent=2) + '\n', encoding='utf-8')
(evidence / 'repository-verification.md').write_text(
    '# Patched JDT feature repository\n\n'
    '- Result: **PASS**\n'
    f'- Stock feature: `{feature_id} {os.environ["STOCK_FEATURE_VERSION"]}`\n'
    f'- Replacement feature: `{feature_id} {feature_version}`\n'
    f'- Patched bundle: `{bundle_id} {bundle_version}`\n'
    '- Exact patched bundle requirement: **PASS**\n'
    '- SHA-256 metadata for every local artifact: **PASS**\n',
    encoding='utf-8',
)
print(json.dumps(payload, indent=2))
PY

printf 'Published replacement %s feature %s with %s %s to %s\n' \
  "$JDT_FEATURE_ID" "$PATCHED_FEATURE_VERSION" "$PATCHED_JDT_UI_BUNDLE" "$BUNDLE_VERSION" "$REPOSITORY_DIR"
