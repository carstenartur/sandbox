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

# The two consumer modules are intentionally built in isolation from the unrelated
# Eclipse cleanup reactor. Install the root parent first, then verify the OSGi/plain-
# Maven bridge and the shaded standalone server with their own tests.
set -o pipefail
"${maven[@]}" -B -ntp -nsu -N -f pom.xml install 2>&1 \
  | tee "$evidence_dir/parent-install.log"

"${maven[@]}" -B -ntp -nsu \
  -f sandbox-jgit-storage-hibernate/pom.xml \
  install 2>&1 | tee "$evidence_dir/storage-bridge.log"

"${maven[@]}" -B -ntp -nsu \
  -f sandbox-jgit-server-webapp/pom.xml \
  package 2>&1 | tee "$evidence_dir/server-package.log"

manifest=sandbox-jgit-storage-hibernate/target/classes/META-INF/MANIFEST.MF
server_jar=sandbox-jgit-server-webapp/target/jgit-server.jar
for artifact in "$manifest" "$server_jar"; do
  if [[ ! -s "$artifact" ]]; then
    echo "Required Sandbox packaging evidence is missing: $artifact" >&2
    exit 1
  fi
done

grep -Fq 'Bundle-SymbolicName:' "$manifest"
jar tf "$server_jar" | grep -Fq \
  'org/eclipse/jgit/server/config/LegacyCoreSchemaPreflight.class'
jar tf "$server_jar" | grep -Fq \
  'io/github/carstenartur/jgit/storage/hibernate/DefaultHibernateRepositoryFactory.class'

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

if grep -Eq \
  'jgit-storage-hibernate-(search|java-analysis|architecture|benchmarks)' \
  "$evidence_dir/dependency-tree.txt"; then
  echo "Sandbox currently consumes upstream Core only; another upstream module leaked in." >&2
  cat "$evidence_dir/dependency-tree.txt" >&2
  exit 1
fi
if ! grep -Fq 'jgit-storage-hibernate-core' "$evidence_dir/dependency-tree.txt"; then
  echo "The Sandbox contract did not resolve jgit-storage-hibernate-core." >&2
  exit 1
fi
if [[ "$mode" == "candidate" ]] \
    && ! grep -Fq ":$candidate_version" "$evidence_dir/dependency-tree.txt"; then
  echo "Sandbox did not resolve candidate $candidate_version." >&2
  cat "$evidence_dir/dependency-tree.txt" >&2
  exit 1
fi

find sandbox-jgit-storage-hibernate/target/surefire-reports \
     sandbox-jgit-server-webapp/target/surefire-reports \
     -type f -name 'TEST-*.xml' -print 2>/dev/null \
  | sort > "$evidence_dir/test-reports.txt"
if [[ ! -s "$evidence_dir/test-reports.txt" ]]; then
  echo "The Sandbox storage modules produced no test reports." >&2
  exit 1
fi

cat > "$evidence_dir/result.json" <<EOF
{
  "consumer": "sandbox",
  "mode": "$mode",
  "candidateVersion": "$candidate_version",
  "java": "$java_specification",
  "contract": "Core-only OSGi bridge, legacy preflight, repository lifecycle and shaded-server packaging"
}
EOF

printf 'Sandbox jgit-storage-hibernate contract passed in %s mode.\n' "$mode"
