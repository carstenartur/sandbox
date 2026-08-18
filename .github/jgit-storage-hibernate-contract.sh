#!/usr/bin/env bash
# Consumer-owned jgit-storage-hibernate contract for Sandbox.
set -euo pipefail

mode=${JGIT_STORAGE_HIBERNATE_CONTRACT_MODE:-candidate}
candidate_version=${JGIT_STORAGE_HIBERNATE_CANDIDATE_VERSION:-}
evidence_dir=target/jgit-storage-hibernate-contract
mkdir -p "$evidence_dir"

case "$mode" in
  candidate)
    if [[ -z "$candidate_version" ]]; then
      echo "Candidate mode requires JGIT_STORAGE_HIBERNATE_CANDIDATE_VERSION." >&2
      exit 64
    fi
    ;;
  baseline)
    ;;
  *)
    echo "Unsupported contract mode: $mode" >&2
    exit 64
    ;;
esac

java -version 2>&1 | tee "$evidence_dir/java-version.log"
java_specification="$({ java -XshowSettings:properties -version; } 2>&1 \
  | sed -n 's/^ *java.specification.version = //p' \
  | tail -n 1)"
if [[ "$java_specification" != "21" ]]; then
  echo "Sandbox's storage contract requires Java 21, found $java_specification." >&2
  exit 1
fi

if [[ -x ./mvnw ]]; then
  maven=(./mvnw)
else
  maven=(mvn)
fi

resolved_version="$(sed -n \
  's:.*<jgit-storage-hibernate.version>\([^<]*\)</jgit-storage-hibernate.version>.*:\1:p' \
  sandbox-jgit-storage-hibernate/pom.xml | head -n 1)"
if [[ -z "$resolved_version" ]]; then
  echo "Could not read jgit-storage-hibernate.version from the consumer POM." >&2
  exit 1
fi
if [[ "$mode" == "candidate" && "$resolved_version" != "$candidate_version" ]]; then
  echo "Candidate substitution mismatch: POM=$resolved_version, expected=$candidate_version." >&2
  exit 1
fi

# These two consumer modules are intentionally built in isolation from the
# unrelated Eclipse cleanup reactor. Sandbox currently consumes released Core
# only; Search and Java Analysis remain explicit later migration slices.
set -o pipefail
"${maven[@]}" -B -ntp -nsu -N -f pom.xml install 2>&1 \
  | tee "$evidence_dir/parent-install.log"

"${maven[@]}" -B -ntp -nsu \
  -f sandbox-jgit-storage-hibernate/pom.xml \
  install 2>&1 | tee "$evidence_dir/storage-bridge.log"

"${maven[@]}" -B -ntp -nsu \
  -f sandbox-jgit-server-webapp/pom.xml \
  package 2>&1 | tee "$evidence_dir/server-package.log"

if grep -Eq 'Invalid property key|No value specified for key' \
    "$evidence_dir/storage-bridge.log"; then
  echo "Bnd rejected part of the OSGi package contract." >&2
  grep -E 'Invalid property key|No value specified for key' \
    "$evidence_dir/storage-bridge.log" >&2
  exit 1
fi

manifest=sandbox-jgit-storage-hibernate/target/classes/META-INF/MANIFEST.MF
bridge_jar="$(find sandbox-jgit-storage-hibernate/target -maxdepth 1 -type f \
  -name 'sandbox-jgit-storage-hibernate-*.jar' \
  ! -name '*-sources.jar' ! -name '*-javadoc.jar' \
  | sort | head -n 1)"
server_jar=sandbox-jgit-server-webapp/target/jgit-server.jar
for artifact in "$manifest" "$bridge_jar" "$server_jar"; do
  if [[ -z "$artifact" || ! -s "$artifact" ]]; then
    echo "Required Sandbox packaging evidence is missing: $artifact" >&2
    exit 1
  fi
done

cp "$manifest" "$evidence_dir/bridge-manifest.mf"
python3 - "$manifest" "$evidence_dir/bridge-manifest-unfolded.txt" <<'PY'
from pathlib import Path
import sys

source = Path(sys.argv[1]).read_text(encoding="utf-8")
unfolded = []
for line in source.splitlines():
    if line.startswith(" ") and unfolded:
        unfolded[-1] += line[1:]
    else:
        unfolded.append(line)
Path(sys.argv[2]).write_text("\n".join(unfolded) + "\n", encoding="utf-8")
PY
unfolded_manifest="$evidence_dir/bridge-manifest-unfolded.txt"

required_manifest_fragments=(
  'Bundle-SymbolicName: sandbox-jgit-storage-hibernate'
  'Export-Package:'
  'org.sandbox.jgit.storage.integration'
  'Import-Package:'
  'io.github.carstenartur.jgit.storage.hibernate'
)
for fragment in "${required_manifest_fragments[@]}"; do
  if ! grep -Fq "$fragment" "$unfolded_manifest"; then
    echo "Generated OSGi manifest is missing: $fragment" >&2
    cat "$unfolded_manifest" >&2
    exit 1
  fi
done

jar tf "$bridge_jar" > "$evidence_dir/bridge-jar-entries.txt"
jar tf "$server_jar" > "$evidence_dir/server-jar-entries.txt"
grep -Fq 'org/sandbox/jgit/storage/integration/JGitStorageLibraryBoundary.class' \
  "$evidence_dir/bridge-jar-entries.txt"
grep -Fq 'org/eclipse/jgit/server/config/LegacyCoreSchemaPreflight.class' \
  "$evidence_dir/server-jar-entries.txt"
grep -Fq 'io/github/carstenartur/jgit/storage/hibernate/DefaultHibernateRepositoryFactory.class' \
  "$evidence_dir/server-jar-entries.txt"

for module in sandbox-jgit-storage-hibernate sandbox-jgit-server-webapp; do
  "${maven[@]}" -B -ntp -nsu \
    -f "$module/pom.xml" \
    -Dincludes=io.github.carstenartur \
    -DoutputType=text \
    -DoutputFile="$PWD/$evidence_dir/${module}-dependency-tree.txt" \
    dependency:tree
done

cat "$evidence_dir"/*-dependency-tree.txt \
  > "$evidence_dir/dependency-tree.txt"
test -s "$evidence_dir/dependency-tree.txt"

for forbidden_module in \
    jgit-storage-hibernate-search \
    jgit-storage-hibernate-java-analysis \
    jgit-storage-hibernate-architecture \
    jgit-storage-hibernate-benchmarks; do
  if grep -Fq "$forbidden_module" "$evidence_dir/dependency-tree.txt"; then
    echo "Sandbox's Core-only contract resolved forbidden module $forbidden_module." >&2
    cat "$evidence_dir/dependency-tree.txt" >&2
    exit 1
  fi
done
core_coordinate="io.github.carstenartur:jgit-storage-hibernate-core:jar:$resolved_version:"
if ! grep -Fq "$core_coordinate" "$evidence_dir/dependency-tree.txt"; then
  echo "Sandbox did not resolve expected Core coordinate $core_coordinate." >&2
  cat "$evidence_dir/dependency-tree.txt" >&2
  exit 1
fi

report_directories=()
for directory in \
    sandbox-jgit-storage-hibernate/target/surefire-reports \
    sandbox-jgit-server-webapp/target/surefire-reports; do
  if [[ -d "$directory" ]]; then
    report_directories+=("$directory")
  fi
done
if (( ${#report_directories[@]} == 0 )); then
  echo "The Sandbox storage modules produced no Surefire report directories." >&2
  exit 1
fi
find "${report_directories[@]}" -type f -name 'TEST-*.xml' -print \
  | sort > "$evidence_dir/test-reports.txt"
if [[ ! -s "$evidence_dir/test-reports.txt" ]]; then
  echo "The Sandbox storage modules produced no test reports." >&2
  exit 1
fi

python3 - \
    "$evidence_dir/result.json" \
    "$mode" \
    "$candidate_version" \
    "$resolved_version" \
    "$java_specification" <<'PY'
import json
from pathlib import Path
import sys

output, mode, candidate_version, resolved_version, java_specification = sys.argv[1:]
evidence = {
    "consumer": "sandbox",
    "mode": mode,
    "candidateVersion": candidate_version,
    "resolvedVersion": resolved_version,
    "java": java_specification,
    "expectedModules": ["jgit-storage-hibernate-core"],
    "forbiddenModules": [
        "jgit-storage-hibernate-search",
        "jgit-storage-hibernate-java-analysis",
        "jgit-storage-hibernate-architecture",
        "jgit-storage-hibernate-benchmarks",
    ],
    "contract": (
        "Core-only public API, OSGi bridge metadata, legacy preflight, "
        "repository lifecycle and shaded-server packaging"
    ),
}
Path(output).write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
PY

printf 'Sandbox jgit-storage-hibernate contract passed in %s mode with Core %s.\n' \
  "$mode" "$resolved_version"
