#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
P2_ROOT=${1:?usage: smoke_test_patched_jdt_ui_repository.sh P2_OUTPUT [EVIDENCE_DIR]}
EVIDENCE_DIR=${2:-"$ROOT_DIR/target/patched-jdt-ui-installation"}
REPOSITORY_DIR="$P2_ROOT/repository"
REPOSITORY_EVIDENCE="$P2_ROOT/evidence"
VERIFICATION_JSON="$REPOSITORY_EVIDENCE/repository-verification.json"
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
print(report['bundle']['id'])
print(report['bundle']['version'])
print(report['bundle']['sha256'])
print(report['feature']['groupIU'])
print(report['feature']['version'])
PY
)
BUNDLE_ID=${expected[0]}
BUNDLE_VERSION=${expected[1]}
BUNDLE_SHA256=${expected[2]}
FEATURE_GROUP=${expected[3]}
FEATURE_VERSION=${expected[4]}

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
  || { echo "Launcher parent is not a materialized product root: $PRODUCT_ROOT" >&2; exit 1; }
BUNDLES_INFO="$PRODUCT_ROOT/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info"
[[ -f "$BUNDLES_INFO" ]] || { echo "Missing bundles.info: $BUNDLES_INFO" >&2; exit 1; }

mapfile -t profiles < <(find "$PRODUCT_ROOT/p2/org.eclipse.equinox.p2.engine/profileRegistry" \
  -maxdepth 1 -mindepth 1 -type d -name '*.profile' | sort)
if (( ${#profiles[@]} != 1 )); then
  printf 'Expected exactly one materialized product profile, found %d: %s\n' \
    "${#profiles[@]}" "${profiles[*]:-<none>}" >&2
  exit 1
fi
PROFILE_ID=$(basename "${profiles[0]}" .profile)

selected_bundle_line() {
  awk -F, -v id="$BUNDLE_ID" '$1 == id { print }' "$BUNDLES_INFO"
}

mapfile -t stock_lines < <(selected_bundle_line)
if (( ${#stock_lines[@]} != 1 )); then
  printf 'Expected one selected stock %s bundle, found %d\n' "$BUNDLE_ID" "${#stock_lines[@]}" >&2
  exit 1
fi
STOCK_VERSION=$(awk -F, '{print $2}' <<<"${stock_lines[0]}")
if [[ "$STOCK_VERSION" == "$BUNDLE_VERSION" ]]; then
  echo 'Stock product already selects the patched version; stock regression cannot be distinguished' >&2
  exit 1
fi

mkdir -p "$EVIDENCE_DIR"
printf '%s\n' "${stock_lines[0]}" > "$EVIDENCE_DIR/stock-bundle-selection.txt"
REPOSITORY_URI=$(python3 - "$REPOSITORY_DIR" <<'PY'
import sys
from pathlib import Path
print(Path(sys.argv[1]).resolve().as_uri())
PY
)

(
  cd "$PRODUCT_ROOT"
  timeout 900s xvfb-run -a java -jar "$LAUNCHER" -nosplash -consoleLog \
    -application org.eclipse.equinox.p2.director \
    -repository "$REPOSITORY_URI" \
    -installIU "$FEATURE_GROUP" \
    -destination "$PRODUCT_ROOT" \
    -bundlepool "$PRODUCT_ROOT" \
    -profile "$PROFILE_ID" \
    -profileProperties org.eclipse.update.install.features=true \
    -p2.os linux -p2.ws gtk -p2.arch x86_64
) > "$EVIDENCE_DIR/install.log" 2>&1

mapfile -t patched_lines < <(selected_bundle_line)
if (( ${#patched_lines[@]} != 1 )); then
  printf 'Expected one selected patched %s bundle after installation, found %d\n' \
    "$BUNDLE_ID" "${#patched_lines[@]}" >&2
  exit 1
fi
SELECTED_VERSION=$(awk -F, '{print $2}' <<<"${patched_lines[0]}")
if [[ "$SELECTED_VERSION" != "$BUNDLE_VERSION" ]]; then
  echo "Installed product selected $BUNDLE_ID $SELECTED_VERSION instead of $BUNDLE_VERSION" >&2
  exit 1
fi
printf '%s\n' "${patched_lines[0]}" > "$EVIDENCE_DIR/patched-bundle-selection.txt"

mapfile -t selected_jars < <(find "$PRODUCT_ROOT/plugins" -maxdepth 1 -type f \
  -name "${BUNDLE_ID}_${BUNDLE_VERSION}.jar" | sort)
if (( ${#selected_jars[@]} != 1 )); then
  printf 'Expected exactly one installed patched bundle file, found %d\n' "${#selected_jars[@]}" >&2
  exit 1
fi
INSTALLED_SHA=$(sha256sum "${selected_jars[0]}" | awk '{print $1}')
if [[ "$INSTALLED_SHA" != "$BUNDLE_SHA256" ]]; then
  echo 'Installed bundle SHA-256 differs from repository provenance' >&2
  exit 1
fi

(
  cd "$PRODUCT_ROOT"
  timeout 300s xvfb-run -a java -jar "$LAUNCHER" -nosplash -consoleLog \
    -application org.eclipse.equinox.p2.director \
    -listInstalledRoots \
    -destination "$PRODUCT_ROOT" \
    -profile "$PROFILE_ID"
) > "$EVIDENCE_DIR/installed-roots.log" 2>&1
if ! grep -Fq "$FEATURE_GROUP" "$EVIDENCE_DIR/installed-roots.log"; then
  echo "Installed product did not report root $FEATURE_GROUP" >&2
  exit 1
fi

export EVIDENCE_DIR PRODUCT_ROOT PROFILE_ID BUNDLE_ID BUNDLE_VERSION BUNDLE_SHA256
export STOCK_VERSION FEATURE_GROUP FEATURE_VERSION INSTALLED_SHA
python3 <<'PY'
import json
import os
from pathlib import Path

payload = {
    'schemaVersion': 1,
    'result': 'PASS',
    'productRoot': os.environ['PRODUCT_ROOT'],
    'profileId': os.environ['PROFILE_ID'],
    'stockBundle': {'id': os.environ['BUNDLE_ID'], 'version': os.environ['STOCK_VERSION']},
    'patchedBundle': {
        'id': os.environ['BUNDLE_ID'],
        'version': os.environ['BUNDLE_VERSION'],
        'sha256': os.environ['BUNDLE_SHA256'],
        'installedSha256': os.environ['INSTALLED_SHA'],
    },
    'feature': {'groupIU': os.environ['FEATURE_GROUP'], 'version': os.environ['FEATURE_VERSION']},
}
root = Path(os.environ['EVIDENCE_DIR'])
(root / 'installation-verification.json').write_text(
    json.dumps(payload, indent=2) + '\n', encoding='utf-8'
)
lines = [
    '# Patched JDT UI installation verification',
    '',
    '- Result: **PASS**',
    f"- Stock selection: `{os.environ['BUNDLE_ID']} {os.environ['STOCK_VERSION']}`",
    f"- Patched selection: `{os.environ['BUNDLE_ID']} {os.environ['BUNDLE_VERSION']}`",
    f"- Installed root: `{os.environ['FEATURE_GROUP']} {os.environ['FEATURE_VERSION']}`",
    '- Exactly one active simpleconfigurator entry: **PASS**',
    '- Installed bundle bytes match pinned SHA-256: **PASS**',
    '- Patched product starts the p2 director and lists installed roots: **PASS**',
]
(root / 'installation-verification.md').write_text(
    '\n'.join(lines) + '\n', encoding='utf-8'
)
print(json.dumps(payload, indent=2))
PY
