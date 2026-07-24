#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
EVIDENCE_DIR=${1:-"$ROOT_DIR/target/distribution-verification"}
VERIFY_JSON="$EVIDENCE_DIR/verification.json"
UPDATE_SITE="$ROOT_DIR/sandbox_updatesite/target/repository"
TARGET_FILE="$ROOT_DIR/sandbox_target/eclipse.target"
SMOKE_ROOT=${DISTRIBUTION_SMOKE_ROOT:-"${RUNNER_TEMP:-${TMPDIR:-/tmp}}/sandbox-distribution-smoke"}

for command in java javac python3 timeout xvfb-run; do
  command -v "$command" >/dev/null || { echo "Missing required command: $command" >&2; exit 1; }
done
[[ -f "$VERIFY_JSON" ]] || { echo "Missing distribution verification evidence: $VERIFY_JSON" >&2; exit 1; }

readarray -t values < <(python3 - "$VERIFY_JSON" "$UPDATE_SITE" "$TARGET_FILE" <<'PY'
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

with open(sys.argv[1], encoding='utf-8') as stream:
    report = json.load(stream)
print(report['product']['root'])
print(report['product']['launcher'])

target = ET.parse(sys.argv[3]).getroot()
p2_repositories = sorted({
    repository.attrib['location']
    for location in target.findall(".//location[@type='InstallableUnit']")
    for repository in location.findall('repository')
    if repository.attrib.get('location')
})
if not p2_repositories:
    raise SystemExit('The target definition contains no InstallableUnit repositories')
repositories = [Path(sys.argv[2]).resolve().as_uri(), *p2_repositories]
print(','.join(dict.fromkeys(repositories)))
roots = ['org.eclipse.sdk.ide', 'org.eclipse.equinox.p2.extras.feature.feature.group',
         *report['publishedFeatureIUs']]
print(','.join(dict.fromkeys(roots)))
PY
)
PRODUCT_ROOT=${values[0]}
PRODUCT_LAUNCHER=${values[1]}
REPOSITORIES=${values[2]}
INSTALL_IUS=${values[3]}

rm -rf "$SMOKE_ROOT"
mkdir -p "$SMOKE_ROOT/product-data" "$SMOKE_ROOT/fresh-data"

run_equinox() {
  local working_directory=$1
  local launcher=$2
  shift 2
  (
    cd "$working_directory"
    timeout 300s xvfb-run -a java -Declipse.p2.mirrors=false \
      -jar "$launcher" -nosplash -consoleLog "$@"
  )
}

# Prove that the product assembled by Tycho resolves and starts its p2 application.
run_equinox "$PRODUCT_ROOT" "$PRODUCT_LAUNCHER" \
  -application org.eclipse.equinox.p2.director \
  -listInstalledRoots \
  -data "$SMOKE_ROOT/product-data" \
  > "$EVIDENCE_DIR/materialized-product.log" 2>&1

grep -Eq 'org\.eclipse\.|sandbox_' "$EVIDENCE_DIR/materialized-product.log"

# Provision every published feature into a new destination. The built product is
# only the director host; the destination is resolved from the local update site
# plus the actual p2 InstallableUnit repositories in sandbox_target/eclipse.target.
# Mirror indirection is disabled so CI contacts those canonical repository URLs
# directly instead of failing on rate-limited mirror-list endpoints.
FRESH_INSTALL="$SMOKE_ROOT/fresh-install"
(
  cd "$PRODUCT_ROOT"
  timeout 900s xvfb-run -a java -Declipse.p2.mirrors=false \
    -jar "$PRODUCT_LAUNCHER" -nosplash -consoleLog \
    -application org.eclipse.equinox.p2.director \
    -repository "$REPOSITORIES" \
    -installIU "$INSTALL_IUS" \
    -destination "$FRESH_INSTALL" \
    -bundlepool "$FRESH_INSTALL" \
    -profile SandboxDistributionSmoke \
    -profileProperties org.eclipse.update.install.features=true \
    -p2.os linux -p2.ws gtk -p2.arch x86_64 \
    -roaming
) > "$EVIDENCE_DIR/fresh-install.log" 2>&1

mapfile -t fresh_launchers < <(find "$FRESH_INSTALL/plugins" -maxdepth 1 -type f \
  -name 'org.eclipse.equinox.launcher_*.jar' | sort)
if (( ${#fresh_launchers[@]} != 1 )); then
  printf 'Expected one launcher in fresh installation, found %d\n' "${#fresh_launchers[@]}" >&2
  exit 1
fi
FRESH_LAUNCHER=${fresh_launchers[0]}

run_equinox "$FRESH_INSTALL" "$FRESH_LAUNCHER" \
  -application org.eclipse.equinox.p2.director \
  -listInstalledRoots \
  -data "$SMOKE_ROOT/fresh-data" \
  > "$EVIDENCE_DIR/fresh-product.log" 2>&1

python3 - "$VERIFY_JSON" "$EVIDENCE_DIR/fresh-product.log" <<'PY'
import json
import sys
from pathlib import Path

with open(sys.argv[1], encoding='utf-8') as stream:
    expected = json.load(stream)['publishedFeatureIds']
text = Path(sys.argv[2]).read_text(encoding='utf-8', errors='replace')
missing = [feature for feature in expected if feature not in text]
if missing:
    raise SystemExit(f'Fresh installation is missing published roots: {missing}')
PY

# Create and import a real Java project, run a deterministic cleanup through the
# freshly provisioned application, and prove both the report and source change.
CLEANUP_WORKSPACE="$SMOKE_ROOT/cleanup-workspace"
CLEANUP_PROJECT="$CLEANUP_WORKSPACE/SmokeProject"
CLEANUP_SOURCE="$CLEANUP_PROJECT/src/smoke/Smoke.java"
CLEANUP_REPORT="$EVIDENCE_DIR/cleanup-report.json"
mkdir -p "$CLEANUP_PROJECT/src/smoke" "$CLEANUP_PROJECT/bin"
cat > "$CLEANUP_PROJECT/.project" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<projectDescription>
  <name>SmokeProject</name>
  <comment></comment>
  <projects></projects>
  <buildSpec>
    <buildCommand>
      <name>org.eclipse.jdt.core.javabuilder</name>
      <arguments></arguments>
    </buildCommand>
  </buildSpec>
  <natures>
    <nature>org.eclipse.jdt.core.javanature</nature>
  </natures>
</projectDescription>
EOF
cat > "$CLEANUP_PROJECT/.classpath" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<classpath>
  <classpathentry kind="src" path="src"/>
  <classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER"/>
  <classpathentry kind="output" path="bin"/>
</classpath>
EOF
cat > "$CLEANUP_SOURCE" <<'EOF'
package smoke;

import java.util.List;

public class Smoke {
    public String value() {
        return "smoke";
    }
}
EOF

run_equinox "$FRESH_INSTALL" "$FRESH_LAUNCHER" \
  -application org.sandbox.jdt.core.JavaCleanup \
  -data "$CLEANUP_WORKSPACE" \
  --import-project "$CLEANUP_PROJECT" \
  --mode apply \
  --report "$CLEANUP_REPORT" \
  --config "$ROOT_DIR/.github/cleanup-profiles/minimal.properties" \
  "$CLEANUP_SOURCE" \
  > "$EVIDENCE_DIR/cleanup-application.log" 2>&1

python3 - "$CLEANUP_REPORT" "$CLEANUP_SOURCE" <<'PY'
import json
import sys
from pathlib import Path

report_path = Path(sys.argv[1])
source_path = Path(sys.argv[2])
with report_path.open(encoding='utf-8') as stream:
    report = json.load(stream)
if report.get('filesProcessed') != 1:
    raise SystemExit(f"Expected one processed file, got {report.get('filesProcessed')!r}")
if report.get('filesChanged') != 1:
    raise SystemExit(f"Expected one changed file, got {report.get('filesChanged')!r}")
if str(source_path) not in report.get('changedFiles', []):
    raise SystemExit('Cleanup report does not name the transformed source file')
source = source_path.read_text(encoding='utf-8')
if 'import java.util.List;' in source:
    raise SystemExit('Minimal cleanup did not remove the unused import')
PY

mkdir -p "$SMOKE_ROOT/compiled"
javac -d "$SMOKE_ROOT/compiled" "$CLEANUP_SOURCE"

cat >> "$EVIDENCE_DIR/verification.md" <<'EOF'

## Runtime smoke tests

- Materialized product started and listed installed roots: **PASS**
- Every published Sandbox feature provisioned into a fresh p2 destination: **PASS**
- Fresh installation started and reported all published roots: **PASS**
- Installed cleanup application imported a Java project and transformed one source file: **PASS**
- Transformed source still compiled with Java 21: **PASS**
EOF
