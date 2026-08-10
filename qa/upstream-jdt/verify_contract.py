#!/usr/bin/env python3
"""Fail-closed validation for the version-controlled upstream JDT QA contract."""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

XSI_TYPE = "{http://www.w3.org/2001/XMLSchema-instance}type"


def fail(message: str) -> None:
    raise ValueError(message)


def load_pins(path: Path) -> dict[str, str]:
    pins: dict[str, str] = {}
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            fail(f"{path}:{number}: expected NAME=value")
        key, value = line.split("=", 1)
        if not key.startswith("PIN_") or not value:
            fail(f"{path}:{number}: invalid pin")
        pins[key] = value
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
    if missing:
        fail(f"Missing pins: {', '.join(missing)}")
    for key in ("PIN_JDT_CORE_COMMIT", "PIN_JDT_UI_COMMIT", "PIN_JDT_CORE_BINARIES_COMMIT"):
        value = pins[key]
        if len(value) != 40 or any(character not in "0123456789abcdef" for character in value):
            fail(f"{key} is not a full lowercase Git commit id")
    return pins


def variable_values(root: ET.Element) -> dict[str, str]:
    result: dict[str, str] = {}
    for element in root.iter():
        if element.attrib.get(XSI_TYPE) == "setup:VariableTask" and "name" in element.attrib:
            result[element.attrib["name"]] = element.attrib.get("value", element.attrib.get("defaultValue", ""))
    return result


def git_clones(root: ET.Element) -> dict[str, ET.Element]:
    result: dict[str, ET.Element] = {}
    for element in root.iter():
        if element.attrib.get(XSI_TYPE) == "git:GitCloneTask":
            result[element.attrib.get("remoteURI", "")] = element
    return result


def validate_oomph(root: Path, pins: dict[str, str]) -> None:
    setup_path = root / "sandbox_oomph/jdt-migration-qa.setup"
    configuration_path = root / "sandbox_oomph/jdt-migration-qa.configuration.setup"
    setup = ET.parse(setup_path).getroot()
    configuration = ET.parse(configuration_path).getroot()

    variables = variable_values(setup)
    expected_variables = {
        "sandbox.qa.eclipse.release": pins["PIN_ECLIPSE_RELEASE"],
        "sandbox.qa.jdt.core.ref": pins["PIN_JDT_CORE_REF"],
        "sandbox.qa.jdt.core.commit": pins["PIN_JDT_CORE_COMMIT"],
        "sandbox.qa.jdt.ui.ref": pins["PIN_JDT_UI_REF"],
        "sandbox.qa.jdt.ui.commit": pins["PIN_JDT_UI_COMMIT"],
        "sandbox.qa.jdt.core.binaries.ref": pins["PIN_JDT_CORE_BINARIES_REF"],
        "sandbox.qa.jdt.core.binaries.commit": pins["PIN_JDT_CORE_BINARIES_COMMIT"],
    }
    for name, expected in expected_variables.items():
        if variables.get(name) != expected:
            fail(f"Oomph variable {name} does not match pins.env")

    clones = git_clones(setup)
    expected_clones = {
        pins["PIN_JDT_CORE_REPOSITORY"]: "${sandbox.qa.jdt.core.ref}",
        pins["PIN_JDT_UI_REPOSITORY"]: "${sandbox.qa.jdt.ui.ref}",
        pins["PIN_JDT_CORE_BINARIES_REPOSITORY"]: "${sandbox.qa.jdt.core.binaries.ref}",
    }
    for repository, checkout in expected_clones.items():
        clone = clones.get(repository)
        if clone is None:
            fail(f"Oomph setup does not clone {repository}")
        if clone.attrib.get("checkoutBranch") != checkout:
            fail(f"Oomph checkout for {repository} is not pinned through {checkout}")

    setup_text = setup_path.read_text(encoding="utf-8")
    for requirement in (
        "org.eclipse.jdt.apt.tests",
        "org.eclipse.jdt.core.tests.model",
        "org.eclipse.jdt.core.tests.binaries",
        "org.eclipse.jdt.ui.tests",
    ):
        if f'name="{requirement}"' not in setup_text:
            fail(f"Missing Oomph targlet requirement {requirement}")

    product_versions = [
        element.attrib.get("href", "")
        for element in configuration.iter()
        if element.tag.endswith("productVersion")
    ]
    expected_version_fragment = f"@versions[name='{pins['PIN_ECLIPSE_PLATFORM_VERSION']}']"
    if len(product_versions) != 1 or expected_version_fragment not in product_versions[0]:
        fail("Advanced Mode configuration is not fixed to Eclipse SDK 4.40")
    streams = [element.attrib.get("href", "") for element in configuration.iter() if element.tag.endswith("stream")]
    if "jdt-migration-qa.setup#//@streams[name='r4_40']" not in streams:
        fail("Advanced Mode configuration does not select the pinned QA stream")


def parse_properties(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if not separator:
            fail(f"Invalid property in {path}: {line}")
        result[key.strip()] = value.strip()
    return result


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
        passing = subprocess.run(
            [sys.executable, str(comparator), "--baseline", str(baseline), "--migrated", str(migrated),
             "--mapping", str(mapping), "--output", str(temporary_root / "pass.json")],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            check=False,
        )
        if passing.returncode != 0:
            fail(f"Comparator rejected an identical inventory:\n{passing.stdout}")
        (migrated / "TEST-sample.xml").unlink()
        write_report(migrated, tests[:1])
        failing = subprocess.run(
            [sys.executable, str(comparator), "--baseline", str(baseline), "--migrated", str(migrated),
             "--mapping", str(mapping), "--output", str(temporary_root / "fail.json")],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            check=False,
        )
        if failing.returncode == 0:
            fail("Comparator accepted a disappeared test")


def validate_runner(root: Path, pins: dict[str, str]) -> None:
    runner = root / "qa/upstream-jdt/run-before-after.sh"
    syntax = subprocess.run(["bash", "-n", str(runner)], check=False, text=True, capture_output=True)
    if syntax.returncode != 0:
        fail(f"Runner shell syntax is invalid: {syntax.stderr}")
    text = runner.read_text(encoding="utf-8")
    for required in (
        "sandbox_cleanup_application.org.sandbox.jdt.core.JavaCleanup",
        "CHECK_STATUS\" -eq 2",
        "test-inventory-comparison.json",
        "git -C \"$JDT_CORE\" reset --hard",
    ):
        if required not in text:
            fail(f"Runner is missing required fail-closed contract: {required}")
    properties = parse_properties(root / "qa/upstream-jdt/junit3-to-jupiter.properties")
    if properties != {
        "cleanup.junitcleanup": "false",
        "cleanup.junit3cleanup": "true",
        "cleanup.junitcleanup_3_test": "true",
    }:
        fail("JUnit 3 profile is broader than the documented first scenario")
    overlay = (root / "qa/upstream-jdt/overlays/jdt-core-r4_40-jupiter.patch").read_text(encoding="utf-8")
    if "org.junit.jupiter.api" not in overlay or "org.junit.platform.suite.api" not in overlay:
        fail("The identical baseline/migrated build overlay lacks Jupiter APIs")
    if pins["PIN_PRIMARY_PROJECT"] not in overlay:
        fail("Build overlay is not tied to the pinned primary project")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository-root", type=Path)
    args = parser.parse_args()
    root = (args.repository_root or Path(__file__).resolve().parents[2]).resolve()
    pins = load_pins(root / "qa/upstream-jdt/pins.env")
    validate_oomph(root, pins)
    validate_runner(root, pins)
    validate_comparator(root)
    summary = {
        "result": "PASS",
        "eclipseRelease": pins["PIN_ECLIPSE_RELEASE"],
        "eclipsePlatformVersion": pins["PIN_ECLIPSE_PLATFORM_VERSION"],
        "jdtCoreCommit": pins["PIN_JDT_CORE_COMMIT"],
        "jdtUiCommit": pins["PIN_JDT_UI_COMMIT"],
        "jdtCoreBinariesCommit": pins["PIN_JDT_CORE_BINARIES_COMMIT"],
        "primaryProject": pins["PIN_PRIMARY_PROJECT"],
    }
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ET.ParseError, OSError, ValueError) as exc:
        print(f"Upstream JDT QA contract validation failed: {exc}", file=sys.stderr)
        raise SystemExit(1) from exc
