#!/usr/bin/env python3
"""Validate real pinned JDT UI JUnit 4 migration evidence."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


def fail(message: str) -> None:
    raise ValueError(message)


def load_object(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        fail(f"{path} does not contain a JSON object")
    return value


def string_list(value: Any, label: str, *, allow_empty: bool = True) -> list[str]:
    if not isinstance(value, list) or any(not isinstance(item, str) or not item for item in value):
        fail(f"{label} must be a list of non-empty strings")
    if not allow_empty and not value:
        fail(f"{label} must not be empty")
    return value


def changed_files(path: Path) -> set[str]:
    return {
        line.strip().replace("\\", "/")
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    }


def normalize_report_file(project: str, value: str) -> str:
    normalized = value.replace("\\", "/").lstrip("./")
    return normalized if normalized.startswith(project + "/") else f"{project}/{normalized}"


def report_changed_files(report: dict[str, Any], project: str, label: str) -> set[str]:
    values = report.get("changedFiles")
    if not isinstance(values, list) or any(not isinstance(item, str) or not item for item in values):
        fail(f"{label} report has no valid changedFiles list")
    return {normalize_report_file(project, item) for item in values}


def require_report(report: dict[str, Any], mode: str, label: str) -> None:
    if report.get("mode") != mode:
        fail(f"{label} report mode is {report.get('mode')!r}, expected {mode!r}")
    if int(report.get("errorCount", 0)) != 0:
        fail(f"{label} report contains cleanup errors")
    errors = report.get("errors", [])
    if not isinstance(errors, list) or errors:
        fail(f"{label} report contains error entries")
    if "planningDiagnostics" not in report:
        fail(f"{label} report contains no planningDiagnostics")


def require_markers(text: str, markers: list[str], label: str) -> None:
    missing = [marker for marker in markers if marker not in text]
    if missing:
        fail(f"{label} is missing markers: {missing}")


def require_absent(text: str, markers: list[str], label: str) -> None:
    present = [marker for marker in markers if marker in text]
    if present:
        fail(f"{label} still contains forbidden markers: {present}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", required=True, type=Path)
    parser.add_argument("--baseline-sources", required=True, type=Path)
    parser.add_argument("--contract", required=True, type=Path)
    parser.add_argument("--mode", required=True, choices=("strict", "best-effort"))
    parser.add_argument("--changed-files", required=True, type=Path)
    parser.add_argument("--check-report", required=True, type=Path)
    parser.add_argument("--apply-report", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    contract = load_object(args.contract)
    project = contract.get("project")
    if not isinstance(project, str) or not project:
        fail("Corpus contract has no project")
    required = contract.get("requiredFiles")
    if not isinstance(required, dict) or not required:
        fail("Corpus contract has no requiredFiles object")

    actual_changed = changed_files(args.changed_files)
    actual_java = {path for path in actual_changed if path.endswith(".java")}
    actual_non_java = actual_changed - actual_java
    minimum = int(contract.get("minimumChangedJavaFiles", 0))
    if len(actual_java) < minimum:
        fail(f"Only {len(actual_java)} Java files changed; contract requires at least {minimum}")

    check_report = load_object(args.check_report)
    apply_report = load_object(args.apply_report)
    require_report(check_report, "check", "check")
    require_report(apply_report, "apply", "apply")
    check_changed = report_changed_files(check_report, project, "check")
    apply_changed = report_changed_files(apply_report, project, "apply")
    if check_changed != apply_changed:
        fail("Cleanup check and apply report different changed-file sets")
    if check_changed != actual_changed:
        fail(
            "Cleanup reports and Git migration patch differ: "
            f"reportOnly={sorted(check_changed - actual_changed)}, "
            f"gitOnly={sorted(actual_changed - check_changed)}"
        )

    strict_unchanged: list[str] = []
    verified_changed: list[str] = []
    required_reason_codes: set[str] = set()
    for relative, raw_rules in sorted(required.items()):
        if not isinstance(relative, str) or not relative or not isinstance(raw_rules, dict):
            fail(f"Invalid requiredFiles entry: {relative!r}")
        current = args.repository / relative
        baseline = args.baseline_sources / relative
        if not current.is_file() or not baseline.is_file():
            fail(f"Required corpus source is missing: {relative}")
        baseline_text = baseline.read_text(encoding="utf-8")
        current_text = current.read_text(encoding="utf-8")
        require_markers(
            baseline_text,
            string_list(raw_rules.get("baselineMustContain", []), f"{relative}.baselineMustContain"),
            f"baseline {relative}",
        )

        unchanged_in_strict = raw_rules.get("strictUnchanged") is True
        if args.mode == "strict" and unchanged_in_strict:
            if relative in actual_changed:
                fail(f"Strict mode changed quarantined corpus file: {relative}")
            if baseline.read_bytes() != current.read_bytes():
                fail(f"Strict mode did not preserve quarantined file byte-for-byte: {relative}")
            if "Sandbox JUnit migration gap" in current_text:
                fail(f"Strict mode inserted a best-effort marker into {relative}")
            strict_unchanged.append(relative)
            continue

        if relative not in actual_changed:
            fail(f"Expected migrated corpus file is absent from the patch: {relative}")
        require_markers(
            current_text,
            string_list(raw_rules.get("migratedMustContain", []), f"{relative}.migratedMustContain"),
            f"migrated {relative}",
        )
        require_absent(
            current_text,
            string_list(raw_rules.get("migratedMustNotContain", []), f"{relative}.migratedMustNotContain"),
            f"migrated {relative}",
        )
        if args.mode == "best-effort":
            best_effort_markers = string_list(
                raw_rules.get("bestEffortMustContain", []),
                f"{relative}.bestEffortMustContain",
            )
            require_markers(current_text, best_effort_markers, f"best-effort {relative}")
            reason = raw_rules.get("strictReasonCode")
            if reason is not None:
                if not isinstance(reason, str) or not reason:
                    fail(f"{relative}.strictReasonCode must be a non-empty string")
                required_reason_codes.add(reason)
        elif "Sandbox JUnit migration gap" in current_text:
            fail(f"Strict mode inserted a best-effort marker into {relative}")
        verified_changed.append(relative)

    if args.mode == "best-effort":
        evidence = json.dumps(
            {"check": check_report, "apply": apply_report},
            sort_keys=True,
            ensure_ascii=False,
        )
        missing_reasons = sorted(reason for reason in required_reason_codes if reason not in evidence)
        if missing_reasons:
            fail(f"Best-effort reports omit required reason codes: {missing_reasons}")
        if '"manualCompletionRequired": true' not in evidence and '"manualCompletionRequired":true' not in evidence:
            fail("Best-effort reports do not state that manual completion is required")

    summary = {
        "result": "PASS",
        "mode": args.mode,
        "project": project,
        "changedFiles": len(actual_changed),
        "changedJavaFiles": len(actual_java),
        "changedNonJavaFiles": sorted(actual_non_java),
        "verifiedChangedCorpusFiles": verified_changed,
        "strictlyQuarantinedCorpusFiles": strict_unchanged,
        "requiredReasonCodes": sorted(required_reason_codes),
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"JDT UI corpus verification failed: {exc}", file=sys.stderr)
        raise SystemExit(2) from exc
