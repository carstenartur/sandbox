#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
EVIDENCE_DIR=${1:-"$ROOT_DIR/target/distribution-verification"}
VERIFY_JSON="$EVIDENCE_DIR/verification.json"
UPDATE_SITE="$ROOT_DIR/sandbox_updatesite/target/repository"
TARGET_FILE="$ROOT_DIR/sandbox_target/eclipse.target"
SMOKE_ROOT=${DISTRIBUTION_SMOKE_ROOT:-"${RUNNER_TEMP:-${TMPDIR:-/tmp}}/sandbox-distribution-smoke"}

for command in java python3 timeout xvfb-run; do
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
mkdir -p "$SMOKE_ROOT/product-data" "$SMOKE_ROOT/fresh-data" "$SMOKE_ROOT/cleanup-workspace"

run_equinox() {
  local working_directory=$1
  local launcher=$2
  shift 2
  (
    cd "$working_directory"
    timeout 300s xvfb-run -a java -jar "$launcher" -nosplash -consoleLog "$@"
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
FRESH_INSTALL="$SMOKE_ROOT/fresh-install"
(
  cd "$PRODUCT_ROOT"
  timeout 900s xvfb-run -a java -jar "$PRODUCT_LAUNCHER" -nosplash -consoleLog \
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

# Activate the installed Sandbox cleanup application from the fresh destination.
# Help mode is side-effect free but still proves application-extension lookup,
# bundle resolution and Java/UI service startup in the installed distribution.
run_equinox "$FRESH_INSTALL" "$FRESH_LAUNCHER" \
  -application sandbox_cleanup_application.org.sandbox.jdt.core.JavaCleanup \
  -data "$SMOKE_ROOT/cleanup-workspace" \
  --help \
  > "$EVIDENCE_DIR/cleanup-application.log" 2>&1

grep -Eiq 'usage|cleanup|config' "$EVIDENCE_DIR/cleanup-application.log"

cat >> "$EVIDENCE_DIR/verification.md" <<'EOF'

## Runtime smoke tests

- Materialized product started and listed installed roots: **PASS**
- Every published Sandbox feature provisioned into a fresh p2 destination: **PASS**
- Fresh installation started and reported all published roots: **PASS**
- Installed Sandbox cleanup application activated in side-effect-free help mode: **PASS**
EOF
