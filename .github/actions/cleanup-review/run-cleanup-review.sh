#!/usr/bin/env bash
# Copyright (c) 2026 Carsten Hammer.
# SPDX-License-Identifier: EPL-2.0

set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: run-cleanup-review.sh \
  --base-sha <sha> \
  --head-sha <sha> \
  [--config-file <repository-relative path>] \
  --image <container image> \
  [--java-home <host JDK directory>] \
  [--scope main|test|both] \
  [--source-mode changed|project] \
  [--unmatched-files error|warn] \
  [--output-dir <directory>]

The repository must be checked out at --head-sha with full history available.
Omit --config-file to use the shipped conservative encoding profile. Existing
Eclipse .project/.classpath/.settings are used as-is; build files are not converted.
USAGE
}

die() {
  echo "::error::$*" >&2
  exit 1
}

note() {
  echo "::notice::$*"
}

warning() {
  echo "::warning::$*" >&2
}

emit_output() {
  local key=$1
  local value=$2
  if [[ -n ${GITHUB_OUTPUT:-} ]]; then
    printf '%s=%s\n' "$key" "$value" >> "$GITHUB_OUTPUT"
  fi
}

append_quoted_line() {
  local output_file=$1
  local value=$2
  printf '%q\n' "$value" >> "$output_file"
}

base_sha=
head_sha=
config_file=
image=
java_home=
scope=both
source_mode=changed
unmatched_files=error
output_dir=${RUNNER_TEMP:-/tmp}/sandbox-cleanup-review

while (($# > 0)); do
  case $1 in
    --base-sha)
      (($# >= 2)) || die "--base-sha requires a value"
      base_sha=$2
      shift 2
      ;;
    --head-sha)
      (($# >= 2)) || die "--head-sha requires a value"
      head_sha=$2
      shift 2
      ;;
    --config-file)
      (($# >= 2)) || die "--config-file requires a value"
      config_file=$2
      shift 2
      ;;
    --image)
      (($# >= 2)) || die "--image requires a value"
      image=$2
      shift 2
      ;;
    --java-home)
      (($# >= 2)) || die "--java-home requires a value"
      java_home=$2
      shift 2
      ;;
    --scope)
      (($# >= 2)) || die "--scope requires a value"
      scope=$2
      shift 2
      ;;
    --source-mode)
      (($# >= 2)) || die "--source-mode requires a value"
      source_mode=$2
      shift 2
      ;;
    --unmatched-files)
      (($# >= 2)) || die "--unmatched-files requires a value"
      unmatched_files=$2
      shift 2
      ;;
    --output-dir)
      (($# >= 2)) || die "--output-dir requires a value"
      output_dir=$2
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      die "Unknown argument: $1"
      ;;
  esac
done

[[ -n $base_sha ]] || die "--base-sha is required"
[[ -n $head_sha ]] || die "--head-sha is required"
[[ -n $image ]] || die "--image is required"
[[ $scope == main || $scope == test || $scope == both ]] || die "Invalid scope: $scope"
[[ $source_mode == changed || $source_mode == project ]] || die "Invalid source mode: $source_mode"
[[ $unmatched_files == error || $unmatched_files == warn ]] || die "Invalid unmatched-files policy: $unmatched_files"

# A released image may predate the source project's Java baseline. An explicit
# host JDK is mounted read-only; never rewrite the project's compiler settings.
if [[ -n $java_home ]]; then
  java_home=$(realpath -- "$java_home") || die "Cannot resolve JDK directory"
  [[ -x $java_home/bin/java && -f $java_home/release ]] || die "JDK must contain an executable bin/java and a release file"
  [[ $java_home != *:* && $java_home != *$'\n'* ]] || die "JDK path cannot contain a colon or newline"
fi

repo_root=$(git rev-parse --show-toplevel 2>/dev/null) || die "The action must run inside a Git repository"
repo_root=$(realpath "$repo_root")
cd "$repo_root"

resolved_base=$(git rev-parse --verify "${base_sha}^{commit}" 2>/dev/null) || die "Base commit is unavailable: $base_sha"
resolved_head=$(git rev-parse --verify "${head_sha}^{commit}" 2>/dev/null) || die "Head commit is unavailable: $head_sha"
current_head=$(git rev-parse --verify HEAD)
[[ $current_head == "$resolved_head" ]] || die "Checkout mismatch: HEAD is $current_head, expected $resolved_head"

if [[ -n $(git status --porcelain --untracked-files=all) ]]; then
  die "The checkout is not clean before the cleanup run"
fi

config_mount=()
if [[ -z $config_file ]]; then
  action_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
  config_path=$action_dir/profiles/conservative-encoding.properties
  config_rel="built-in conservative encoding"
  config_container=/review-config/cleanup.properties
  [[ -f $config_path ]] || die "The installed action is missing its built-in cleanup profile"
  [[ $config_path != *:* && $config_path != *$'\n'* ]] || die "Action path cannot contain a colon or newline"
  config_mount=(--volume "$config_path:$config_container:ro")
else
  config_path=$(realpath -m "$repo_root/$config_file")
  case $config_path in
    "$repo_root"/*) ;;
    *) die "Configuration path escapes the repository: $config_file" ;;
  esac
  [[ -f $config_path ]] || die "Cleanup configuration does not exist: $config_file (omit config-file to use the built-in profile)"
  config_rel=${config_path#"$repo_root"/}
  config_container=/workspace/$config_rel
fi

mkdir -p "$output_dir"
output_dir=$(realpath "$output_dir")
case $output_dir in
  /) die "The cleanup evidence directory must not be the filesystem root" ;;
  "$repo_root"|"$repo_root"/*) die "The cleanup evidence directory must be outside the repository" ;;
esac
find "$output_dir" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
mkdir -p "$output_dir/manifests"

projects_file=$output_dir/projects.txt
skipped_file=$output_dir/skipped-files.txt
changed_file_list=$output_dir/changed-files.txt
patch_file=$output_dir/suggestions.patch
summary_file=$output_dir/summary.md
: > "$projects_file"
: > "$skipped_file"
: > "$changed_file_list"
: > "$patch_file"
emit_output output_dir "$output_dir"
emit_output summary_file "$summary_file"
emit_output analysis_status failed

is_java_project_root() {
  local directory=$1
  grep -Eq '<nature>[[:space:]]*org\.eclipse\.jdt\.core\.javanature[[:space:]]*</nature>' \
    "$directory/.project"
}

find_project_root() {
  local candidate=$1
  local directory
  directory=$(dirname "$candidate")
  while :; do
    if [[ -f $directory/.project ]]; then
      if is_java_project_root "$directory"; then
        printf '%s\n' "$directory"
        return 0
      fi
      # An Eclipse project boundary is authoritative. Do not accidentally
      # associate a file from a non-Java project with a Java parent project.
      return 1
    fi
    [[ $directory == "$repo_root" ]] && break
    local parent
    parent=$(dirname "$directory")
    [[ $parent != "$directory" ]] || break
    directory=$parent
  done
  return 1
}

declare -A project_index_by_root=()
declare -a project_roots=()
skipped_count=0
input_java_count=0

# Do not hide Git failures inside process substitution and report an empty input.
git diff -z --name-only --diff-filter=ACMR "$resolved_base...$resolved_head" -- '*.java' \
  > "$output_dir/manifests/pr-input.files" || die "Cannot determine PR Java input; no cleanup ran"
while IFS= read -r -d '' relative_path; do
  [[ -f $repo_root/$relative_path ]] || continue
  ((input_java_count += 1))
  absolute_path=$(realpath "$repo_root/$relative_path")
  case $absolute_path in
    "$repo_root"/*) ;;
    *) die "Changed Java path resolves outside the repository: $relative_path" ;;
  esac
  if ! project_root=$(find_project_root "$absolute_path"); then
    append_quoted_line "$skipped_file" "$relative_path"
    ((skipped_count += 1))
    warning "Skipping Java file outside an Eclipse Java project: $relative_path"
    continue
  fi

  project_rel=${project_root#"$repo_root"/}
  if [[ $project_root == "$repo_root" ]]; then
    project_rel=.
  fi

  if [[ ! -v 'project_index_by_root[$project_rel]' ]]; then
    project_index=${#project_roots[@]}
    project_index_by_root[$project_rel]=$project_index
    project_roots+=("$project_rel")
    append_quoted_line "$projects_file" "$project_rel"
    : > "$output_dir/manifests/project-${project_index}.files"
  else
    project_index=${project_index_by_root[$project_rel]}
  fi
  printf '%s\0' "$relative_path" >> "$output_dir/manifests/project-${project_index}.files"
done < "$output_dir/manifests/pr-input.files"

project_count=${#project_roots[@]}
emit_output input_java_count "$input_java_count"
emit_output project_count "$project_count"
emit_output skipped_file_count "$skipped_count"
if ((skipped_count > 0)); then
  analysis_status=partial-analysis
  if ((project_count == 0)); then analysis_status=no-supported-projects; fi
  emit_output analysis_status "$analysis_status"
  {
    echo "## Cleanup input not fully analysed"
    echo
    echo "**$skipped_count Java file(s) are not analysed:** no enclosing Eclipse Java project was found."
    echo "Existing Eclipse .project, .classpath and .settings are reused, not generated or overwritten."
    echo "A Maven pom.xml or Gradle build file alone is not an Eclipse project import."
    echo "See the project prerequisites in GITHUB_ACTIONS.md in carstenartur/sandbox."
    echo
    echo '```text'
    cat "$skipped_file"
    echo '```'
  } > "$summary_file"
  if [[ $unmatched_files == error ]]; then
    die "$skipped_count Java file(s) not analysed. Supply an Eclipse Java project with its classpath, or explicitly set unmatched-files: warn to permit partial coverage. No cleanup ran."
  fi
  warning "$skipped_count Java file(s) not analysed; unmatched-files: warn explicitly permits incomplete coverage"
fi
docker_bin=${DOCKER_BIN:-docker}
image_identity=$image

if ((project_count > 0)); then
  command -v "$docker_bin" >/dev/null 2>&1 || die "Docker command not found: $docker_bin"
  if [[ ${SANDBOX_CLEANUP_SKIP_PULL:-false} != true ]]; then
    "$docker_bin" pull "$image"
  fi
  inspected_identity=$($docker_bin image inspect "$image" --format '{{index .RepoDigests 0}}' 2>/dev/null | head -n 1 || true)
  if [[ -n $inspected_identity ]]; then
    image_identity=$inspected_identity
  fi
fi

for project_index in "${!project_roots[@]}"; do
  project_rel=${project_roots[$project_index]}
  project_container=/workspace
  if [[ $project_rel != . ]]; then
    project_container=/workspace/$project_rel
  fi

  report_name=report-${project_index}.json
  runtime_args=()
  if [[ -n $java_home ]]; then
    runtime_args+=(--volume "$java_home:/opt/sandbox-review-jdk:ro"
      --env JAVA_HOME=/opt/sandbox-review-jdk
      --env PATH=/opt/sandbox-review-jdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin)
  fi
  command_args=(
    "$docker_bin" run --rm
    --user "$(id -u):$(id -g)"
    --env HOME=/tmp/sandbox-home
    --env "SANDBOX_CLEANUP_WORKSPACE=/tmp/sandbox-cleanup-workspace-${project_index}"
    --volume "$repo_root:/workspace"
    --volume "$output_dir:/review-output"
    "${config_mount[@]}"
    --workdir /workspace
    "${runtime_args[@]}"
    "$image"
    --config "$config_container"
    --mode apply
    --scope "$scope"
    --import-project "$project_container"
    --report "/review-output/$report_name"
  )

  if [[ $source_mode == project ]]; then
    command_args+=(--source "$project_container")
  else
    while IFS= read -r -d '' relative_path; do
      command_args+=(--source "/workspace/$relative_path")
    done < "$output_dir/manifests/project-${project_index}.files"
  fi

  note "Running Sandbox cleanup for Eclipse project $project_rel"
  "${command_args[@]}"
done

non_java_change=false
while IFS= read -r -d '' untracked_path; do
  case $untracked_path in
    *.java)
      # Intent-to-add makes a newly generated Java file visible to git diff and
      # therefore to the complete cleanup commit and patch without staging content.
      git add --intent-to-add -- "$untracked_path"
      ;;
    *)
      non_java_change=true
      warning "Cleanup unexpectedly created a non-Java file: $untracked_path"
      ;;
  esac
done < <(git ls-files --others --exclude-standard -z)

while IFS= read -r -d '' changed_path; do
  case $changed_path in
    *.java) ;;
    *)
      non_java_change=true
      warning "Cleanup unexpectedly modified a non-Java file: $changed_path"
      ;;
  esac
done < <(git diff -z --name-only)
[[ $non_java_change == false ]] || die "Cleanup modified files outside its Java-source contract"

changed_file_count=0
while IFS= read -r -d '' changed_path; do
  append_quoted_line "$changed_file_list" "$changed_path"
  ((changed_file_count += 1))
done < <(git diff -z --name-only --diff-filter=ACMRD -- '*.java')

git diff --binary --no-ext-diff --no-textconv --src-prefix=a/ --dst-prefix=b/ -- '*.java' > "$patch_file"

has_changes=false
if ((changed_file_count > 0)); then
  has_changes=true
fi

analysis_status=no-cleanup-changes
if ((input_java_count == 0)); then
  analysis_status=no-java-changes
elif ((project_count == 0)); then
  analysis_status=no-supported-projects
elif ((skipped_count > 0)); then
  analysis_status=partial-analysis
elif [[ $has_changes == true ]]; then
  analysis_status=changes-proposed
fi
emit_output analysis_status "$analysis_status"

{
  echo "## Sandbox cleanup review"
  echo
  echo "**Result:** \`$analysis_status\`"
  if ((skipped_count > 0)); then
    echo
    echo "**Incomplete coverage: $skipped_count Java file(s) were not analysed.** See skipped-files.txt in the evidence."
  fi
  echo
  echo "| Field | Value |"
  echo "|---|---:|"
  echo "| Changed Java files in the PR input | $input_java_count |"
  echo "| Imported Eclipse projects | $project_count |"
  echo "| Cleanup-modified Java files | $changed_file_count |"
  echo "| Skipped files outside Eclipse Java projects | $skipped_count |"
  echo
  echo "**Profile:** \`$config_rel\`  "
  echo "**Analyzed head:** \`$resolved_head\`  "
  echo "**Scope:** \`$scope\`  "
  echo "**Source mode:** \`$source_mode\`  "
  echo "**Cleanup image:** \`$image_identity\`"
  echo
  if ((project_count > 0)); then
    echo "### Imported Eclipse projects"
    echo
    for project_rel in "${project_roots[@]}"; do
      echo "- \`$project_rel\`"
    done
    echo
  fi
  if [[ $has_changes == true ]]; then
    echo "The review links to one complete cleanup commit, including edits outside the PR diff. Inspect its GitHub comparison, then open and merge the cleanup PR into the original PR branch to accept every change together. The artifact retains suggestions.patch, the captured review, and JSON reports."
    echo
    echo "The default CLI processes files separately. Choosing project source mode widens the input; it does not create a coordinated multi-file cleanup transaction."
  elif ((project_count == 0 && input_java_count > 0)); then
    echo "No cleanup ran because none of the changed Java files belongs to an Eclipse Java project."
  else
    echo "The selected cleanup produced no source changes."
  fi
} > "$summary_file"

emit_output has_changes "$has_changes"
emit_output input_java_count "$input_java_count"
emit_output project_count "$project_count"
emit_output changed_file_count "$changed_file_count"
emit_output skipped_file_count "$skipped_count"
emit_output patch_file "$patch_file"
emit_output output_dir "$output_dir"
emit_output summary_file "$summary_file"

if [[ $has_changes == true ]]; then
  note "Sandbox cleanup produced suggestions for $changed_file_count Java file(s)"
elif ((project_count == 0)); then
  note "Sandbox cleanup did not analyse any project ($analysis_status)"
else
  note "Sandbox cleanup completed without source changes ($analysis_status)"
fi
