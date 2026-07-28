#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
CONFIG_FILE=${PATCHED_JDT_UI_CONFIG:-"$ROOT_DIR/.github/patched-jdt-ui.env"}
PATCH_DIR=${1:?usage: publish_patched_jdt_ui_repository.sh PATCH_DIR COMPATIBILITY_DIR [OUTPUT_DIR]}
COMPATIBILITY_DIR=${2:?usage: publish_patched_jdt_ui_repository.sh PATCH_DIR COMPATIBILITY_DIR [OUTPUT_DIR]}
OUTPUT_DIR=${3:-"$ROOT_DIR/target/patched-jdt-ui-p2"}
WORK_DIR=${PATCHED_JDT_UI_P2_WORK_DIR:-"${RUNNER_TEMP:-${TMPDIR:-/tmp}}/patched-jdt-ui-p2-publisher"}
FEATURE_ID=${PATCHED_JDT_UI_FEATURE_ID:-sandbox_patched_jdt_ui_feature}

# shellcheck disable=SC1090
source "$CONFIG_FILE"
: "${PATCHED_JDT_UI_BUNDLE:?missing PATCHED_JDT_UI_BUNDLE}"
: "${PATCHED_JDT_UI_TARGET_FEATURE:?missing PATCHED_JDT_UI_TARGET_FEATURE}"
: "${PATCHED_JDT_UI_TARGET_FEATURE_VERSION:?missing PATCHED_JDT_UI_TARGET_FEATURE_VERSION}"

for command in awk find java mvn python3 sed sha256sum sort tail; do
  command -v "$command" >/dev/null || { echo "Missing required command: $command" >&2; exit 1; }
done

PROVENANCE="$PATCH_DIR/provenance.json"
COMPATIBILITY="$COMPATIBILITY_DIR/compatibility.json"
[[ -f "$PROVENANCE" ]] || { echo "Missing bundle provenance: $PROVENANCE" >&2; exit 1; }
[[ -f "$COMPATIBILITY" ]] || { echo "Missing target compatibility report: $COMPATIBILITY" >&2; exit 1; }

readarray -t values < <(python3 - "$PROVENANCE" "$COMPATIBILITY" "$PATCHED_JDT_UI_BUNDLE" <<'PY'
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
sha256 = str(provenance.get('bundleSha256', ''))
patched = compatibility.get('patchedBundle') or {}
if patched.get('version') != version or patched.get('sha256') != sha256:
    raise SystemExit('Compatibility report and provenance refer to different bundle bytes')
if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+\.[A-Za-z0-9_-]+', version):
    raise SystemExit(f'Unsupported qualified bundle version: {version}')
print(version)
print(sha256)
print(version.split('.', 3)[3])
PY
)
BUNDLE_VERSION=${values[0]}
BUNDLE_SHA256=${values[1]}
BUNDLE_QUALIFIER=${values[2]}
FEATURE_VERSION="1.0.0.${BUNDLE_QUALIFIER}"

mapfile -t bundle_candidates < <(find "$PATCH_DIR/plugins" -maxdepth 1 -type f \
  -name "${PATCHED_JDT_UI_BUNDLE}_${BUNDLE_VERSION}.jar" | sort)
if (( ${#bundle_candidates[@]} != 1 )); then
  printf 'Expected exactly one patched bundle %s_%s.jar, found %d\n' \
    "$PATCHED_JDT_UI_BUNDLE" "$BUNDLE_VERSION" "${#bundle_candidates[@]}" >&2
  exit 1
fi
BUNDLE_JAR=${bundle_candidates[0]}
if [[ "$(sha256sum "$BUNDLE_JAR" | awk '{print $1}')" != "$BUNDLE_SHA256" ]]; then
  echo 'Patched bundle bytes do not match provenance before publication' >&2
  exit 1
fi

SOURCE_DIR="$WORK_DIR/source"
FEATURE_DIR="$SOURCE_DIR/features/${FEATURE_ID}_${FEATURE_VERSION}"
REPOSITORY_DIR="$OUTPUT_DIR/repository"
EVIDENCE_DIR="$OUTPUT_DIR/evidence"
rm -rf "$WORK_DIR" "$OUTPUT_DIR"
mkdir -p "$SOURCE_DIR/plugins" "$FEATURE_DIR" "$REPOSITORY_DIR" "$EVIDENCE_DIR"
cp "$BUNDLE_JAR" "$SOURCE_DIR/plugins/${PATCHED_JDT_UI_BUNDLE}_${BUNDLE_VERSION}.jar"

cat > "$FEATURE_DIR/feature.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<feature
      id="$FEATURE_ID"
      label="Sandbox Patched JDT UI"
      version="$FEATURE_VERSION"
      provider-name="Sandbox">
   <description>
      Exact feature patch for the reviewed Sandbox JDT UI scope-expansion change.
   </description>
   <copyright>
      Copyright (c) Eclipse contributors and Sandbox contributors.
   </copyright>
   <license url="https://www.eclipse.org/legal/epl-2.0/">
      Eclipse Public License 2.0
   </license>
   <requires>
      <import feature="$PATCHED_JDT_UI_TARGET_FEATURE" version="$PATCHED_JDT_UI_TARGET_FEATURE_VERSION" patch="true"/>
   </requires>
   <plugin
         id="$PATCHED_JDT_UI_BUNDLE"
         download-size="0"
         install-size="0"
         version="$BUNDLE_VERSION"
         unpack="false"/>
</feature>
EOF

TYCHO_VERSION=$(mvn -q -ntp -f "$ROOT_DIR/pom.xml" help:evaluate \
  -Dexpression=tycho-version -DforceStdout \
  | sed -nE '/^[0-9]+\.[0-9]+\.[0-9]+([.-].*)?$/p' \
  | tail -n 1)
if ! [[ "$TYCHO_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-].*)?$ ]]; then
  echo "Could not determine a Tycho version from the reactor: $TYCHO_VERSION" >&2
  exit 1
fi

cat > "$WORK_DIR/pom.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.sandbox.build</groupId>
  <artifactId>patched-jdt-ui-p2-publisher</artifactId>
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
  -Drepository.location="$REPOSITORY_DIR"

python3 "$ROOT_DIR/.github/scripts/add_p2_sha256_checksums.py" \
  --repository "$REPOSITORY_DIR" \
  > "$EVIDENCE_DIR/checksum-normalization.json"
python3 "$ROOT_DIR/.github/scripts/verify_minimal_patched_jdt_ui_units.py" \
  --repository "$REPOSITORY_DIR" \
  --bundle-id "$PATCHED_JDT_UI_BUNDLE" \
  --bundle-version "$BUNDLE_VERSION" \
  --feature-id "$FEATURE_ID" \
  --feature-version "$FEATURE_VERSION" \
  > "$EVIDENCE_DIR/minimal-unit-set.txt"
cp "$PROVENANCE" "$EVIDENCE_DIR/bundle-provenance.json"
cp "$COMPATIBILITY" "$EVIDENCE_DIR/target-compatibility.json"
cp "$FEATURE_DIR/feature.xml" "$EVIDENCE_DIR/feature.xml"
python3 "$ROOT_DIR/.github/scripts/verify_patched_jdt_ui_repository.py" \
  --repository "$REPOSITORY_DIR" \
  --bundle-provenance "$PROVENANCE" \
  --compatibility "$COMPATIBILITY" \
  --feature-id "$FEATURE_ID" \
  --feature-version "$FEATURE_VERSION" \
  --output "$EVIDENCE_DIR"

python3 - "$REPOSITORY_DIR" "$FEATURE_DIR/feature.xml" "$FEATURE_ID" "$FEATURE_VERSION" \
  "$PATCHED_JDT_UI_TARGET_FEATURE" "$PATCHED_JDT_UI_TARGET_FEATURE_VERSION" \
  "$PATCHED_JDT_UI_BUNDLE" "$BUNDLE_VERSION" "$EVIDENCE_DIR" <<'PY'
import json
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

repository = Path(sys.argv[1])
feature_xml_path = Path(sys.argv[2])
feature_id = sys.argv[3]
feature_version = sys.argv[4]
target_feature_id = sys.argv[5]
target_feature_version = sys.argv[6]
bundle_id = sys.argv[7]
bundle_version = sys.argv[8]
evidence = Path(sys.argv[9])


def repository_xml() -> ET.Element:
    jar = repository / 'content.jar'
    xml = repository / 'content.xml'
    if jar.is_file():
        with zipfile.ZipFile(jar) as archive:
            return ET.fromstring(archive.read('content.xml'))
    if xml.is_file():
        return ET.parse(xml).getroot()
    raise SystemExit(f'Repository is missing content.jar and content.xml: {repository}')


def has_exact_requirement(unit: ET.Element, identifier: str, version: str) -> bool:
    expected = {version, f'[{version},{version}]'}
    return any(
        item.attrib.get('name') == identifier
        and item.attrib.get('namespace') in (None, 'org.eclipse.equinox.p2.iu')
        and item.attrib.get('range', '') in expected
        for item in unit.findall('./requires/required')
    )

feature_xml = ET.parse(feature_xml_path).getroot()
if feature_xml.attrib.get('id') != feature_id or feature_xml.attrib.get('version') != feature_version:
    raise SystemExit('Patch feature identity differs from the expected feature')
patch_imports = [
    item for item in feature_xml.findall('./requires/import')
    if item.attrib.get('patch') == 'true'
]
if len(patch_imports) != 1:
    raise SystemExit(f'Expected one patch import, found {len(patch_imports)}')
patch_import = patch_imports[0]
if patch_import.attrib.get('feature') != target_feature_id \
        or patch_import.attrib.get('version') != target_feature_version:
    raise SystemExit(f'Patch import targets the wrong feature: {patch_import.attrib}')
if patch_import.attrib.get('match') not in (None, '', 'perfect'):
    raise SystemExit('Feature patch may not use a non-perfect match rule')
plugin = feature_xml.find(f"./plugin[@id='{bundle_id}']")
if plugin is None or plugin.attrib.get('version') != bundle_version:
    raise SystemExit('Patch feature does not include the exact patched bundle version')

content = repository_xml()
group_id = f'{feature_id}.feature.group'
matching_groups = [
    unit for unit in content.findall('./units/unit')
    if unit.attrib.get('id') == group_id and unit.attrib.get('version') == feature_version
]
if len(matching_groups) != 1:
    raise SystemExit(f'Expected one patch feature group IU, found {len(matching_groups)}')
group = matching_groups[0]
target_group_id = f'{target_feature_id}.feature.group'
if not has_exact_requirement(group, target_group_id, target_feature_version):
    raise SystemExit('Published patch IU does not require the exact patched feature version')
if not has_exact_requirement(group, bundle_id, bundle_version):
    raise SystemExit('Published patch IU does not require the exact patched bundle version')

payload = {
    'schemaVersion': 1,
    'result': 'PASS',
    'patchFeature': {'id': feature_id, 'version': feature_version, 'groupIU': group_id},
    'patchTarget': {
        'id': target_feature_id,
        'version': target_feature_version,
        'groupIU': target_group_id,
    },
    'patchedBundle': {'id': bundle_id, 'version': bundle_version},
}
(evidence / 'patch-feature-verification.json').write_text(
    json.dumps(payload, indent=2) + '\n', encoding='utf-8'
)
(evidence / 'patch-feature-verification.md').write_text(
    '\n'.join([
        '# Patched JDT UI feature-patch verification',
        '',
        '- Result: **PASS**',
        f'- Patch feature: `{group_id} {feature_version}`',
        f'- Patched feature: `{target_group_id} {target_feature_version}`',
        f'- Replacement bundle: `{bundle_id} {bundle_version}`',
        '- Exact feature-patch relationship: **PASS**',
    ]) + '\n',
    encoding='utf-8',
)
report_path = evidence / 'repository-verification.json'
report = json.loads(report_path.read_text(encoding='utf-8'))
report['feature']['kind'] = 'patch'
report['feature']['patchTarget'] = payload['patchTarget']
report_path.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
markdown_path = evidence / 'repository-verification.md'
with markdown_path.open('a', encoding='utf-8') as stream:
    stream.write(f'- Exact patch target: `{target_group_id} {target_feature_version}`: **PASS**\n')
print(json.dumps(payload, indent=2))
PY

export EVIDENCE_DIR FEATURE_ID FEATURE_VERSION PATCHED_JDT_UI_BUNDLE BUNDLE_VERSION BUNDLE_SHA256
export PATCHED_JDT_UI_TARGET_FEATURE PATCHED_JDT_UI_TARGET_FEATURE_VERSION
python3 <<'PY'
import json
import os
from pathlib import Path

path = Path(os.environ['EVIDENCE_DIR']) / 'repository-provenance.json'
payload = {
    'schemaVersion': 2,
    'featureId': os.environ['FEATURE_ID'],
    'featureVersion': os.environ['FEATURE_VERSION'],
    'featureGroupIU': f"{os.environ['FEATURE_ID']}.feature.group",
    'patchTargetFeatureId': os.environ['PATCHED_JDT_UI_TARGET_FEATURE'],
    'patchTargetFeatureVersion': os.environ['PATCHED_JDT_UI_TARGET_FEATURE_VERSION'],
    'patchTargetFeatureGroupIU': f"{os.environ['PATCHED_JDT_UI_TARGET_FEATURE']}.feature.group",
    'bundleSymbolicName': os.environ['PATCHED_JDT_UI_BUNDLE'],
    'bundleVersion': os.environ['BUNDLE_VERSION'],
    'bundleSha256': os.environ['BUNDLE_SHA256'],
}
path.write_text(json.dumps(payload, indent=2) + '\n', encoding='utf-8')
PY

printf 'Published patch %s %s for %s %s with %s %s to %s\n' \
  "$FEATURE_ID" "$FEATURE_VERSION" \
  "$PATCHED_JDT_UI_TARGET_FEATURE" "$PATCHED_JDT_UI_TARGET_FEATURE_VERSION" \
  "$PATCHED_JDT_UI_BUNDLE" "$BUNDLE_VERSION" "$REPOSITORY_DIR"
