#!/usr/bin/env python3
"""Verify that real, named JDT JUnit 3 corpus cases were actually migrated."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any


def fail(message: str) -> None:
    raise ValueError(message)


def load_json(path: Path) -> dict[str, Any]:
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


def report_changed_paths(report: dict[str, Any], project: str) -> set[str]:
    values = report.get("changedFiles", [])
    if not isinstance(values, list) or any(not isinstance(value, str) for value in values):
        fail("Cleanup report changedFiles is not a string array")
    prefix = f"{project}/"
    return {
        value.replace("\\", "/")
        if value.replace("\\", "/").startswith(prefix)
        else prefix + value.replace("\\", "/").lstrip("/")
        for value in values
    }


def verify_report(
    report: dict[str, Any],
    *,
    expected_mode: str,
    project: str,
    problems: list[str],
) -> None:
    if report.get("tool") != "sandbox-project-cleanup":
        problems.append(f"{expected_mode} evidence was not produced by the project-wide cleanup application")
    if report.get("mode") != expected_mode:
        problems.append(f"Expected a {expected_mode} cleanup report, got {report.get('mode')!r}")
    if report.get("project") != project:
        problems.append(f"Cleanup report project is {report.get('project')!r}, expected {project!r}")
    try:
        error_count = int(report.get("errorCount", -1))
        files_processed = int(report.get("filesProcessed", 0))
        files_changed = int(report.get("filesChanged", -1))
    except (TypeError, ValueError) as exc:
        fail(f"Cleanup report counters are invalid: {exc}") from exc
    if error_count != 0:
        problems.append(f"{expected_mode} cleanup report contains errors: {report.get('errors')}")
    if files_processed <= 1:
        problems.append(f"{expected_mode} cleanup processed fewer than two source files")
    changed = report.get("changedFiles", [])
    if isinstance(changed, list) and files_changed != len(changed):
        problems.append(
            f"{expected_mode} report filesChanged={files_changed} but lists {len(changed)} paths"
        )


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
    parser.add_argument("--project", required=True)
    parser.add_argument("--contract", required=True, type=Path)
    parser.add_argument("--changed-files", required=True, type=Path)
    parser.add_argument("--check-report", required=True, type=Path)
    parser.add_argument("--apply-report", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    repository = args.repository.resolve()
    contract = load_json(args.contract)
    check_report = load_json(args.check_report)
    apply_report = load_json(args.apply_report)
    changed = changed_paths(args.changed_files)
    problems: list[str] = []

    verify_report(check_report, expected_mode="check", project=args.project, problems=problems)
    verify_report(apply_report, expected_mode="apply", project=args.project, problems=problems)

    check_changed = report_changed_paths(check_report, args.project)
    apply_changed = report_changed_paths(apply_report, args.project)
    if not check_changed:
        problems.append("Check mode predicted no changed files")
    if check_changed != apply_changed:
        problems.append(
            "Check/apply changed-file inventories differ: "
            f"check-only={sorted(check_changed - apply_changed)}, "
            f"apply-only={sorted(apply_changed - check_changed)}"
        )
    if apply_changed != changed:
        problems.append(
            "Apply report and Git diff disagree: "
            f"report-only={sorted(apply_changed - changed)}, "
            f"diff-only={sorted(changed - apply_changed)}"
        )

    check_diagnostics = check_report.get("planningDiagnostics", [])
    apply_diagnostics = apply_report.get("planningDiagnostics", [])
    if contract.get("requirePlanningDiagnostics") and not apply_diagnostics:
        problems.append("No structured JUnit planning diagnostics were exported")
    if check_diagnostics != apply_diagnostics:
        problems.append("Check and apply planning diagnostics are not deterministic")

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
        must_contain = raw_rules.get("mustContain", [])
        must_not_contain = raw_rules.get("mustNotContain", [])
        if not isinstance(must_contain, list) or not isinstance(must_not_contain, list):
            fail(f"Invalid text rules for {relative}")
        missing = [
            value
            for value in must_contain
            if not isinstance(value, str) or value not in text
        ]
        remaining = [
            value
            for value in must_not_contain
            if not isinstance(value, str) or value in text
        ]
        result["missingRequiredText"] = missing
        result["remainingForbiddenText"] = remaining
        if missing:
            problems.append(f"{relative} lacks required migrated text: {missing}")
        if remaining:
            problems.append(f"{relative} retains forbidden JUnit 3 text: {remaining}")
        file_results[relative] = result

    remaining = remaining_legacy_files(repository, args.project)
    summary = {
        "result": "PASS" if not problems else "FAIL",
        "project": args.project,
        "projectWideFilesProcessed": int(apply_report.get("filesProcessed", 0)),
        "checkChangedFiles": sorted(check_changed),
        "applyChangedFiles": sorted(apply_changed),
        "changedJavaFiles": changed_java,
        "planningDiagnostics": apply_diagnostics,
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
