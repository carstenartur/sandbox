#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
EVIDENCE_DIR=${1:-"$ROOT_DIR/target/distribution-verification"}
PRODUCTS_DIR="$ROOT_DIR/sandbox_product/target/products"
VALIDATOR="$ROOT_DIR/.github/scripts/verify_distribution_artifacts.py"

mkdir -p "$EVIDENCE_DIR"

mapfile -t linux_product_directories < <(
  find "$PRODUCTS_DIR" -type d -path '*/linux/gtk/x86_64' -print | sort
)

if (( ${#linux_product_directories[@]} != 1 )); then
  printf 'Expected exactly one Linux GTK x86_64 product directory, found %d: %s\n' \
    "${#linux_product_directories[@]}" "${linux_product_directories[*]:-<none>}" \
    | tee "$EVIDENCE_DIR/validator.log" >&2
  exit 1
fi

python3 "$VALIDATOR" \
  --products "${linux_product_directories[0]}" \
  --output "$EVIDENCE_DIR" \
  2>&1 | tee "$EVIDENCE_DIR/validator.log"
