#!/usr/bin/env python3
"""Fail-closed validation for the version-controlled upstream JDT QA contract."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any

XSI_TYPE = "{http://www.w3.org/2001/XMLSchema-instance}type"
VARIABLE = re.compile(r"\$\{([^}]+)}")


def fail(message: str) -> None:
    raise ValueError(message)


def parse_key_values(path: Path, *, required_prefix: str | None = None) -> dict[str, str]:
    result: dict[str, str] = {}
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            fail(f"{path}:{number}: expected NAME=value")
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if not key or not value or required_prefix is not None and not key.startswith(required_prefix):
            fail(f"{path}:{number}: invalid key/value")
        if key in result:
            fail(f"{path}:{number}: duplicate key {key}")
        result[key] = value
    return result


def load_pins(path: Path) -> dict[str, str]:
    pins = parse_key_values(path, required_prefix="PIN_")
    required = {
        "PIN_ECLIPSE_RELEASE",
        "PIN_ECLIPSE_PLATFORM_VERSION",
        "PIN_JDT_CORE_REPOSITORY",
        "PIN_JDT_CORE_REF",
        "PIN_JDT_CORE_COMMIT",
        "PIN_JDT_UI_REPOSITORY",
        "PIN_JDT_UI_REF",
        "PIN_JDT_UI_COMMIT",
        "PIN_JDT_CORE_BINARIES_REPOSITORY",
        "PIN_JDT_CORE_BINARIES_REF",
        "PIN_JDT_CORE_BINARIES_COMMIT",
        "PIN_PRIMARY_PROJECT",
        "PIN_PRIMARY_SOURCE",
        "PIN_PRIMARY_TEST_POM",
    }
    missing = sorted(required - pins.keys())
    extra = sorted(pins.keys() - required)
    if missing or extra:
        fail(f"pins.env contract mismatch: missing={missing}, extra={extra}")
    for key in ("PIN_JDT_CORE_COMMIT", "PIN_JDT_UI_COMMIT", "PIN_JDT_CORE_BINARIES_COMMIT"):
        value = pins[key]
        if len(value) != 40 or any(character not in "0123456789abcdef" for character in value):
            fail(f"{key} is not a full lowercase Git commit id")
    return pins


def local_name(element: ET.Element) -> str:
    return element.tag.rsplit("}", 1)[-1]


def variable_values(root: ET.Element) -> dict[str, str]:
    result: dict[str, str] = {}
    for element in root.iter():
        if element.attrib.get(XSI_TYPE) == "setup:VariableTask" and "name" in element.attrib:
            result[element.attrib["name"]] = element.attrib.get("value", element.attrib.get("defaultValue", ""))
    return result


def resolve_variables(value: str, variables: dict[str, str]) -> str:
    previous = None
    current = value
    for _ in range(20):
        if current == previous:
            break
        previous = current
        current = VARIABLE.sub(lambda match: variables.get(match.group(1), match.group(0)), current)
    unresolved = VARIABLE.findall(current)
    if unresolved:
        fail(f"Unresolved Oomph variables in {value!r}: {unresolved}")
    return current


def resource_pin_map(setup: ET.Element, variables: dict[str, str]) -> dict[str, str]:
    for task in setup.iter():
        if task.attrib.get(XSI_TYPE) != "setup:ResourceCreationTask":
            continue
        if not task.attrib.get("targetURL", "").endswith("/.sandbox-jdt-migration-qa-pins.env"):
            continue
        content = next((child for child in task if local_name(child) == "content"), None)
        if content is None or content.text is None:
            fail("Oomph pin ResourceCreationTask has no content")
        resolved = resolve_variables(content.text, variables)
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "pins.env"
            path.write_text(resolved, encoding="utf-8")
            return parse_key_values(path, required_prefix="PIN_")
    fail("Oomph setup has no workspace pin ResourceCreationTask")


def validate_oomph(root: Path, pins: dict[str, str]) -> None:
    setup_path = root / "sandbox_oomph/jdt-migration-qa.setup"
    configuration_path = root / "sandbox_oomph/jdt-migration-qa.configuration.setup"
    setup = ET.parse(setup_path).getroot()
    configuration = ET.parse(configuration_path).getroot()

    variables = variable_values(setup)
    expected_variables = {
        "sandbox.qa.eclipse.release": pins["PIN_ECLIPSE_RELEASE"],
        "sandbox.qa.eclipse.platform.version": pins["PIN_ECLIPSE_PLATFORM_VERSION"],
        "sandbox.qa.jdt.core.repository": pins["PIN_JDT_CORE_REPOSITORY"],
        "sandbox.qa.jdt.core.ref": pins["PIN_JDT_CORE_REF"],
        "sandbox.qa.jdt.core.commit": pins["PIN_JDT_CORE_COMMIT"],
        "sandbox.qa.jdt.ui.repository": pins["PIN_JDT_UI_REPOSITORY"],
        "sandbox.qa.jdt.ui.ref": pins["PIN_JDT_UI_REF"],
        "sandbox.qa.jdt.ui.commit": pins["PIN_JDT_UI_COMMIT"],
        "sandbox.qa.jdt.core.binaries.repository": pins["PIN_JDT_CORE_BINARIES_REPOSITORY"],
        "sandbox.qa.jdt.core.binaries.ref": pins["PIN_JDT_CORE_BINARIES_REF"],
        "sandbox.qa.jdt.core.binaries.commit": pins["PIN_JDT_CORE_BINARIES_COMMIT"],
        "sandbox.qa.primary.project": pins["PIN_PRIMARY_PROJECT"],
        "sandbox.qa.primary.source": pins["PIN_PRIMARY_SOURCE"],
        "sandbox.qa.primary.test.pom": pins["PIN_PRIMARY_TEST_POM"],
    }
    for name, expected in expected_variables.items():
        if variables.get(name) != expected:
            fail(f"Oomph variable {name}={variables.get(name)!r}, expected {expected!r}")

    clone_contract = {
        pins["PIN_JDT_CORE_REPOSITORY"]: pins["PIN_JDT_CORE_REF"],
        pins["PIN_JDT_UI_REPOSITORY"]: pins["PIN_JDT_UI_REF"],
        pins["PIN_JDT_CORE_BINARIES_REPOSITORY"]: pins["PIN_JDT_CORE_BINARIES_REF"],
    }
    observed: dict[str, str] = {}
    for element in setup.iter():
        if element.attrib.get(XSI_TYPE) != "git:GitCloneTask":
            continue
        remote = resolve_variables(element.attrib.get("remoteURI", ""), variables)
        checkout = resolve_variables(element.attrib.get("checkoutBranch", ""), variables)
        observed[remote] = checkout
    for repository, expected_ref in clone_contract.items():
        if observed.get(repository) != expected_ref:
            fail(f"Oomph checkout for {repository} is {observed.get(repository)!r}, expected {expected_ref!r}")

    generated_pins = resource_pin_map(setup, variables)
    if generated_pins != pins:
        missing = sorted(pins.keys() - generated_pins.keys())
        extra = sorted(generated_pins.keys() - pins.keys())
        changed = sorted(key for key in pins.keys() & generated_pins.keys() if pins[key] != generated_pins[key])
        fail(f"Oomph-generated PIN_* map differs: missing={missing}, extra={extra}, changed={changed}")

    setup_text = setup_path.read_text(encoding="utf-8")
    for requirement in (
        "org.eclipse.jdt.apt.tests",
        "org.eclipse.jdt.core.tests.model",
        "org.eclipse.jdt.core.tests.binaries",
        "org.eclipse.jdt.ui.tests",
    ):
        if f'name="{requirement}"' not in setup_text:
            fail(f"Missing Oomph targlet requirement {requirement}")
    if "ProjectsBuildTask" not in setup_text:
        fail("Oomph setup does not build the imported workspace")

    product_versions = [
        element.attrib.get("href", "")
        for element in configuration.iter()
        if local_name(element) == "productVersion"
    ]
    expected_version_fragment = f"@versions[name='{pins['PIN_ECLIPSE_PLATFORM_VERSION']}']"
    if len(product_versions) != 1 or expected_version_fragment not in product_versions[0]:
        fail("Advanced Mode configuration is not fixed to the pinned Eclipse SDK")
    streams = [
        element.attrib.get("href", "")
        for element in configuration.iter()
        if local_name(element) == "stream"
    ]
    if "jdt-migration-qa.setup#//@streams[name='r4_40']" not in streams:
        fail("Advanced Mode configuration does not select the pinned QA stream")


def parse_properties(path: Path) -> dict[str, str]:
    return parse_key_values(path)


def run(command: list[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        command,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
    )


def write_report(directory: Path, tests: list[tuple[str, str]]) -> None:
    cases = "".join(f'<testcase classname="{owner}" name="{name}"/>' for owner, name in tests)
    (directory / "TEST-sample.xml").write_text(
        f'<?xml version="1.0" encoding="UTF-8"?><testsuite name="sample">{cases}</testsuite>',
        encoding="utf-8",
    )


def validate_comparator(root: Path) -> None:
    comparator = root / "qa/upstream-jdt/compare_test_inventory.py"
    mapping = root / "qa/upstream-jdt/expected-test-mapping.json"
    with tempfile.TemporaryDirectory() as temporary:
        temporary_root = Path(temporary)
        baseline = temporary_root / "baseline"
        migrated = temporary_root / "migrated"
        baseline.mkdir()
        migrated.mkdir()
        tests = [("example.SampleTest", "testOne"), ("example.SampleTest", "testTwo")]
        write_report(baseline, tests)
        write_report(migrated, tests)
        passing = run([
            sys.executable,
            str(comparator),
            "--baseline",
            str(baseline),
            "--migrated",
            str(migrated),
            "--mapping",
            str(mapping),
            "--output",
            str(temporary_root / "pass.json"),
        ])
        if passing.returncode != 0:
            fail(f"Comparator rejected an identical inventory:\n{passing.stdout}")
        (migrated / "TEST-sample.xml").unlink()
        write_report(migrated, tests[:1])
        failing = run([
            sys.executable,
            str(comparator),
            "--baseline",
            str(baseline),
            "--migrated",
            str(migrated),
            "--mapping",
            str(mapping),
            "--output",
            str(temporary_root / "fail.json"),
        ])
        if failing.returncode == 0:
            fail("Comparator accepted a disappeared test")


def load_json_object(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        fail(f"{path} does not contain a JSON object")
    return value


def validate_corpus_contract(root: Path, pins: dict[str, str]) -> None:
    contract_path = root / "qa/upstream-jdt/expected-corpus.json"
    verifier = root / "qa/upstream-jdt/verify_corpus_result.py"
    contract = load_json_object(contract_path)
    expected_files = {
        f"{pins['PIN_PRIMARY_PROJECT']}/src/org/eclipse/jdt/apt/tests/FactoryPathTests.java",
        f"{pins['PIN_PRIMARY_PROJECT']}/src/org/eclipse/jdt/apt/tests/TestAll.java",
    }
    required_files = contract.get("requiredFiles")
    if not isinstance(required_files, dict) or set(required_files) != expected_files:
        fail(f"Real-corpus contract must name exactly {sorted(expected_files)}")
    if int(contract.get("minimumChangedJavaFiles", 0)) < len(expected_files):
        fail("Real-corpus contract permits fewer changed Java files than its named examples")
    if contract.get("requirePlanningDiagnostics") is not True:
        fail("Real-corpus contract does not require structured planning diagnostics")

    with tempfile.TemporaryDirectory() as temporary:
        temporary_root = Path(temporary)
        repository = temporary_root / "repository"
        changed = temporary_root / "changed-files.txt"
        check_report = temporary_root / "check.json"
        apply_report = temporary_root / "apply.json"
        output = temporary_root / "result.json"
        changed_entries: list[str] = []
        report_entries: list[str] = []
        for relative, rules in sorted(required_files.items()):
            if not isinstance(rules, dict):
                fail(f"Invalid corpus rules for {relative}")
            must_contain = rules.get("mustContain", [])
            must_not_contain = rules.get("mustNotContain", [])
            if (
                not isinstance(must_contain, list)
                or not must_contain
                or any(not isinstance(item, str) or not item for item in must_contain)
                or not isinstance(must_not_contain, list)
                or not must_not_contain
                or any(not isinstance(item, str) or not item for item in must_not_contain)
            ):
                fail(f"Corpus rules for {relative} must contain non-empty string lists")
            source = repository / relative
            source.parent.mkdir(parents=True, exist_ok=True)
            source.write_text("\n".join(must_contain) + "\n", encoding="utf-8")
            changed_entries.append(relative)
            prefix = pins["PIN_PRIMARY_PROJECT"] + "/"
            report_entries.append(relative[len(prefix) :] if relative.startswith(prefix) else relative)
        changed.write_text("\n".join(changed_entries) + "\n", encoding="utf-8")
        diagnostics = [{"cleanupId": "junit", "candidates": []}]
        common = {
            "schemaVersion": "1",
            "tool": "sandbox-project-cleanup",
            "project": pins["PIN_PRIMARY_PROJECT"],
            "filesProcessed": 25,
            "filesChanged": len(report_entries),
            "changedFiles": report_entries,
            "planningDiagnostics": diagnostics,
            "errorCount": 0,
            "errors": [],
        }
        check_report.write_text(
            json.dumps({**common, "mode": "check"}, indent=2) + "\n",
            encoding="utf-8",
        )
        apply_report.write_text(
            json.dumps({**common, "mode": "apply"}, indent=2) + "\n",
            encoding="utf-8",
        )
        command = [
            sys.executable,
            str(verifier),
            "--repository",
            str(repository),
            "--project",
            pins["PIN_PRIMARY_PROJECT"],
            "--contract",
            str(contract_path),
            "--changed-files",
            str(changed),
            "--check-report",
            str(check_report),
            "--apply-report",
            str(apply_report),
            "--output",
            str(output),
        ]
        passing = run(command)
        if passing.returncode != 0:
            fail(f"Corpus verifier rejected valid evidence:\n{passing.stdout}")
        first_relative = sorted(required_files)[0]
        forbidden = required_files[first_relative]["mustNotContain"][0]
        with (repository / first_relative).open("a", encoding="utf-8") as stream:
            stream.write(forbidden + "\n")
        failing = run(command)
        if failing.returncode == 0:
            fail("Corpus verifier accepted a required file that retained forbidden JUnit 3 text")


def validate_application(root: Path) -> None:
    plugin = (root / "sandbox_cleanup_application/plugin.xml").read_text(encoding="utf-8")
    source = (
        root
        / "sandbox_cleanup_application/src/org/sandbox/jdt/core/cleanupapp/ProjectWideCodeCleanupApplication.java"
    ).read_text(encoding="utf-8")
    manifest = (root / "sandbox_cleanup_application/META-INF/MANIFEST.MF").read_text(encoding="utf-8")
    for required in (
        'id="org.sandbox.jdt.core.ProjectWideJavaCleanup"',
        'class="org.sandbox.jdt.core.cleanupapp.ProjectWideCodeCleanupApplication"',
    ):
        if required not in plugin:
            fail(f"Project-wide application registration is missing {required}")
    for required in (
        "new CleanUpRefactoring()",
        "refactoring.addCompilationUnit(source.unit())",
        'case "--project-location"',
        "IMultiFileCleanUpDiagnosticsProvider",
        "restore(sources, monitor, errors)",
        'report.put',
    ):
        if required not in source:
            fail(f"Project-wide application is missing contract fragment: {required}")
    if "sandbox_common_core" not in manifest:
        fail("Cleanup application does not require the diagnostics API bundle")


def validate_runner(root: Path, pins: dict[str, str]) -> None:
    runner = root / "qa/upstream-jdt/run-before-after.sh"
    syntax = run(["bash", "-n", str(runner)])
    if syntax.returncode != 0:
        fail(f"Runner shell syntax is invalid:\n{syntax.stdout}")
    text = runner.read_text(encoding="utf-8")
    for required in (
        "sandbox_cleanup_application.org.sandbox.jdt.core.ProjectWideJavaCleanup",
        'CHECK_STATUS" -eq 2',
        "--project-location",
        "verify_corpus_result.py",
        "corpus-result.json",
        'git -C "$JDT_CORE" apply --index',
        'git -C "$JDT_CORE" reset --hard',
        ".sandbox-jdt-migration-qa-pins.env",
        "--allow-clean-workspace",
        "test-inventory-comparison.json",
    ):
        if required not in text:
            fail(f"Runner is missing required fail-closed contract: {required}")

    properties = parse_properties(root / "qa/upstream-jdt/junit3-to-jupiter.properties")
    expected_properties = {
        "cleanup.junitcleanup": "true",
        "cleanup.junit3cleanup": "true",
        "cleanup.junitcleanup_3_test": "true",
        "cleanup.junitcleanup_4_suite": "true",
    }
    if properties != expected_properties:
        fail(f"JUnit 3 real-corpus profile differs: {properties!r}")

    overlay = (root / "qa/upstream-jdt/overlays/jdt-core-r4_40-jupiter.patch").read_text(encoding="utf-8")
    if "org.junit.jupiter.api" not in overlay or "org.junit.platform.suite.api" not in overlay:
        fail("The identical baseline/migrated build overlay lacks Jupiter APIs")
    if pins["PIN_PRIMARY_PROJECT"] not in overlay:
        fail("Build overlay is not tied to the pinned primary project")


def validate_workflow(root: Path) -> None:
    workflow = (root / ".github/workflows/upstream-jdt-migration-qa.yml").read_text(encoding="utf-8")
    for required in (
        "workflow_dispatch:",
        "run_full_migration:",
        "run-before-after.sh",
        "--allow-clean-workspace",
        "upstream-jdt-migration-evidence",
        "actions/upload-artifact",
    ):
        if required not in workflow:
            fail(f"Manual upstream workflow is missing {required}")


def validate_python_syntax(root: Path) -> None:
    scripts = [
        root / "qa/upstream-jdt/compare_test_inventory.py",
        root / "qa/upstream-jdt/verify_corpus_result.py",
        root / "qa/upstream-jdt/verify_contract.py",
    ]
    result = run([sys.executable, "-m", "py_compile", *(str(script) for script in scripts)])
    if result.returncode != 0:
        fail(f"Python QA script compilation failed:\n{result.stdout}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository-root", type=Path)
    args = parser.parse_args()
    root = (args.repository_root or Path(__file__).resolve().parents[2]).resolve()
    pins = load_pins(root / "qa/upstream-jdt/pins.env")
    validate_python_syntax(root)
    validate_oomph(root, pins)
    validate_application(root)
    validate_runner(root, pins)
    validate_comparator(root)
    validate_corpus_contract(root, pins)
    validate_workflow(root)
    summary = {
        "result": "PASS",
        "eclipseRelease": pins["PIN_ECLIPSE_RELEASE"],
        "eclipsePlatformVersion": pins["PIN_ECLIPSE_PLATFORM_VERSION"],
        "jdtCoreCommit": pins["PIN_JDT_CORE_COMMIT"],
        "jdtUiCommit": pins["PIN_JDT_UI_COMMIT"],
        "jdtCoreBinariesCommit": pins["PIN_JDT_CORE_BINARIES_COMMIT"],
        "primaryProject": pins["PIN_PRIMARY_PROJECT"],
        "namedCorpusFiles": 2,
        "projectWideApplication": "org.sandbox.jdt.core.ProjectWideJavaCleanup",
    }
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ET.ParseError, OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"Upstream JDT QA contract validation failed: {exc}", file=sys.stderr)
        raise SystemExit(1) from exc
