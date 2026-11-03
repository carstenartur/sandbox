#!/usr/bin/env bash
# Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0
# Process adapter for JdtMathQualificationTest; source and result semantics stay in JUnit.
set -euo pipefail
if (($# != 5)); then
  echo 'Usage: run-jdt-math-before-after.sh FIXTURE_ROOT PRODUCT OUTPUT MAVEN core|ui' >&2
  exit 2
fi
ROOT=$(cd "$1" && pwd -P)
PRODUCT=$(cd "$2" && pwd -P)
OUTPUT=$(cd "$3" && pwd -P)
MAVEN=$4
FAMILY=$5
SANDBOX=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)
IMPORTER="$SANDBOX/qa/math-optimization/src/testFixtures/jdt-workspace-importer"
case "$FAMILY" in
  core) PROJECTS=(org.eclipse.jdt.core.compiler.batch org.eclipse.jdt.core) ;;
  ui) PROJECTS=(org.eclipse.jdt.core.manipulation org.eclipse.jdt.ui) ;;
  *) exit 2 ;;
esac
[[ "$MAVEN" == /* && -x "$MAVEN" && -x "$PRODUCT/eclipse" ]]
[[ ! -e "$OUTPUT/baseline" && ! -e "$OUTPUT/generated" && ! -e "$ROOT/runtime" ]]
cp -a "$ROOT/releng/eclipse-platform-parent" "$ROOT/releng/eclipse.platform.releng.prereqs.sdk" "$ROOT/releng/.mvn" "$ROOT/"
cp "$SANDBOX/qa/upstream-jdt/jdt-ui-reactor.xml" "$ROOT/reactor.xml"
cp "$SANDBOX/qa/math-optimization/profiles/bc-strict.properties" "$OUTPUT/math-profile.properties"
cp -a "$PRODUCT" "$ROOT/runtime"
mkdir -p "$ROOT/importer-classes" "$OUTPUT/importer"
"${JAVA_HOME:?}/bin/javac" -encoding UTF-8 --release 25 -cp "$ROOT/runtime/plugins/*" -d "$ROOT/importer-classes" "$IMPORTER/src/org/sandbox/qa/workspace/Importer.java"
IMPORTER_JAR="$ROOT/runtime/plugins/org.sandbox.qa.workspace.importer_1.0.0.jar"
"$JAVA_HOME/bin/jar" --create --file "$IMPORTER_JAR" --manifest "$IMPORTER/META-INF/MANIFEST.MF" -C "$ROOT/importer-classes" . -C "$IMPORTER" plugin.xml
printf '\norg.sandbox.qa.workspace.importer,1.0.0,plugins/org.sandbox.qa.workspace.importer_1.0.0.jar,4,false\n' >> "$ROOT/runtime/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info"
cp "$IMPORTER/src/org/sandbox/qa/workspace/Importer.java" "$IMPORTER/META-INF/MANIFEST.MF" "$IMPORTER/plugin.xml" "$IMPORTER_JAR" "$OUTPUT/importer/"
sha256sum "$OUTPUT/importer/"* "$OUTPUT/math-profile.properties" "$ROOT/reactor.xml" \
  "$ROOT/eclipse-platform-parent/pom.xml" "$ROOT/eclipse.platform.releng.prereqs.sdk/eclipse-sdk-prereqs.target" > "$OUTPUT/build-inputs.sha256"
"$JAVA_HOME/bin/java" -version > "$OUTPUT/java-version.txt" 2>&1
git -C "$ROOT/$FAMILY" diff --binary > "$OUTPUT/build-configuration.patch"
sha256sum "$ROOT/core/org.eclipse.jdt.core/pom.xml" "$OUTPUT/build-configuration.patch" >> "$OUTPUT/build-inputs.sha256"
export MAVEN_OPTS="${MAVEN_OPTS-} -Xmx1536m -Declipse.p2.mirrors=false"

run_phase() {
  local phase=$1 status=0 report jar project
  local destination="$OUTPUT/$phase"
  mkdir -p "$destination/reports" "$destination/runtime" "$destination/bundles" "$destination/classes"
  local -a command=(dbus-run-session -- xvfb-run -a -s '-screen 0 1280x1024x24 -noreset'
    "$MAVEN" --batch-mode --no-transfer-progress --no-snapshot-updates
    -f "$ROOT/reactor.xml" "-Djdt-ui-source=$ROOT/$FAMILY" "-Dmaven.repo.local=$ROOT/m2"
    -Pbuild-individual-bundles -DskipTests=false -DforkCount=1
    -Dtycho.baseline.replace=none -DcompilerBaselineReplace=none
    -Dsurefire.testArgLine=-Xmx2g -Dtycho.surefire.argLine=-Xmx2g
    -Dcbi-ecj-version=3.47.0-20261102.234213-18
    -Declipse-sdk-repo=https://download.eclipse.org/eclipse/updates/4.41/R-4.41-202608281142/
    --fail-at-end clean verify)
  if [[ "$phase" == generated ]]; then command+=(--offline); fi
  printf '%q ' "${command[@]}" > "$OUTPUT/$phase-command.txt"
  printf '\n' >> "$OUTPUT/$phase-command.txt"
  (cd "$ROOT" && "${command[@]}") > "$OUTPUT/$phase-maven.log" 2>&1 || status=$?
  printf '%s\n' "$status" > "$OUTPUT/$phase-exit-code.txt"
  while IFS= read -r -d '' report; do
    mkdir -p "$destination/reports/$(dirname "$report")"
    cp "$ROOT/$FAMILY/$report" "$destination/reports/$report"
  done < <(cd "$ROOT/$FAMILY" && rg --files --hidden --no-ignore -0 -g '**/target/surefire-reports/TEST-*.xml' -g '**/target/surefire-reports/*.dump*')
  while IFS= read -r -d '' report; do
    mkdir -p "$destination/runtime/$(dirname "$report")"
    cp "$ROOT/$FAMILY/$report" "$destination/runtime/$report"
  done < <(cd "$ROOT/$FAMILY" && rg --files --hidden --no-ignore -0 -g '**/target/work/configuration/config.ini' -g '**/target/work/data/.metadata/.log')
  for project in "${PROJECTS[@]}"; do
    local -a jars=("$ROOT/$FAMILY/$project/target/$project"-*-SNAPSHOT.jar)
    [[ ${#jars[@]} == 1 && -f ${jars[0]} ]]
    printf '%s\n' "${jars[0]}" > "$destination/bundles/$project.path"
    cp "${jars[0]}" "$destination/bundles/$project.jar"
    cp -a "$ROOT/$FAMILY/$project/target/classes" "$destination/classes/$project"
    if [[ "$project" == org.eclipse.jdt.core ]]; then
      local -a adapters=("$ROOT/$FAMILY/$project/target/$project"-*-antadapter.jar)
      [[ ${#adapters[@]} == 1 && -f ${adapters[0]} ]]
      cp "${adapters[0]}" "$destination/bundles/$project-antadapter.jar"
      cp -a "$ROOT/$FAMILY/$project/target/jdtCompilerAdapter.jar-classes" "$destination/classes/$project-antadapter"
    fi
  done
}

run_phase baseline
git -C "$ROOT/$FAMILY" diff --exit-code HEAD -- '*.java'
"$ROOT/runtime/eclipse" -nosplash -consoleLog -clean -vm "$JAVA_HOME/bin/java" -data "$ROOT/workspace" \
  -application org.sandbox.qa.workspace.importer.bootstrap \
  "$ROOT/core/org.eclipse.jdt.annotation" "$ROOT/core/org.eclipse.jdt.core.compiler.batch" "$ROOT/core/org.eclipse.jdt.core" \
  "$ROOT/ui/org.eclipse.jdt.core.manipulation" "$ROOT/ui/org.eclipse.jdt.ui" \
  -vmargs -Xmx2g > "$OUTPUT/import.log" 2>&1
for project in "${PROJECTS[@]}"; do
  status=0
  "$ROOT/runtime/eclipse" -nosplash -consoleLog -vm "$JAVA_HOME/bin/java" -data "$ROOT/workspace" \
    -application sandbox_math_cleanup.analysis --project "$project" --apply \
    --config "$OUTPUT/math-profile.properties" --report "$OUTPUT/$project.json" \
    -vmargs -Xmx2g > "$OUTPUT/$project-apply.log" 2>&1 || status=$?
  printf '%s\n' "$status" > "$OUTPUT/$project-exit-code.txt"
  [[ "$status" == 0 ]]
done
git -C "$ROOT/$FAMILY" diff --binary -- '*.java' > "$OUTPUT/mathematics.patch"
git -C "$ROOT/$FAMILY" diff --check
sha256sum "$OUTPUT/mathematics.patch" > "$OUTPUT/mathematics.patch.sha256"
run_phase generated
[[ $(cat "$OUTPUT/baseline-exit-code.txt") == 0 && $(cat "$OUTPUT/generated-exit-code.txt") == 0 ]]
