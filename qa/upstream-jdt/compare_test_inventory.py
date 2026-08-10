#!/usr/bin/env python3
"""Compare JUnit XML inventories produced before and after a migration."""

from __future__ import annotations

import argparse
import fnmatch
import json
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


@dataclass(frozen=True)
class Inventory:
    states: Counter[tuple[str, str]]
    report_files: int
    parse_errors: tuple[str, ...]

    @property
    def test_count(self) -> int:
        return sum(self.states.values())

    def count_state(self, state: str) -> int:
        return sum(count for (_, actual_state), count in self.states.items() if actual_state == state)


def _state(testcase: ET.Element) -> str:
    if testcase.find("failure") is not None:
        return "failure"
    if testcase.find("error") is not None:
        return "error"
    if testcase.find("skipped") is not None:
        return "skipped"
    return "passed"


def _testcases(root: ET.Element) -> Iterable[tuple[str, ET.Element]]:
    if root.tag == "testsuite":
        suites = [root]
    else:
        suites = list(root.iter("testsuite"))
    for suite in suites:
        suite_name = suite.attrib.get("name", "<unnamed-suite>")
        for testcase in suite.findall("testcase"):
            yield suite_name, testcase


def collect(directory: Path) -> Inventory:
    states: Counter[tuple[str, str]] = Counter()
    parse_errors: list[str] = []
    reports = sorted(directory.rglob("*.xml"))
    for report in reports:
        try:
            root = ET.parse(report).getroot()
        except (ET.ParseError, OSError) as exc:
            parse_errors.append(f"{report}: {exc}")
            continue
        for suite_name, testcase in _testcases(root):
            owner = testcase.attrib.get("classname") or suite_name
            name = testcase.attrib.get("name", "<unnamed-test>")
            states[(f"{owner}#{name}", _state(testcase))] += 1
    return Inventory(states=states, report_files=len(reports), parse_errors=tuple(parse_errors))


def _matches_any(value: str, patterns: list[str]) -> bool:
    return any(fnmatch.fnmatchcase(value, pattern) for pattern in patterns)


def _mapped_baseline(inventory: Inventory, renames: dict[str, str]) -> Counter[tuple[str, str]]:
    mapped: Counter[tuple[str, str]] = Counter()
    for (identity, state), count in inventory.states.items():
        mapped[(renames.get(identity, identity), state)] += count
    return mapped


def _render(counter: Counter[tuple[str, str]]) -> list[dict[str, object]]:
    return [
        {"test": identity, "state": state, "count": count}
        for (identity, state), count in sorted(counter.items())
    ]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", required=True, type=Path)
    parser.add_argument("--migrated", required=True, type=Path)
    parser.add_argument("--mapping", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    mapping = json.loads(args.mapping.read_text(encoding="utf-8"))
    renames = mapping.get("renames", {})
    allowed_missing = mapping.get("allowedMissing", [])
    allowed_added = mapping.get("allowedAdded", [])
    if not isinstance(renames, dict) or not isinstance(allowed_missing, list) or not isinstance(allowed_added, list):
        raise ValueError("Invalid mapping document")

    baseline = collect(args.baseline)
    migrated = collect(args.migrated)
    expected = _mapped_baseline(baseline, {str(k): str(v) for k, v in renames.items()})

    missing = expected - migrated.states
    added = migrated.states - expected
    unexpected_missing = Counter(
        {key: count for key, count in missing.items() if not _matches_any(key[0], allowed_missing)}
    )
    unexpected_added = Counter(
        {key: count for key, count in added.items() if not _matches_any(key[0], allowed_added)}
    )

    problems: list[str] = []
    if baseline.report_files == 0 or baseline.test_count == 0:
        problems.append("Baseline produced no parseable test inventory")
    if migrated.report_files == 0 or migrated.test_count == 0:
        problems.append("Migrated run produced no parseable test inventory")
    problems.extend(f"Baseline report parse error: {error}" for error in baseline.parse_errors)
    problems.extend(f"Migrated report parse error: {error}" for error in migrated.parse_errors)
    if baseline.count_state("failure") or baseline.count_state("error"):
        problems.append("Baseline contains failing or errored tests")
    if migrated.count_state("failure") or migrated.count_state("error"):
        problems.append("Migrated run contains failing or errored tests")
    if unexpected_missing:
        problems.append("Tests disappeared or changed state after migration")
    if unexpected_added:
        problems.append("Unexpected tests appeared or changed state after migration")

    summary = {
        "baseline": {
            "reportFiles": baseline.report_files,
            "tests": baseline.test_count,
            "passed": baseline.count_state("passed"),
            "skipped": baseline.count_state("skipped"),
            "failures": baseline.count_state("failure"),
            "errors": baseline.count_state("error"),
        },
        "migrated": {
            "reportFiles": migrated.report_files,
            "tests": migrated.test_count,
            "passed": migrated.count_state("passed"),
            "skipped": migrated.count_state("skipped"),
            "failures": migrated.count_state("failure"),
            "errors": migrated.count_state("error"),
        },
        "unexpectedMissing": _render(unexpected_missing),
        "unexpectedAdded": _render(unexpected_added),
        "problems": problems,
        "result": "PASS" if not problems else "FAIL",
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 0 if not problems else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"Inventory comparison failed: {exc}", file=sys.stderr)
        raise SystemExit(2) from exc
