#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
CONFIG=${JDT_UI_BUILD_CONFIG:-"$SCRIPT_DIR/jdt-ui-build.env"}
source "$CONFIG"
JDT_UI=$(cd "$1" && pwd -P)
mkdir -p "$2"
BUILD_DIR=$(cd "$2" && pwd -P)
MAVEN_BIN=$3
shift 3
[[ "${PATCHED_JDT_UI_RELENG_COMMIT:?}" =~ ^[0-9a-f]{40}$ ]]
if [[ ! -d "$BUILD_DIR/.git" ]]; then
  git -C "$BUILD_DIR" init -q
  git -C "$BUILD_DIR" remote add origin "$PATCHED_JDT_UI_RELENG_REPOSITORY"
  git -C "$BUILD_DIR" fetch --depth=1 origin "$PATCHED_JDT_UI_RELENG_COMMIT"
  git -C "$BUILD_DIR" sparse-checkout set eclipse-platform-parent eclipse.platform.releng.prereqs.sdk .mvn
  git -C "$BUILD_DIR" checkout --detach -q FETCH_HEAD
fi
[[ $(git -C "$BUILD_DIR" rev-parse HEAD) == "$PATCHED_JDT_UI_RELENG_COMMIT" ]]
[[ $(git -C "$BUILD_DIR" rev-parse HEAD:eclipse.jdt.ui) == "$(git -C "$JDT_UI" rev-parse HEAD)" ]]
printf '%s  %s\n' \
  "$PATCHED_JDT_UI_PARENT_SHA256" "$BUILD_DIR/eclipse-platform-parent/pom.xml" \
  "$PATCHED_JDT_UI_PREREQS_SHA256" "$BUILD_DIR/eclipse.platform.releng.prereqs.sdk/eclipse-sdk-prereqs.target" \
  | sha256sum --check --strict
created=()
trap 'if ((${#created[@]})); then rm -rf -- "${created[@]}"; fi' EXIT
for module in eclipse-platform-parent eclipse.platform.releng.prereqs.sdk; do
  destination="$(dirname "$JDT_UI")/$module"
  if [[ ! -e "$destination" && ! -L "$destination" ]]; then
    created+=("$destination")
    cp -R "$BUILD_DIR/$module" "$destination"
  fi
  cmp "$BUILD_DIR/$module/pom.xml" "$destination/pom.xml"
done
cmp "$BUILD_DIR/eclipse.platform.releng.prereqs.sdk/eclipse-sdk-prereqs.target" \
  "$(dirname "$JDT_UI")/eclipse.platform.releng.prereqs.sdk/eclipse-sdk-prereqs.target"
cp "$SCRIPT_DIR/jdt-ui-reactor.xml" "$BUILD_DIR/pom.xml"
cp "$CONFIG" "$BUILD_DIR/build-inputs.env"
"$MAVEN_BIN" -f "$BUILD_DIR/pom.xml" \
  "-Djdt-ui-source=$(realpath --relative-to="$BUILD_DIR" "$JDT_UI")" \
  "-Declipse-sdk-repo=$PATCHED_JDT_UI_SDK_REPOSITORY" "$@"
