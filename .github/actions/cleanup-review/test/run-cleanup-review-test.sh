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
mkdir -p "$repo/alpha/src" "$repo/orphan" "$repo/.github/scripts" "$bin_dir"

cat > "$repo/.project" <<'PROJECT'
<?xml version="1.0" encoding="UTF-8"?>
<projectDescription>
  <name>aggregate</name>
  <natures>
    <nature>org.eclipse.m2e.core.maven2Nature</nature>
  </natures>
</projectDescription>
PROJECT
cat > "$repo/alpha/.project" <<'PROJECT'
<?xml version="1.0" encoding="UTF-8"?>
<projectDescription>
  <name>alpha</name>
  <natures>
    <nature>org.eclipse.jdt.core.javanature</nature>
  </natures>
</projectDescription>
PROJECT
cat > "$repo/alpha/src/A.java" <<'JAVA'
import java.nio.charset.Charset;

class A {
    int firstUnchangedValue() {
        return 1;
    }

    int secondUnchangedValue() {
        return 2;
    }

    String first(byte[] bytes) {
        return new String(bytes, Charset.forName("UTF-8"));
    }

    int thirdUnchangedValue() {
        return 3;
    }

    int fourthUnchangedValue() {
        return 4;
    }

    String second(byte[] bytes) {
        return new String(bytes, Charset.forName("UTF-8"));
    }

    int fifthUnchangedValue() {
        return 5;
    }

    int sixthUnchangedValue() {
        return 6;
    }
}
JAVA
cat > "$repo/alpha/src/With Space.java" <<'JAVA'
class WithSpace {
    String value() { return "before"; }
}
JAVA
cat > "$repo/alpha/src/Obsolete.java" <<'JAVA'
final class Obsolete {
    static int legacyValue() {
        return -1;
    }
}
JAVA
cat > "$repo/orphan/B.java" <<'JAVA'
class B {}
JAVA
cat > "$repo/.github/scripts/Verifier.java" <<'JAVA'
class Verifier {}
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
  git config core.autocrlf false
  git config diff.context 3
  git add .
  git commit -qm baseline
)
base_sha=$(git -C "$repo" rev-parse HEAD)
printf '\n// pull request change\n' >> "$repo/alpha/src/A.java"
printf '\n// pull request change\n' >> "$repo/alpha/src/With Space.java"
printf '\n// pull request change\n' >> "$repo/orphan/B.java"
printf '\n// pull request change\n' >> "$repo/.github/scripts/Verifier.java"
(
  cd "$repo"
  git add .
  git commit -qm head
)
head_sha=$(git -C "$repo" rev-parse HEAD)

# The PR only appends a comment. Neither the import nor either expression is
# visible in its normal diff context, but the cleanup must retain all three.
pr_context=$test_root/pr-context.patch
git -C "$repo" diff --unified=3 "$base_sha...$head_sha" -- alpha/src/A.java > "$pr_context"
grep -F '// pull request change' "$pr_context" >/dev/null
if grep -F 'Charset' "$pr_context" >/dev/null; then
  echo 'The fixture must keep every charset edit outside the PR diff context' >&2
  exit 1
fi

# Build an independent byte-for-byte oracle, including a new and a deleted file.
expected_repo=$test_root/expected-repository
git clone --quiet --no-hardlinks "$repo" "$expected_repo"
git -C "$expected_repo" config core.autocrlf false
cat > "$expected_repo/alpha/src/A.java" <<'JAVA'
import java.nio.charset.StandardCharsets;

class A {
    int firstUnchangedValue() {
        return 1;
    }

    int secondUnchangedValue() {
        return 2;
    }

    String first(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    int thirdUnchangedValue() {
        return 3;
    }

    int fourthUnchangedValue() {
        return 4;
    }

    String second(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    int fifthUnchangedValue() {
        return 5;
    }

    int sixthUnchangedValue() {
        return 6;
    }
}

// pull request change
JAVA
cat > "$expected_repo/alpha/src/With Space.java" <<'JAVA'
class WithSpace {
    String value() { return "after"; }
}

// pull request change
JAVA
cat > "$expected_repo/alpha/src/Generated.java" <<'JAVA'
final class Generated {
    static final String NAME = "cleanup";
}
JAVA
rm "$expected_repo/alpha/src/Obsolete.java"
git -C "$expected_repo" add --all
expected_tree=$(git -C "$expected_repo" write-tree)

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
  case $relative in
    alpha/src/A.java)
      sed 's/import java.nio.charset.Charset;/import java.nio.charset.StandardCharsets;/;
           s/Charset.forName("UTF-8")/StandardCharsets.UTF_8/g' \
        "$workspace/$relative" > "$workspace/$relative.tmp"
      mv "$workspace/$relative.tmp" "$workspace/$relative"
      ;;
    'alpha/src/With Space.java')
      sed 's/return "before";/return "after";/' \
        "$workspace/$relative" > "$workspace/$relative.tmp"
      mv "$workspace/$relative.tmp" "$workspace/$relative"
      ;;
    *)
      echo "Unexpected cleanup input: $source" >&2
      exit 1
      ;;
  esac
done
cat > "$workspace/alpha/src/Generated.java" <<'JAVA'
final class Generated {
    static final String NAME = "cleanup";
}
JAVA
rm "$workspace/alpha/src/Obsolete.java"
report_path=$review_output/${report#/review-output/}
printf '{"tool":"sandbox-cleanup","filesChanged":%d}\n' "$((${#sources[@]} + 2))" > "$report_path"
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
grep -Fx 'input_java_count=4' "$output_file" >/dev/null
grep -Fx 'project_count=1' "$output_file" >/dev/null
grep -Fx 'changed_file_count=4' "$output_file" >/dev/null
grep -Fx 'skipped_file_count=2' "$output_file" >/dev/null
grep -F -- '--import-project /workspace/alpha' "$log_file" >/dev/null
grep -F -- '--source /workspace/alpha/src/A.java' "$log_file" >/dev/null
grep -F -- "--source /workspace/alpha/src/With\\ Space.java" "$log_file" >/dev/null
[[ $(grep -c '^run ' "$log_file") -eq 1 ]]
grep -F '`alpha`' "$output/summary.md" >/dev/null
grep -Fx '+import java.nio.charset.StandardCharsets;' "$output/suggestions.patch" >/dev/null
[[ $(grep -Fxc '+        return new String(bytes, StandardCharsets.UTF_8);' "$output/suggestions.patch") -eq 2 ]]
[[ $(awk '
  /^diff --git / { in_a = ($0 == "diff --git a/alpha/src/A.java b/alpha/src/A.java") }
  in_a && /^@@ / { count++ }
  END { print count + 0 }
' "$output/suggestions.patch") -eq 3 ]]
grep -Fx '+    String value() { return "after"; }' "$output/suggestions.patch" >/dev/null
grep -Fx '+final class Generated {' "$output/suggestions.patch" >/dev/null
grep -Fx -- '-final class Obsolete {' "$output/suggestions.patch" >/dev/null
grep -Fx 'new file mode 100644' "$output/suggestions.patch" >/dev/null
grep -Fx 'deleted file mode 100644' "$output/suggestions.patch" >/dev/null
grep -F 'orphan/B.java' "$output/skipped-files.txt" >/dev/null
grep -F '.github/scripts/Verifier.java' "$output/skipped-files.txt" >/dev/null
[[ -s $output/report-0.json ]]

# Compare the entire generated result, then prove the artifact alone can undo
# and reproduce it. Tree equality covers all file bytes, names and modes,
# including files that were not in the PR's changed-file input.
git -C "$repo" add --all
[[ $(git -C "$repo" write-tree) == "$expected_tree" ]]
cmp "$repo/alpha/src/A.java" "$expected_repo/alpha/src/A.java"
cmp "$repo/alpha/src/With Space.java" "$expected_repo/alpha/src/With Space.java"
git -C "$repo" apply --reverse --check --index "$output/suggestions.patch"
git -C "$repo" apply --reverse --index "$output/suggestions.patch"
[[ $(git -C "$repo" write-tree) == "$(git -C "$repo" rev-parse "${head_sha}^{tree}")" ]]
[[ -z $(git -C "$repo" status --porcelain --untracked-files=all) ]]
git -C "$repo" apply --check --index "$output/suggestions.patch"
git -C "$repo" apply --index "$output/suggestions.patch"
[[ $(git -C "$repo" write-tree) == "$expected_tree" ]]
cmp "$repo/alpha/src/A.java" "$expected_repo/alpha/src/A.java"
cmp "$repo/alpha/src/With Space.java" "$expected_repo/alpha/src/With Space.java"
cmp "$repo/alpha/src/Generated.java" "$expected_repo/alpha/src/Generated.java"
[[ ! -e $repo/alpha/src/Obsolete.java ]]
git -C "$repo" apply --reverse --index "$output/suggestions.patch"
[[ -z $(git -C "$repo" status --porcelain --untracked-files=all) ]]

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
