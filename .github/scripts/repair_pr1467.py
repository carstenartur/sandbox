from __future__ import annotations

import re
from pathlib import Path


def read(path: str) -> str:
    return Path(path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    Path(path).write_text(text, encoding="utf-8")


def replace_once_text(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one exact match, found {count}")
    return text.replace(old, new, 1)


def sub_once_text(text: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(
        pattern,
        lambda _match: replacement,
        text,
        count=1,
        flags=re.MULTILINE | re.DOTALL,
    )
    if count != 1:
        raise SystemExit(f"{label}: expected one regex match, found {count}: {pattern}")
    return updated


def update_section(
    text: str,
    start_marker: str,
    end_marker: str,
    transform,
    label: str,
) -> str:
    start = text.find(start_marker)
    if start < 0:
        raise SystemExit(f"{label}: start marker missing: {start_marker}")
    end = text.find(end_marker, start)
    if end < 0:
        raise SystemExit(f"{label}: end marker missing: {end_marker}")
    section = text[start:end]
    updated = transform(section)
    if updated == section:
        raise SystemExit(f"{label}: section was not changed")
    return text[:start] + updated + text[end:]


def patch_runner() -> None:
    path = "qa/upstream-jdt/run-before-after.sh"
    text = read(path)
    text = replace_once_text(
        text,
        "  done < <(find \"$JDT_CORE\" -type f -path '*/target/surefire-reports/*.xml' -print0)\n"
        "  ((count > 0)) || fail \"No Surefire XML reports were produced\"",
        "  done < <(find \"$JDT_CORE/$PRIMARY_TEST_MODULE/target/surefire-reports\" \\\n"
        "    -type f -name '*.xml' -print0 2>/dev/null)\n"
        "  ((count > 0)) || fail \"The pinned primary test module produced no Surefire XML reports\"",
        f"{path}: report scope",
    )
    replacement = r'''prepare_test_reactor() {
  local tmp="$OUTPUT/tmp/dependencies"
  mkdir -p "$tmp"
  local -a maven_args=(
    --batch-mode
    --no-transfer-progress
    -U
    -DskipTests
    -Djava.io.tmpdir="$tmp"
    -f pom.xml
    --projects "$PRIMARY_TEST_MODULE"
    --also-make
    clean install
  )
  printf '%q ' "$MAVEN_BIN" "${maven_args[@]}" > "$OUTPUT/dependency-build-command.txt"
  printf '\n' >> "$OUTPUT/dependency-build-command.txt"
  local -a display_prefix=()
  if [[ -z "${DISPLAY:-}" ]] && command -v xvfb-run >/dev/null; then
    display_prefix=(xvfb-run --auto-servernum)
  fi
  (
    cd "$JDT_CORE"
    "${display_prefix[@]}" "$MAVEN_BIN" "${maven_args[@]}"
  ) 2>&1 | tee "$OUTPUT/logs/dependency-build-maven.log"
}

run_tests() {
  local phase=$1 destination=$2
  local tmp="$OUTPUT/tmp/$phase"
  mkdir -p "$tmp"
  local -a maven_args=(
    --batch-mode
    --no-transfer-progress
    -U
    -Djava.io.tmpdir="$tmp"
    -f pom.xml
    --projects "$PRIMARY_TEST_MODULE"
    clean verify
  )
  printf '%q ' "$MAVEN_BIN" "${maven_args[@]}" > "$destination-command.txt"
  printf '\n' >> "$destination-command.txt"
  local -a display_prefix=()
  if [[ -z "${DISPLAY:-}" ]] && command -v xvfb-run >/dev/null; then
    display_prefix=(xvfb-run --auto-servernum)
  fi
  (
    cd "$JDT_CORE"
    "${display_prefix[@]}" "$MAVEN_BIN" "${maven_args[@]}"
  ) 2>&1 | tee "$OUTPUT/logs/$phase-maven.log"
  copy_reports "$destination"
}

run_cleanup() {'''
    text = sub_once_text(
        text,
        r"^run_tests\(\) \{\n.*?^\}\n\nrun_cleanup\(\) \{",
        replacement,
        f"{path}: Maven phases",
    )
    text = replace_once_text(
        text,
        "printf 'BASELINE_TESTS\\n' > \"$OUTPUT/run-state.txt\"\n"
        "copy_corpus_sources \"$OUTPUT/corpus/baseline\"\n"
        "run_tests baseline \"$OUTPUT/baseline\"",
        "printf 'PREPARING_TEST_REACTOR\\n' > \"$OUTPUT/run-state.txt\"\n"
        "prepare_test_reactor\n\n"
        "printf 'BASELINE_TESTS\\n' > \"$OUTPUT/run-state.txt\"\n"
        "copy_corpus_sources \"$OUTPUT/corpus/baseline\"\n"
        "run_tests baseline \"$OUTPUT/baseline\"",
        f"{path}: dependency preparation call",
    )
    write(path, text)


def patch_workflow() -> None:
    path = ".github/workflows/upstream-jdt-migration-qa.yml"
    text = read(path)
    replacement = r'''      - name: Prepare pinned JDT reactor dependencies
        working-directory: upstream/eclipse.jdt.core
        shell: bash
        run: |
          set -euo pipefail
          source "$GITHUB_WORKSPACE/sandbox/qa/upstream-jdt/pins.env"
          primary_module=${PIN_PRIMARY_TEST_POM%/pom.xml}
          test "$primary_module" != "$PIN_PRIMARY_TEST_POM"
          xvfb-run --auto-servernum mvn \
            --batch-mode \
            --no-transfer-progress \
            -U \
            -DskipTests \
            -Djava.io.tmpdir="${{ runner.temp }}/jdt-core-qa/dependencies" \
            -f pom.xml \
            --projects "$primary_module" \
            --also-make \
            clean install

      - name: Run the pinned APT baseline tests
        working-directory: upstream/eclipse.jdt.core
        shell: bash
        run: |
          set -euo pipefail
          source "$GITHUB_WORKSPACE/sandbox/qa/upstream-jdt/pins.env"
          primary_module=${PIN_PRIMARY_TEST_POM%/pom.xml}
          test "$primary_module" != "$PIN_PRIMARY_TEST_POM"
          xvfb-run --auto-servernum mvn \
            --batch-mode \
            --no-transfer-progress \
            -U \
            -Djava.io.tmpdir="${{ runner.temp }}/jdt-core-qa/primary" \
            -f pom.xml \
            --projects "$primary_module" \
            clean verify

      - name: Require baseline JUnit XML evidence'''
    text = sub_once_text(
        text,
        r"^      - name: Run the pinned upstream baseline tests\n.*?^      - name: Require baseline JUnit XML evidence",
        replacement,
        f"{path}: baseline Maven phases",
    )
    text = replace_once_text(
        text,
        "          mapfile -d '' reports < <(find upstream/eclipse.jdt.core -type f \\\n"
        "            -path '*/target/surefire-reports/*.xml' -print0)\n"
        "          if (( ${#reports[@]} == 0 )); then",
        "          source sandbox/qa/upstream-jdt/pins.env\n"
        "          primary_module=${PIN_PRIMARY_TEST_POM%/pom.xml}\n"
        "          mapfile -d '' reports < <(find \\\n"
        "            \"upstream/eclipse.jdt.core/$primary_module/target/surefire-reports\" \\\n"
        "            -type f -name '*.xml' -print0 2>/dev/null)\n"
        "          if (( ${#reports[@]} == 0 )); then",
        f"{path}: report scope",
    )
    write(path, text)


def patch_contract() -> None:
    path = "qa/upstream-jdt/verify_contract.py"
    text = read(path)

    def update_runner(section: str) -> str:
        section = replace_once_text(
            section,
            '        "--also-make",\n',
            '        "--also-make",\n'
            '        "prepare_test_reactor",\n'
            '        "-DskipTests",\n'
            '        "PRIMARY_TEST_MODULE/target/surefire-reports",\n',
            f"{path}: runner required fragments",
        )
        section = replace_once_text(
            section,
            '    if re.search(r\'-f\\s+"?\\$PIN_PRIMARY_TEST_POM"?\\s+clean\\s+verify\', text):\n'
            '        fail("Runner invokes the primary child POM in isolation instead of the JDT reactor")\n\n'
            '    properties = parse_properties(root / "qa/upstream-jdt/junit3-to-jupiter.properties")',
            '    if re.search(r\'-f\\s+"?\\$PIN_PRIMARY_TEST_POM"?\\s+clean\\s+verify\', text):\n'
            '        fail("Runner invokes the primary child POM in isolation instead of the JDT reactor")\n\n'
            '    prepare_match = re.search(r"(?ms)^prepare_test_reactor\\(\\) \\{\\n(.*?)^\\}\\n", text)\n'
            '    if prepare_match is None:\n'
            '        fail("Runner has no dependency preparation phase")\n'
            '    prepare_body = prepare_match.group(1)\n'
            '    for required in ("-DskipTests", "--also-make", "clean install"):\n'
            '        if required not in prepare_body:\n'
            '            fail(f"Dependency preparation is missing {required}")\n'
            '    test_match = re.search(r"(?ms)^run_tests\\(\\) \\{\\n(.*?)^\\}\\n", text)\n'
            '    if test_match is None:\n'
            '        fail("Runner has no selected-module test phase")\n'
            '    test_body = test_match.group(1)\n'
            '    if "clean verify" not in test_body or "--also-make" in test_body or "-DskipTests" in test_body:\n'
            '        fail("Before/after tests must execute only the pinned primary module")\n\n'
            '    properties = parse_properties(root / "qa/upstream-jdt/junit3-to-jupiter.properties")',
            f"{path}: runner phase validation",
        )
        return section

    text = update_section(
        text,
        "def validate_runner(root: Path, pins: dict[str, str]) -> None:",
        "\ndef validate_workflow(root: Path) -> None:",
        update_runner,
        f"{path}: validate_runner",
    )

    def update_workflow(section: str) -> str:
        section = replace_once_text(
            section,
            '        "--also-make",\n',
            '        "--also-make",\n'
            '        "Prepare pinned JDT reactor dependencies",\n'
            '        "Run the pinned APT baseline tests",\n'
            '        "-DskipTests",\n'
            '        "clean install",\n',
            f"{path}: workflow required fragments",
        )
        section = replace_once_text(
            section,
            '    if "-f org.eclipse.jdt.apt.tests/pom.xml" in workflow:\n'
            '        fail("Manual baseline workflow invokes the APT child POM in isolation")',
            '    if "-f org.eclipse.jdt.apt.tests/pom.xml" in workflow:\n'
            '        fail("Manual baseline workflow invokes the APT child POM in isolation")\n'
            '    prepare_marker = "      - name: Prepare pinned JDT reactor dependencies"\n'
            '    baseline_marker = "      - name: Run the pinned APT baseline tests"\n'
            '    prepare_step = workflow.split(prepare_marker, 1)[1].split("\\n      - name:", 1)[0]\n'
            '    baseline_step = workflow.split(baseline_marker, 1)[1].split("\\n      - name:", 1)[0]\n'
            '    if not all(fragment in prepare_step for fragment in ("-DskipTests", "--also-make", "clean install")):\n'
            '        fail("Manual baseline workflow does not prepare dependencies without executing their tests")\n'
            '    if "clean verify" not in baseline_step or "--also-make" in baseline_step or "-DskipTests" in baseline_step:\n'
            '        fail("Manual baseline workflow does not isolate the pinned APT test module")',
            f"{path}: workflow phase validation",
        )
        return section

    text = update_section(
        text,
        "def validate_workflow(root: Path) -> None:",
        "\ndef validate_python_syntax(root: Path) -> None:",
        update_workflow,
        f"{path}: validate_workflow",
    )
    write(path, text)


def patch_readme() -> None:
    path = "qa/upstream-jdt/README.md"
    text = read(path)
    text = replace_once_text(
        text,
        "3. applies and locally commits the identical Jupiter build overlay;",
        "3. applies and locally commits the identical Jupiter build overlay, then builds and installs the selected reactor dependencies once with upstream tests skipped;",
        f"{path}: dependency preparation description",
    )
    text = replace_once_text(
        text,
        "5. runs the pinned `org.eclipse.jdt.apt.tests` Maven test command under Xvfb;\n"
        "6. saves every Surefire XML report as the baseline inventory;",
        "5. runs only the pinned `org.eclipse.jdt.apt.tests` Maven test command under Xvfb;\n"
        "6. saves only that module's Surefire XML reports as the baseline inventory;",
        f"{path}: baseline scope description",
    )
    text = replace_once_text(
        text,
        "13. runs exactly the same Maven test command again;",
        "13. runs exactly the same selected-module Maven test command again;",
        f"{path}: migrated scope description",
    )
    text = replace_once_text(
        text,
        "baseline/                         original JUnit XML reports\n",
        "dependency-build-command.txt      one-time reactor dependency preparation\n"
        "baseline/                         original APT-module JUnit XML reports\n",
        f"{path}: evidence layout",
    )
    write(path, text)


patch_runner()
patch_workflow()
patch_contract()
patch_readme()
