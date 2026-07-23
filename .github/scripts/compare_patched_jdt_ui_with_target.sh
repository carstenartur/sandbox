#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
CONFIG_FILE=${PATCHED_JDT_UI_CONFIG:-"$ROOT_DIR/.github/patched-jdt-ui.env"}
PATCH_DIR=${1:?usage: compare_patched_jdt_ui_with_target.sh PATCH_DIR [REPORT_DIR]}
REPORT_DIR=${2:-"$ROOT_DIR/target/patched-jdt-ui-compatibility"}
MAVEN_REPOSITORY=${MAVEN_REPOSITORY:-"$REPORT_DIR/maven-repository"}

mkdir -p "$REPORT_DIR"
exec > >(tee "$REPORT_DIR/compatibility-build.log") 2>&1

# shellcheck disable=SC1090
source "$CONFIG_FILE"
: "${PATCHED_JDT_UI_BUNDLE:?missing PATCHED_JDT_UI_BUNDLE}"

for command in find java mvn python3; do
  command -v "$command" >/dev/null || { echo "Missing required command: $command" >&2; exit 1; }
done

mapfile -t patched_candidates < <(find "$PATCH_DIR/plugins" -maxdepth 1 -type f \
  -name "${PATCHED_JDT_UI_BUNDLE}_*.jar" | sort)
if (( ${#patched_candidates[@]} != 1 )); then
  printf 'Expected exactly one patched %s bundle below %s/plugins, found %d\n' \
    "$PATCHED_JDT_UI_BUNDLE" "$PATCH_DIR" "${#patched_candidates[@]}" >&2
  exit 1
fi
PATCHED_JAR=${patched_candidates[0]}

# Use an isolated local Maven repository. Scanning a shared Actions cache cannot
# prove which JDT UI version the checked-in target selected because stale target
# artifacts from earlier Eclipse releases may coexist there.
rm -rf "$MAVEN_REPOSITORY"
mkdir -p "$MAVEN_REPOSITORY"

mvn --batch-mode -ntp -f "$ROOT_DIR/pom.xml" \
  -Dmaven.repo.local="$MAVEN_REPOSITORY" \
  -pl sandbox_common -am \
  -DskipTests -DskipITs -Dspotbugs.skip=true -Dlicense.skip=true \
  package

# Tycho's p2 cache does not guarantee Maven-style artifact file names. Inspect
# manifests from every freshly resolved JAR and select the exact symbolic name.
CANDIDATE_LIST="$REPORT_DIR/resolved-jars.txt"
find "$MAVEN_REPOSITORY" -type f -name '*.jar' \
  ! -name '*-sources.jar' ! -name '*-javadoc.jar' -print | sort > "$CANDIDATE_LIST"
if [[ ! -s "$CANDIDATE_LIST" ]]; then
  echo "Tycho resolved no JAR artifacts into the isolated Maven repository" >&2
  exit 1
fi

python3 - "$PATCHED_JAR" "$CANDIDATE_LIST" "$REPORT_DIR" "$PATCHED_JDT_UI_BUNDLE" <<'PY'
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path

patched_path = Path(sys.argv[1])
candidate_paths = [Path(line) for line in Path(sys.argv[2]).read_text(encoding='utf-8').splitlines() if line]
report_dir = Path(sys.argv[3])
expected_symbolic_name = sys.argv[4]


def manifest(path: Path) -> dict[str, str]:
    with zipfile.ZipFile(path) as archive:
        raw = archive.read('META-INF/MANIFEST.MF').decode('utf-8')
    logical: list[str] = []
    for line in raw.splitlines():
        if line.startswith(' ') and logical:
            logical[-1] += line[1:]
        else:
            logical.append(line)
    headers: dict[str, str] = {}
    for line in logical:
        if ':' in line:
            key, value = line.split(':', 1)
            headers[key.strip()] = value.strip()
    return headers


def symbolic_name(headers: dict[str, str]) -> str:
    return headers.get('Bundle-SymbolicName', '').split(';', 1)[0].strip()


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def version_key(value: str) -> tuple[int, int, int, str]:
    parts = value.split('.', 3)
    if len(parts) < 3 or not all(part.isdigit() for part in parts[:3]):
        raise ValueError(f'Not an OSGi version: {value!r}')
    qualifier = parts[3] if len(parts) == 4 else ''
    return int(parts[0]), int(parts[1]), int(parts[2]), qualifier


def split_clauses(value: str) -> list[str]:
    if not value:
        return []
    result: list[str] = []
    current: list[str] = []
    quoted = False
    escaped = False
    for char in value:
        if escaped:
            current.append(char)
            escaped = False
        elif char == '\\':
            current.append(char)
            escaped = True
        elif char == '"':
            current.append(char)
            quoted = not quoted
        elif char == ',' and not quoted:
            clause = ''.join(current).strip()
            if clause:
                result.append(re.sub(r'\s+', ' ', clause))
            current = []
        else:
            current.append(char)
    clause = ''.join(current).strip()
    if clause:
        result.append(re.sub(r'\s+', ' ', clause))
    return sorted(result)


def clause_names(value: str) -> set[str]:
    return {clause.split(';', 1)[0].strip() for clause in split_clauses(value)}


patched_manifest = manifest(patched_path)
if symbolic_name(patched_manifest) != expected_symbolic_name:
    raise SystemExit(f'Patched bundle has unexpected symbolic name: {symbolic_name(patched_manifest)!r}')

stock: list[tuple[Path, dict[str, str]]] = []
for candidate in candidate_paths:
    try:
        headers = manifest(candidate)
    except (KeyError, UnicodeDecodeError, zipfile.BadZipFile):
        continue
    if symbolic_name(headers) == expected_symbolic_name:
        stock.append((candidate, headers))

versions = sorted({headers.get('Bundle-Version', '') for _, headers in stock})
if len(versions) != 1:
    locations = [str(path) for path, _ in stock]
    raise SystemExit(
        f'Expected exactly one freshly resolved stock {expected_symbolic_name} version, '
        f'found {versions}; matching artifacts={locations}'
    )
stock_version = versions[0]
stock_candidates = [(path, headers) for path, headers in stock if headers.get('Bundle-Version', '') == stock_version]
stock_path, stock_manifest = stock_candidates[0]
patched_version = patched_manifest.get('Bundle-Version', '')

checks: list[dict[str, object]] = []


def check(name: str, passed: bool, detail: str) -> None:
    checks.append({'name': name, 'passed': passed, 'detail': detail})


try:
    newer = version_key(patched_version) > version_key(stock_version)
except ValueError as error:
    newer = False
    check('replacement-version', False, str(error))
else:
    check('replacement-version', newer,
          f'patched {patched_version} must be strictly newer than stock {stock_version}')

for header in ('Bundle-RequiredExecutionEnvironment', 'Require-Bundle', 'Import-Package'):
    patched_value = split_clauses(patched_manifest.get(header, ''))
    stock_value = split_clauses(stock_manifest.get(header, ''))
    check(f'{header}-compatibility', patched_value == stock_value,
          'identical' if patched_value == stock_value else
          f'added={sorted(set(patched_value) - set(stock_value))}; removed={sorted(set(stock_value) - set(patched_value))}')

stock_exports = clause_names(stock_manifest.get('Export-Package', ''))
patched_exports = clause_names(patched_manifest.get('Export-Package', ''))
missing_exports = sorted(stock_exports - patched_exports)
check('Export-Package-coverage', not missing_exports,
      'all stock exports retained' if not missing_exports else f'missing={missing_exports}')

compatible = all(bool(item['passed']) for item in checks)
payload = {
    'schemaVersion': 1,
    'compatibleForReplacement': compatible,
    'target': 'sandbox_target/eclipse.target (Eclipse 2025-12)',
    'stockBundle': {
        'path': str(stock_path),
        'version': stock_version,
        'sha256': sha256(stock_path),
    },
    'patchedBundle': {
        'path': str(patched_path),
        'version': patched_version,
        'sha256': sha256(patched_path),
    },
    'checks': checks,
}
(report_dir / 'compatibility.json').write_text(json.dumps(payload, indent=2) + '\n', encoding='utf-8')

lines = [
    '# Patched JDT UI target compatibility',
    '',
    f'- Stock bundle: `{expected_symbolic_name} {stock_version}`',
    f'- Patched bundle: `{expected_symbolic_name} {patched_version}`',
    f'- Replacement gate: **{"PASS" if compatible else "BLOCKED"}**',
    '',
    '| Check | Result | Detail |',
    '|---|---:|---|',
]
for item in checks:
    detail = str(item['detail']).replace('|', '\\|').replace('\n', ' ')
    lines.append(f"| `{item['name']}` | {'PASS' if item['passed'] else 'FAIL'} | {detail} |")
(report_dir / 'compatibility.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')

print(json.dumps(payload, indent=2))
PY

python3 -m json.tool "$REPORT_DIR/compatibility.json" >/dev/null
