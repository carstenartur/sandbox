#!/usr/bin/env bash
# Copyright (c) 2026 Carsten Hammer.
# SPDX-License-Identifier: EPL-2.0

set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: run-cleanup-review.sh \
  --base-sha <sha> \
  --head-sha <sha> \
  --config-file <repository-relative path> \
  --image <container image> \
  [--scope main|test|both] \
  [--source-mode changed|project] \
  [--output-dir <directory>]

The repository must be checked out at --head-sha with full history available.
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
scope=both
source_mode=changed
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
[[ -n $config_file ]] || die "--config-file is required"
[[ -n $image ]] || die "--image is required"
[[ $scope == main || $scope == test || $scope == both ]] || die "Invalid scope: $scope"
[[ $source_mode == changed || $source_mode == project ]] || die "Invalid source mode: $source_mode"

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

config_path=$(realpath -m "$repo_root/$config_file")
case $config_path in
  "$repo_root"/*) ;;
  *) die "Configuration path escapes the repository: $config_file" ;;
esac
[[ -f $config_path ]] || die "Cleanup configuration does not exist: $config_file"
config_rel=${config_path#"$repo_root"/}

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
done < <(git diff -z --name-only --diff-filter=ACMR "$resolved_base...$resolved_head" -- '*.java')

project_count=${#project_roots[@]}
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
  command_args=(
    "$docker_bin" run --rm
    --user "$(id -u):$(id -g)"
    --env HOME=/tmp/sandbox-home
    --env "SANDBOX_CLEANUP_WORKSPACE=/tmp/sandbox-cleanup-workspace-${project_index}"
    --volume "$repo_root:/workspace"
    --volume "$output_dir:/review-output"
    --workdir /workspace
    "$image"
    --config "/workspace/$config_rel"
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
      # therefore to the Suggested Changes publisher without staging content.
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

git diff --binary --no-ext-diff --src-prefix=a/ --dst-prefix=b/ -- '*.java' > "$patch_file"

has_changes=false
if ((changed_file_count > 0)); then
  has_changes=true
fi

{
  echo "## Sandbox cleanup review"
  echo
  echo "| Field | Value |"
  echo "|---|---:|"
  echo "| Changed Java files in the PR input | $input_java_count |"
  echo "| Imported Eclipse projects | $project_count |"
  echo "| Cleanup-modified Java files | $changed_file_count |"
  echo "| Skipped files outside Eclipse Java projects | $skipped_count |"
  echo
  echo "**Profile:** \`$config_rel\`  "
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
    echo "Applicable changed lines are published as GitHub Suggested Changes. The complete patch and JSON reports are retained as a workflow artifact, including changes that GitHub cannot place inline."
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
else
  note "Sandbox cleanup produced no applicable suggestions"
fi
