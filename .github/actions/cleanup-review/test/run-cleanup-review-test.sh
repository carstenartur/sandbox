#!/usr/bin/env bash
# Copyright (c) 2026 Carsten Hammer.
# SPDX-License-Identifier: EPL-2.0

set -euo pipefail

script_dir=$(cd "$(dirname "$0")" && pwd)
action_dir=$(cd "$script_dir/.." && pwd)
runner=$action_dir/run-cleanup-review.sh

test_root=$(mktemp -d)
trap 'rm -rf "$test_root"' EXIT
repo=$test_root/repository
output=$test_root/output
bin_dir=$test_root/bin
mkdir -p "$repo/alpha/src" "$repo/orphan" "$bin_dir"

cat > "$repo/alpha/.project" <<'PROJECT'
<?xml version="1.0" encoding="UTF-8"?>
<projectDescription><name>alpha</name></projectDescription>
PROJECT
cat > "$repo/alpha/src/A.java" <<'JAVA'
class A {
    String value() { return "before"; }
}
JAVA
cat > "$repo/alpha/src/With Space.java" <<'JAVA'
class WithSpace {
    String value() { return "before"; }
}
JAVA
cat > "$repo/orphan/B.java" <<'JAVA'
class B {}
JAVA
cat > "$repo/cleanup.properties" <<'PROPERTIES'
cleanup.explicit_encoding=true
cleanup.explicit_encoding_keep_behavior=true
PROPERTIES

(
  cd "$repo"
  git init -q
  git config user.name test
  git config user.email test@example.invalid
  git add .
  git commit -qm baseline
)
base_sha=$(git -C "$repo" rev-parse HEAD)
printf '\n// pull request change\n' >> "$repo/alpha/src/A.java"
printf '\n// pull request change\n' >> "$repo/alpha/src/With Space.java"
printf '\n// pull request change\n' >> "$repo/orphan/B.java"
(
  cd "$repo"
  git add .
  git commit -qm head
)
head_sha=$(git -C "$repo" rev-parse HEAD)

cat > "$bin_dir/docker" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
: "${MOCK_DOCKER_LOG:?}"
printf '%q ' "$@" >> "$MOCK_DOCKER_LOG"
printf '\n' >> "$MOCK_DOCKER_LOG"

if [[ ${1:-} == pull ]]; then
  exit 0
fi
if [[ ${1:-} == image && ${2:-} == inspect ]]; then
  echo 'ghcr.io/carstenartur/sandbox-cleanup@sha256:test'
  exit 0
fi
[[ ${1:-} == run ]] || exit 0

workspace=
review_output=
for ((index=1; index <= $#; index++)); do
  value=${!index}
  if [[ $value == --volume ]]; then
    ((index += 1))
    mount=${!index}
    case $mount in
      *:/workspace) workspace=${mount%:/workspace} ;;
      *:/review-output) review_output=${mount%:/review-output} ;;
    esac
  fi
done
[[ -n $workspace && -n $review_output ]]

sources=()
report=
import_project=
for ((index=1; index <= $#; index++)); do
  value=${!index}
  case $value in
    --source)
      ((index += 1))
      sources+=("${!index}")
      ;;
    --report)
      ((index += 1))
      report=${!index}
      ;;
    --import-project)
      ((index += 1))
      import_project=${!index}
      ;;
  esac
done
[[ $import_project == /workspace/alpha ]]
for source in "${sources[@]}"; do
  relative=${source#/workspace/}
  printf '\n// cleanup suggestion\n' >> "$workspace/$relative"
done
report_path=$review_output/${report#/review-output/}
printf '{"tool":"sandbox-cleanup","filesChanged":%d}\n' "${#sources[@]}" > "$report_path"
MOCK
chmod +x "$bin_dir/docker"

output_file=$test_root/github-output
log_file=$test_root/docker.log
: > "$output_file"
: > "$log_file"
(
  cd "$repo"
  GITHUB_OUTPUT=$output_file \
  MOCK_DOCKER_LOG=$log_file \
  DOCKER_BIN=$bin_dir/docker \
  "$runner" \
    --base-sha "$base_sha" \
    --head-sha "$head_sha" \
    --config-file cleanup.properties \
    --image ghcr.io/carstenartur/sandbox-cleanup:test \
    --scope both \
    --source-mode changed \
    --output-dir "$output"
)

grep -Fx 'has_changes=true' "$output_file" >/dev/null
grep -Fx 'input_java_count=3' "$output_file" >/dev/null
grep -Fx 'project_count=1' "$output_file" >/dev/null
grep -Fx 'changed_file_count=2' "$output_file" >/dev/null
grep -Fx 'skipped_file_count=1' "$output_file" >/dev/null
grep -F -- '--import-project /workspace/alpha' "$log_file" >/dev/null
grep -F -- '--source /workspace/alpha/src/A.java' "$log_file" >/dev/null
grep -F -- "--source /workspace/alpha/src/With\\ Space.java" "$log_file" >/dev/null
[[ $(grep -c '^run ' "$log_file") -eq 1 ]]
grep -F '`alpha`' "$output/summary.md" >/dev/null
grep -F '// cleanup suggestion' "$output/suggestions.patch" >/dev/null
grep -F 'orphan/B.java' "$output/skipped-files.txt" >/dev/null
[[ -s $output/report-0.json ]]

# A pull request without Java changes must not invoke Docker and must be a clean no-op.
printf 'documentation\n' > "$repo/README.md"
(
  cd "$repo"
  git restore .
  git add README.md
  git commit -qm docs
)
no_java_head=$(git -C "$repo" rev-parse HEAD)
no_java_base=$(git -C "$repo" rev-parse HEAD^)
no_java_output=$test_root/no-java-output
no_java_github_output=$test_root/no-java-github-output
no_java_log=$test_root/no-java-docker.log
: > "$no_java_github_output"
: > "$no_java_log"
(
  cd "$repo"
  GITHUB_OUTPUT=$no_java_github_output \
  MOCK_DOCKER_LOG=$no_java_log \
  DOCKER_BIN=$bin_dir/docker \
  "$runner" \
    --base-sha "$no_java_base" \
    --head-sha "$no_java_head" \
    --config-file cleanup.properties \
    --image ghcr.io/carstenartur/sandbox-cleanup:test \
    --output-dir "$no_java_output"
)
grep -Fx 'has_changes=false' "$no_java_github_output" >/dev/null
grep -Fx 'input_java_count=0' "$no_java_github_output" >/dev/null
[[ ! -s $no_java_log ]]
[[ ! -s $no_java_output/suggestions.patch ]]

echo 'cleanup-review contract tests passed'
