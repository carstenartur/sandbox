#!/usr/bin/env python3
"""Verify that real, named JDT JUnit 3 corpus cases were actually migrated."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path


def fail(message: str) -> None:
    raise ValueError(message)


def load_json(path: Path) -> dict[str, object]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        fail(f"{path} does not contain a JSON object")
    return value


def changed_paths(path: Path) -> set[str]:
    return {
        line.strip().replace("\\", "/")
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    }


def remaining_legacy_files(repository: Path, project: str) -> list[str]:
    root = repository / project / "src"
    if not root.is_dir():
        fail(f"Missing pinned source root: {root}")
    legacy_pattern = re.compile(
        r"(?:extends\s+(?:[\w.]+\.)?TestCase\b|"
        r"import\s+junit\.framework\.|"
        r"public\s+static\s+(?:[\w.]+\.)?Test\s+suite\s*\()"
    )
    result: list[str] = []
    for source in sorted(root.rglob("*.java")):
        text = source.read_text(encoding="utf-8", errors="strict")
        if legacy_pattern.search(text):
            result.append(source.relative_to(repository).as_posix())
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", required=True, type=Path)
    parser.add_argument("--contract", required=True, type=Path)
    parser.add_argument("--changed-files", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    repository = args.repository.resolve()
    contract = load_json(args.contract)
    report = load_json(args.report)
    changed = changed_paths(args.changed_files)
    problems: list[str] = []

    if report.get("tool") != "sandbox-project-cleanup":
        problems.append("The evidence was not produced by the project-wide cleanup application")
    if report.get("mode") != "apply":
        problems.append("The inspected cleanup report is not an apply result")
    if int(report.get("errorCount", -1)) != 0:
        problems.append(f"Cleanup report contains errors: {report.get('errors')}")
    if int(report.get("filesProcessed", 0)) <= 1:
        problems.append("Project-wide cleanup processed fewer than two source files")

    diagnostics = report.get("planningDiagnostics", [])
    if contract.get("requirePlanningDiagnostics") and not diagnostics:
        problems.append("No structured JUnit planning diagnostics were exported")

    changed_java = sorted(path for path in changed if path.endswith(".java"))
    minimum = int(contract.get("minimumChangedJavaFiles", 0))
    if len(changed_java) < minimum:
        problems.append(
            f"Only {len(changed_java)} Java files changed; the contract requires at least {minimum}"
        )

    required_files = contract.get("requiredFiles")
    if not isinstance(required_files, dict) or not required_files:
        fail("Corpus contract has no requiredFiles object")

    file_results: dict[str, object] = {}
    for relative, raw_rules in sorted(required_files.items()):
        if not isinstance(relative, str) or not isinstance(raw_rules, dict):
            fail("Invalid requiredFiles entry")
        source = repository / relative
        result: dict[str, object] = {
            "changed": relative in changed,
            "exists": source.is_file(),
            "missingRequiredText": [],
            "remainingForbiddenText": [],
        }
        if relative not in changed:
            problems.append(f"Required real corpus file was not changed: {relative}")
        if not source.is_file():
            problems.append(f"Required real corpus file is missing: {relative}")
            file_results[relative] = result
            continue
        text = source.read_text(encoding="utf-8", errors="strict")
        missing = [
            value
            for value in raw_rules.get("mustContain", [])
            if not isinstance(value, str) or value not in text
        ]
        remaining = [
            value
            for value in raw_rules.get("mustNotContain", [])
            if not isinstance(value, str) or value in text
        ]
        result["missingRequiredText"] = missing
        result["remainingForbiddenText"] = remaining
        if missing:
            problems.append(f"{relative} lacks required migrated text: {missing}")
        if remaining:
            problems.append(f"{relative} retains forbidden JUnit 3 text: {remaining}")
        file_results[relative] = result

    remaining = remaining_legacy_files(repository, "org.eclipse.jdt.apt.tests")
    summary = {
        "result": "PASS" if not problems else "FAIL",
        "projectWideFilesProcessed": int(report.get("filesProcessed", 0)),
        "changedJavaFiles": changed_java,
        "planningDiagnostics": diagnostics,
        "requiredFiles": file_results,
        "remainingLegacyJUnit3Files": remaining,
        "remainingLegacyJUnit3FileCount": len(remaining),
        "problems": problems,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 0 if not problems else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"Corpus result verification failed: {exc}", file=sys.stderr)
        raise SystemExit(2) from exc
