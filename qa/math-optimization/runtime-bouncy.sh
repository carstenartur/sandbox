#!/usr/bin/env bash
# Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0
set -euo pipefail
if [ "$#" -ne 2 ]; then
  echo "Usage: bash qa/math-optimization/runtime-bouncy.sh PINNED_BC_CHECKOUT FRESH_OUTPUT_DIRECTORY" >&2
  exit 2
fi
sandbox="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
bc="$(realpath "$1")"
revision=c314b9cdffa3958a0eff5344f8fdcdb0181ed830
test "$(git -C "$bc" rev-parse HEAD)" = "$revision"
test -z "$(git -C "$bc" status --porcelain)"
if [ -e "$2" ]; then echo 'The qualification output must be a fresh directory.' >&2; exit 2; fi
mkdir -p "$2"
output="$(realpath "$2")"
case "$output/" in "$bc/"*) echo 'Output must not modify the original Bouncy Castle checkout.' >&2; exit 2;; esac
classes="$output/upstream-classes"
mkdir -p "$classes" "$output/results"
inputs=()
for name in MD4Digest RIPEMD160Digest SM3Digest SHA256Digest; do
  inputs+=("$bc/core/src/main/java/org/bouncycastle/crypto/digests/$name.java")
  inputs+=("$bc/core/src/test/java/org/bouncycastle/crypto/test/${name}Test.java")
done
# Compile the unchanged production classes AND unchanged upstream tests. Every
# automatically resolved dependency comes from this same pinned source tree.
javac --release 8 -encoding UTF-8 -d "$classes" \
  -sourcepath "$bc/core/src/main/java:$bc/core/src/test/java" "${inputs[@]}" \
  > "$output/upstream-compilation.log" 2>&1 || { cat "$output/upstream-compilation.log"; exit 1; }
license_found=false
for license in LICENSE.html LICENSE.md LICENSE; do
  if [ -f "$bc/$license" ]; then cp "$bc/$license" "$output/$license"; license_found=true; fi
done
if [ "$license_found" != true ]; then echo 'Pinned upstream license missing.' >&2; exit 1; fi
{
  printf 'sandbox.revision=%s\n' "$(git -C "$sandbox" rev-parse HEAD)"
  printf 'bouncycastle.revision=%s\n' "$revision"
  printf 'jdk=%s\n' "$(java --version | head -1)"
  printf 'sdk.sha256=%s\n' "$(sha256sum "$sandbox/sandbox_math_cleanup/lib/regelsuche-optimization-sdk.jar" | cut -d' ' -f1)"
} > "$output/run.properties"
cp "$sandbox/sandbox_math_cleanup/lib/qualification.json" "$output/sdk-qualification.json"
export MATH_BC_SOURCE="$bc" MATH_BC_CLASSES="$classes" MATH_BC_OUTPUT="$output/results"
cd "$sandbox"
xvfb-run --auto-servernum ./mvnw --batch-mode --no-transfer-progress \
  -pl sandbox_target,sandbox_math_cleanup,sandbox_math_cleanup_test -am \
  -Dtest=BouncyCastleRuntimeCorpusIT verify
cp -a sandbox_math_cleanup_test/target/surefire-reports "$output/production-junit"
# This second JUnit invocation consumes only PASS receipts and matching source
# hashes from the preceding full-production-file test; it cannot invent outputs.
xvfb-run --auto-servernum ./mvnw --batch-mode --no-transfer-progress \
  -pl sandbox_target,sandbox_math_cleanup,sandbox_math_cleanup_test -am \
  -Dtest=RuntimeHelpFixtureExportIT verify
cp -a sandbox_math_cleanup_test/target/surefire-reports "$output/fixture-export-junit"
