#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
P2_ROOT=${1:?usage: install_patched_jdt_feature.sh P2_OUTPUT [EVIDENCE_DIR]}
EVIDENCE_DIR=${2:-"$ROOT_DIR/target/patched-jdt-feature-installation"}
REPOSITORY_DIR="$P2_ROOT/repository"
VERIFICATION_JSON="$P2_ROOT/evidence/repository-verification.json"
PRODUCTS_DIR="$ROOT_DIR/sandbox_product/target/products"

for command in awk basename dirname find java python3 sha256sum timeout xvfb-run; do
  command -v "$command" >/dev/null || { echo "Missing required command: $command" >&2; exit 1; }
done
[[ -f "$VERIFICATION_JSON" ]] || { echo "Missing repository verification: $VERIFICATION_JSON" >&2; exit 1; }

readarray -t expected < <(python3 - "$VERIFICATION_JSON" <<'PY'
import json
import sys
from pathlib import Path

report = json.loads(Path(sys.argv[1]).read_text(encoding='utf-8'))
if report.get('result') != 'PASS':
    raise SystemExit('Repository verification is not PASS')
print(report['stockFeature']['id'])
print(report['stockFeature']['version'])
print(report['replacementFeature']['version'])
print(report['replacementFeature']['groupIU'])
print(report['patchedBundle']['id'])
print(report['patchedBundle']['version'])
print(report['patchedBundle']['sha256'])
print(report['stockBundle']['version'])
PY
)
FEATURE_ID=${expected[0]}
STOCK_FEATURE_VERSION=${expected[1]}
PATCHED_FEATURE_VERSION=${expected[2]}
FEATURE_GROUP=${expected[3]}
BUNDLE_ID=${expected[4]}
BUNDLE_VERSION=${expected[5]}
BUNDLE_SHA256=${expected[6]}
STOCK_BUNDLE_VERSION=${expected[7]}

mapfile -t architecture_roots < <(find "$PRODUCTS_DIR" -type d -path '*/linux/gtk/x86_64' -print | sort)
if (( ${#architecture_roots[@]} != 1 )); then
  printf 'Expected exactly one Linux GTK x86_64 product directory, found %d: %s\n' \
    "${#architecture_roots[@]}" "${architecture_roots[*]:-<none>}" >&2
  exit 1
fi
ARCHITECTURE_ROOT=${architecture_roots[0]}
mapfile -t launchers < <(find "$ARCHITECTURE_ROOT" -type f \
  -path '*/plugins/org.eclipse.equinox.launcher_*.jar' | sort)
if (( ${#launchers[@]} != 1 )); then
  printf 'Expected exactly one Equinox launcher below %s, found %d: %s\n' \
    "$ARCHITECTURE_ROOT" "${#launchers[@]}" "${launchers[*]:-<none>}" >&2
  exit 1
fi
LAUNCHER=${launchers[0]}
PRODUCT_ROOT=$(dirname "$(dirname "$LAUNCHER")")
[[ -f "$PRODUCT_ROOT/configuration/config.ini" ]] \
  || { echo "Launcher parent is not a product root: $PRODUCT_ROOT" >&2; exit 1; }
BUNDLES_INFO="$PRODUCT_ROOT/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info"
[[ -f "$BUNDLES_INFO" ]] || { echo "Missing bundles.info: $BUNDLES_INFO" >&2; exit 1; }
mapfile -t profiles < <(find "$PRODUCT_ROOT/p2/org.eclipse.equinox.p2.engine/profileRegistry" \
  -maxdepth 1 -mindepth 1 -type d -name '*.profile' | sort)
if (( ${#profiles[@]} != 1 )); then
  printf 'Expected one product profile, found %d: %s\n' \
    "${#profiles[@]}" "${profiles[*]:-<none>}" >&2
  exit 1
fi
PROFILE_ID=$(basename "${profiles[0]}" .profile)

selected_bundle_lines() {
  awk -F, -v id="$BUNDLE_ID" '$1 == id { print }' "$BUNDLES_INFO"
}
mapfile -t stock_lines < <(selected_bundle_lines)
if (( ${#stock_lines[@]} != 1 )); then
  printf 'Expected one stock %s bundle selection, found %d\n' "$BUNDLE_ID" "${#stock_lines[@]}" >&2
  exit 1
fi
ACTUAL_STOCK_VERSION=$(awk -F, '{print $2}' <<<"${stock_lines[0]}")
[[ "$ACTUAL_STOCK_VERSION" == "$STOCK_BUNDLE_VERSION" ]] \
  || { echo "Stock product selected $ACTUAL_STOCK_VERSION, evidence expected $STOCK_BUNDLE_VERSION" >&2; exit 1; }

mkdir -p "$EVIDENCE_DIR"
printf '%s\n' "${stock_lines[0]}" > "$EVIDENCE_DIR/stock-bundle-selection.txt"
REPOSITORY_URI=$(python3 - "$REPOSITORY_DIR" <<'PY'
import sys
from pathlib import Path
print(Path(sys.argv[1]).resolve().as_uri())
PY
)

# The replacement repository publishes a newer IU with the same JDT feature ID.
# Explicitly replacing the old root removes its exact requirement on the stock
# org.eclipse.jdt.ui qualifier before the new exact requirement is installed.
(
  cd "$PRODUCT_ROOT"
  timeout 1200s xvfb-run -a java -jar "$LAUNCHER" -nosplash -consoleLog \
    -application org.eclipse.equinox.p2.director \
    -repository "$REPOSITORY_URI" \
    -uninstallIU "${FEATURE_GROUP}/${STOCK_FEATURE_VERSION}" \
    -installIU "${FEATURE_GROUP}/${PATCHED_FEATURE_VERSION}" \
    -destination "$PRODUCT_ROOT" \
    -bundlepool "$PRODUCT_ROOT" \
    -profile "$PROFILE_ID" \
    -profileProperties org.eclipse.update.install.features=true \
    -p2.os linux -p2.ws gtk -p2.arch x86_64
) > "$EVIDENCE_DIR/install.log" 2>&1

mapfile -t patched_lines < <(selected_bundle_lines)
if (( ${#patched_lines[@]} != 1 )); then
  printf 'Expected one active patched %s bundle, found %d\n' "$BUNDLE_ID" "${#patched_lines[@]}" >&2
  exit 1
fi
SELECTED_VERSION=$(awk -F, '{print $2}' <<<"${patched_lines[0]}")
[[ "$SELECTED_VERSION" == "$BUNDLE_VERSION" ]] \
  || { echo "Patched product selected $BUNDLE_ID $SELECTED_VERSION instead of $BUNDLE_VERSION" >&2; exit 1; }
printf '%s\n' "${patched_lines[0]}" > "$EVIDENCE_DIR/patched-bundle-selection.txt"

mapfile -t selected_jars < <(find "$PRODUCT_ROOT/plugins" -maxdepth 1 -type f \
  -name "${BUNDLE_ID}_${BUNDLE_VERSION}.jar" | sort)
if (( ${#selected_jars[@]} != 1 )); then
  printf 'Expected one installed patched bundle file, found %d\n' "${#selected_jars[@]}" >&2
  exit 1
fi
INSTALLED_SHA=$(sha256sum "${selected_jars[0]}" | awk '{print $1}')
[[ "$INSTALLED_SHA" == "$BUNDLE_SHA256" ]] \
  || { echo 'Installed bundle SHA-256 differs from repository provenance' >&2; exit 1; }

(
  cd "$PRODUCT_ROOT"
  timeout 300s xvfb-run -a java -jar "$LAUNCHER" -nosplash -consoleLog \
    -application org.eclipse.equinox.p2.director \
    -listInstalledRoots \
    -destination "$PRODUCT_ROOT" \
    -profile "$PROFILE_ID"
) > "$EVIDENCE_DIR/installed-roots.log" 2>&1

grep -Fq "${FEATURE_GROUP}/${PATCHED_FEATURE_VERSION}" "$EVIDENCE_DIR/installed-roots.log" \
  || grep -Eq "${FEATURE_GROUP}[[:space:]]+${PATCHED_FEATURE_VERSION}" "$EVIDENCE_DIR/installed-roots.log" \
  || { echo "Installed roots do not contain replacement JDT feature $PATCHED_FEATURE_VERSION" >&2; exit 1; }
if grep -Fq "$STOCK_FEATURE_VERSION" "$EVIDENCE_DIR/installed-roots.log"; then
  echo "Installed roots still contain stock JDT feature $STOCK_FEATURE_VERSION" >&2
  exit 1
fi

# Start a real workbench instance long enough to prove framework resolution. A
# timeout is expected because the IDE event loop remains active.
set +e
(
  cd "$PRODUCT_ROOT"
  timeout 45s xvfb-run -a java -jar "$LAUNCHER" -nosplash -consoleLog \
    -application org.eclipse.ui.ide.workbench \
    -data "$EVIDENCE_DIR/workspace"
) > "$EVIDENCE_DIR/workbench.log" 2>&1
WORKBENCH_STATUS=$?
set -e
if [[ "$WORKBENCH_STATUS" -ne 0 && "$WORKBENCH_STATUS" -ne 124 ]]; then
  echo "Patched workbench failed with exit status $WORKBENCH_STATUS" >&2
  tail -n 120 "$EVIDENCE_DIR/workbench.log" >&2
  exit 1
fi
if grep -Eq '(^| )!ENTRY .* [24] ' "$EVIDENCE_DIR/workbench.log"; then
  echo 'Patched workbench logged an Eclipse error entry' >&2
  tail -n 120 "$EVIDENCE_DIR/workbench.log" >&2
  exit 1
fi

export EVIDENCE_DIR PRODUCT_ROOT PROFILE_ID FEATURE_ID STOCK_FEATURE_VERSION PATCHED_FEATURE_VERSION
export FEATURE_GROUP BUNDLE_ID STOCK_BUNDLE_VERSION BUNDLE_VERSION BUNDLE_SHA256 INSTALLED_SHA
python3 <<'PY'
import json
import os
from pathlib import Path

payload = {
    'schemaVersion': 1,
    'result': 'PASS',
    'productRoot': os.environ['PRODUCT_ROOT'],
    'profileId': os.environ['PROFILE_ID'],
    'stockFeature': {'id': os.environ['FEATURE_ID'], 'version': os.environ['STOCK_FEATURE_VERSION']},
    'replacementFeature': {
        'id': os.environ['FEATURE_ID'],
        'version': os.environ['PATCHED_FEATURE_VERSION'],
        'groupIU': os.environ['FEATURE_GROUP'],
    },
    'stockBundle': {'id': os.environ['BUNDLE_ID'], 'version': os.environ['STOCK_BUNDLE_VERSION']},
    'patchedBundle': {
        'id': os.environ['BUNDLE_ID'],
        'version': os.environ['BUNDLE_VERSION'],
        'sha256': os.environ['BUNDLE_SHA256'],
        'installedSha256': os.environ['INSTALLED_SHA'],
    },
    'workbenchStart': 'PASS',
}
root = Path(os.environ['EVIDENCE_DIR'])
(root / 'installation-verification.json').write_text(json.dumps(payload, indent=2) + '\n', encoding='utf-8')
(root / 'installation-verification.md').write_text(
    '# Patched JDT feature installation\n\n'
    '- Result: **PASS**\n'
    f'- Replaced feature: `{os.environ["FEATURE_ID"]} {os.environ["STOCK_FEATURE_VERSION"]}` '
    f'→ `{os.environ["PATCHED_FEATURE_VERSION"]}`\n'
    f'- Replaced bundle: `{os.environ["BUNDLE_ID"]} {os.environ["STOCK_BUNDLE_VERSION"]}` '
    f'→ `{os.environ["BUNDLE_VERSION"]}`\n'
    '- Exactly one active singleton bundle entry: **PASS**\n'
    '- Installed bytes match pinned SHA-256: **PASS**\n'
    '- IDE workbench framework resolution/startup: **PASS**\n',
    encoding='utf-8',
)
print(json.dumps(payload, indent=2))
PY
