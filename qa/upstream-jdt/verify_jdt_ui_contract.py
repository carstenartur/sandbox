#!/usr/bin/env python3
"""Fail closed when the dedicated JDT UI JUnit 4 QA contract drifts."""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
QA = ROOT / "qa/upstream-jdt"
PINNED_COMMIT = "c922f757b27b7e2b6215db383cec5f8aafd13227"
PROJECT = "org.eclipse.jdt.ui.tests"
EXPECTED_FILES = {
    "org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/core/rules/JUnitSourceSetup.java",
    "org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/core/rules/LeakTestSetup.java",
    "org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/search/FileAdapterTest.java",
    "org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/search/SearchLeakTestWrapper.java",
    "org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/quickfix/ConvertLoopOperationTest.java",
}


def fail(message: str) -> None:
    raise ValueError(message)


def load_object(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        fail(f"{path} does not contain a JSON object")
    return value


def properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            fail(f"{path}:{number}: expected key=value")
        key, value = line.split("=", 1)
        if not key or not value or key in values:
            fail(f"{path}:{number}: invalid or duplicate property")
        values[key] = value
    return values


def run(command: list[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        command,
        cwd=ROOT,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
    )


def validate_sources() -> None:
    for relative in (
        "qa/upstream-jdt/verify_jdt_ui_corpus.py",
        "qa/upstream-jdt/verify_jdt_ui_contract.py",
    ):
        source = (ROOT / relative).read_text(encoding="utf-8")
        compile(source, relative, "exec")
    shell = run(["bash", "-n", "qa/upstream-jdt/run-jdt-ui-before-after.sh"])
    if shell.returncode != 0:
        fail(f"JDT UI runner has invalid shell syntax:\n{shell.stdout}")


def write_report(path: Path, mode: str, changed: list[str], *, best_effort: bool) -> None:
    project_relative = [item[len(PROJECT) + 1 :] for item in changed]
    diagnostics: Any = [{"cleanupId": "junit", "candidates": []}]
    if best_effort:
        diagnostics = {
            "bestEffort": True,
            "manualCompletionRequired": True,
            "gaps": [
                {
                    "candidateId": "parameterized:ConvertLoopOperationTest",
                    "reasonCode": "PARAMETERIZED_FIELD_INJECTION",
                    "explanation": "Synthetic contract evidence",
                    "remediation": "Use explicit Jupiter method arguments",
                }
            ],
        }
    path.write_text(
        json.dumps(
            {
                "schemaVersion": "1",
                "tool": "sandbox-project-cleanup",
                "project": PROJECT,
                "mode": mode,
                "filesProcessed": 25,
                "filesChanged": len(changed),
                "changedFiles": project_relative,
                "planningDiagnostics": diagnostics,
                "errorCount": 0,
                "errors": [],
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )


def populate(contract: dict[str, Any], repository: Path, baseline: Path, mode: str) -> list[str]:
    changed: list[str] = []
    required = contract["requiredFiles"]
    for relative, rules in sorted(required.items()):
        baseline_source = baseline / relative
        current_source = repository / relative
        baseline_source.parent.mkdir(parents=True, exist_ok=True)
        current_source.parent.mkdir(parents=True, exist_ok=True)
        baseline_text = "\n".join(rules.get("baselineMustContain", [])) + "\n"
        baseline_source.write_text(baseline_text, encoding="utf-8")
        if mode == "strict" and rules.get("strictUnchanged") is True:
            current_text = baseline_text
        else:
            markers = list(rules.get("migratedMustContain", []))
            if mode == "best-effort":
                markers.extend(rules.get("bestEffortMustContain", []))
            current_text = "\n".join(markers) + "\n"
            changed.append(relative)
        current_source.write_text(current_text, encoding="utf-8")
    return changed


def validate_verifier(contract: dict[str, Any]) -> None:
    verifier = QA / "verify_jdt_ui_corpus.py"
    contract_path = QA / "jdt-ui-junit4-corpus.json"
    for mode in ("strict", "best-effort"):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            repository = root / "repository"
            baseline = root / "baseline"
            changed = populate(contract, repository, baseline, mode)
            changed_file = root / "changed-files.txt"
            changed_file.write_text("\n".join(changed) + "\n", encoding="utf-8")
            check = root / "check.json"
            apply = root / "apply.json"
            write_report(check, "check", changed, best_effort=mode == "best-effort")
            write_report(apply, "apply", changed, best_effort=mode == "best-effort")
            output = root / "result.json"
            command = [
                sys.executable,
                str(verifier),
                "--repository",
                str(repository),
                "--baseline-sources",
                str(baseline),
                "--contract",
                str(contract_path),
                "--mode",
                mode,
                "--changed-files",
                str(changed_file),
                "--check-report",
                str(check),
                "--apply-report",
                str(apply),
                "--output",
                str(output),
            ]
            result = run(command)
            if result.returncode != 0:
                fail(f"JDT UI corpus verifier rejected valid {mode} evidence:\n{result.stdout}")
            if mode == "strict":
                quarantined = next(
                    relative
                    for relative, rules in contract["requiredFiles"].items()
                    if rules.get("strictUnchanged") is True
                )
                with (repository / quarantined).open("a", encoding="utf-8") as stream:
                    stream.write("// accidental strict partial migration\n")
                rejected = run(command)
                if rejected.returncode == 0:
                    fail("JDT UI corpus verifier accepted a modified strict-quarantine file")


def main() -> int:
    contract = load_object(QA / "jdt-ui-junit4-corpus.json")
    if contract.get("repository") != "https://github.com/eclipse-jdt/eclipse.jdt.ui.git":
        fail("JDT UI corpus repository is not pinned to eclipse-jdt/eclipse.jdt.ui")
    if contract.get("ref") != "R4_40" or contract.get("commit") != PINNED_COMMIT:
        fail("JDT UI corpus ref/commit differs from the pinned R4_40 source")
    if contract.get("project") != PROJECT:
        fail("JDT UI corpus must target org.eclipse.jdt.ui.tests")
    required = contract.get("requiredFiles")
    if not isinstance(required, dict) or set(required) != EXPECTED_FILES:
        fail(f"JDT UI corpus must name exactly {sorted(EXPECTED_FILES)}")
    if int(contract.get("minimumChangedJavaFiles", 0)) < 4:
        fail("JDT UI corpus allows fewer than four supported source changes")
    difficult = required[
        "org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/quickfix/ConvertLoopOperationTest.java"
    ]
    if difficult.get("strictUnchanged") is not True:
        fail("ConvertLoopOperationTest is not protected by strict quarantine")
    if difficult.get("strictReasonCode") != "PARAMETERIZED_FIELD_INJECTION":
        fail("ConvertLoopOperationTest does not require the field-injection reason code")
    if not difficult.get("bestEffortMustContain"):
        fail("Best-effort corpus contains no required TODO scaffold evidence")

    strict = properties(QA / "junit4-to-jupiter.properties")
    best = properties(QA / "junit4-to-jupiter-best-effort.properties")
    for label, profile, expected in (
        ("strict", strict, "false"),
        ("best-effort", best, "true"),
    ):
        if profile.get("cleanup.junitcleanup") != "true":
            fail(f"{label} profile does not enable JUnit 4 migration")
        if profile.get("cleanup.junit3cleanup") != "false":
            fail(f"{label} profile mixes the JDT Core/JUnit 3 migration track")
        if profile.get("cleanup.junitcleanup_best_effort") != expected:
            fail(f"{label} profile has the wrong best-effort policy")
        if profile.get("cleanup.junitcleanup_4_parameterized") != "true":
            fail(f"{label} profile does not exercise real JDT UI parameterization")

    runner = (QA / "run-jdt-ui-before-after.sh").read_text(encoding="utf-8")
    for marker in (
        "org.eclipse.jdt.ui.tests",
        "org.eclipse.jdt.bcoview",
        "REACTOR_PROJECTS",
        "verify_reactor_bcoview_runtime",
        "jdt-ui-junit4-corpus.json",
        "verify_jdt_ui_corpus.py",
        "JUnitXmlInventoryComparatorTest#configuredUpstreamEvidenceIsComparedByMaven",
        "sandbox.junit.inventory.baseline",
        "sandbox.junit.inventory.migrated",
        "sandbox.junit.inventory.mapping",
        "sandbox.junit.inventory.output",
        "strict|best-effort",
    ):
        if marker not in runner:
            fail(f"JDT UI runner is missing contract marker {marker!r}")
    if "compare_test_inventory.py" in runner:
        fail("JDT UI runner still delegates inventory comparison to Python")

    validate_sources()
    validate_verifier(contract)
    print("Pinned JDT UI JUnit 4 QA contract is valid.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"JDT UI QA contract validation failed: {exc}", file=sys.stderr)
        raise SystemExit(2) from exc
