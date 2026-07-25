#!/usr/bin/env bash
# Complete local distribution gate. Maven invokes this script from the
# sandbox_updatesite verify phase when -Ddistribution.smoke=true is set.
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
EVIDENCE_DIR=${1:-"$ROOT_DIR/target/distribution-verification"}
mkdir -p "$EVIDENCE_DIR"
cd "$ROOT_DIR"

python3 -m py_compile \
  .github/scripts/verify_target_repository_alignment.py \
  .github/scripts/verify_distribution_artifacts.py \
  .github/scripts/verify_published_repository.py \
  .github/scripts/test_verify_published_repository.py
python3 .github/scripts/verify_target_repository_alignment.py
python3 .github/scripts/test_verify_published_repository.py
bash -n .github/scripts/verify_linux_distribution.sh
bash -n .github/scripts/smoke_test_distribution.sh

bash .github/scripts/verify_linux_distribution.sh \
  2>&1 | tee "$EVIDENCE_DIR/validator.log"

repository_url=$(python3 - <<'PY'
from pathlib import Path
print(Path('sandbox_updatesite/target/repository').resolve().as_uri() + '/')
PY
)
python3 .github/scripts/verify_published_repository.py \
  --repository-url "$repository_url" \
  --expected-json "$EVIDENCE_DIR/verification.json" \
  --attempts 1 \
  --delay-seconds 0 \
  --output "$EVIDENCE_DIR/local-publication-verification.json"

bash .github/scripts/smoke_test_distribution.sh "$EVIDENCE_DIR"
