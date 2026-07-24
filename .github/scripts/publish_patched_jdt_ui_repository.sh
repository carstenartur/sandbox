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

for command in find java mvn python3 sed sha256sum; do
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
      Exact-version carrier feature for the reviewed Sandbox JDT UI scope-expansion patch.
   </description>
   <copyright>
      Copyright (c) Eclipse contributors and Sandbox contributors.
   </copyright>
   <license url="https://www.eclipse.org/legal/epl-2.0/">
      Eclipse Public License 2.0
   </license>
   <requires>
      <import plugin="$PATCHED_JDT_UI_BUNDLE" version="$BUNDLE_VERSION" match="perfect"/>
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

export EVIDENCE_DIR FEATURE_ID FEATURE_VERSION PATCHED_JDT_UI_BUNDLE BUNDLE_VERSION BUNDLE_SHA256
python3 <<'PY'
import json
import os
from pathlib import Path

path = Path(os.environ['EVIDENCE_DIR']) / 'repository-provenance.json'
payload = {
    'schemaVersion': 1,
    'featureId': os.environ['FEATURE_ID'],
    'featureVersion': os.environ['FEATURE_VERSION'],
    'featureGroupIU': f"{os.environ['FEATURE_ID']}.feature.group",
    'bundleSymbolicName': os.environ['PATCHED_JDT_UI_BUNDLE'],
    'bundleVersion': os.environ['BUNDLE_VERSION'],
    'bundleSha256': os.environ['BUNDLE_SHA256'],
}
path.write_text(json.dumps(payload, indent=2) + '\n', encoding='utf-8')
PY

printf 'Published %s %s requiring %s %s to %s\n' \
  "$FEATURE_ID" "$FEATURE_VERSION" "$PATCHED_JDT_UI_BUNDLE" "$BUNDLE_VERSION" "$REPOSITORY_DIR"
