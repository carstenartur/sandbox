#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
P2_ROOT=${1:?usage: run_patched_jdt_ui_scope_probe.sh P2_OUTPUT INSTALLATION_EVIDENCE [PROBE_EVIDENCE]}
INSTALLATION_EVIDENCE=${2:?usage: run_patched_jdt_ui_scope_probe.sh P2_OUTPUT INSTALLATION_EVIDENCE [PROBE_EVIDENCE]}
EVIDENCE_DIR=${3:-"$ROOT_DIR/target/patched-jdt-ui-runtime-probe"}
INSTALLATION_JSON="$INSTALLATION_EVIDENCE/installation-verification.json"

for command in awk find grep jar java javac mvn python3 sed sha256sum sort tail tee timeout xvfb-run; do
  command -v "$command" >/dev/null || { echo "Missing required command: $command" >&2; exit 1; }
done

python3 "$ROOT_DIR/.github/scripts/validate_patched_jdt_ui_probe_inputs.py" \
  --p2-root "$P2_ROOT" \
  --installation "$INSTALLATION_JSON" \
  > "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/patched-jdt-ui-probe-input-validation.json"

exec bash "$ROOT_DIR/.github/scripts/run_patched_jdt_ui_scope_probe_impl.sh" \
  "$P2_ROOT" "$INSTALLATION_EVIDENCE" "$EVIDENCE_DIR"
