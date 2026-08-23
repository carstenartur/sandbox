#!/usr/bin/env python3
"""Generate commit-bound Shields badges from JUnit and aggregate JaCoCo XML."""

from __future__ import annotations

import argparse
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
import html
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


@dataclass(frozen=True)
class TestTotals:
    tests: int = 0
    failures: int = 0
    errors: int = 0
    skipped: int = 0
    report_files: int = 0

    @property
    def executed(self) -> int:
        return self.tests - self.skipped

    @property
    def passed(self) -> int:
        return self.tests - self.skipped - self.failures - self.errors


@dataclass(frozen=True)
class CoverageTotals:
    covered: int
    missed: int
    percent: float
    metric: str


def _local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def _integer(element: ET.Element, attribute: str, source: Path) -> int:
    raw = element.attrib.get(attribute, "0")
    try:
        value = int(float(raw))
    except ValueError as error:
        raise ValueError(
            f"invalid {attribute}={raw!r} in {source} ({_local_name(element.tag)})"
        ) from error
    if value < 0:
        raise ValueError(f"negative {attribute}={value} in {source}")
    return value


def _suite_totals(root: ET.Element, source: Path) -> tuple[int, int, int, int]:
    name = _local_name(root.tag)
    if name == "testsuite":
        suites = [root]
    elif name == "testsuites":
        # Prefer aggregate attributes when the producer provides them. Otherwise
        # sum only direct children so nested suite summaries are not counted twice.
        if "tests" in root.attrib:
            suites = [root]
        else:
            suites = [child for child in root if _local_name(child.tag) == "testsuite"]
    else:
        raise ValueError(f"unsupported JUnit root element {name!r} in {source}")

    if not suites:
        raise ValueError(f"no testsuite data in {source}")

    values = [0, 0, 0, 0]
    for suite in suites:
        for index, attribute in enumerate(("tests", "failures", "errors", "skipped")):
            values[index] += _integer(suite, attribute, source)
    return tuple(values)  # type: ignore[return-value]


def junit_report_files(root: Path) -> list[Path]:
    patterns = (
        "**/target/surefire-reports/TEST-*.xml",
        "**/target/failsafe-reports/TEST-*.xml",
    )
    reports: dict[Path, Path] = {}
    for pattern in patterns:
        for candidate in root.glob(pattern):
            if candidate.is_file():
                reports[candidate.resolve()] = candidate
    return sorted(reports.values(), key=lambda path: path.as_posix())


def collect_tests(root: Path) -> TestTotals:
    reports = junit_report_files(root)
    if not reports:
        raise FileNotFoundError(f"no JUnit XML reports found below {root}")

    tests = failures = errors = skipped = 0
    for report in reports:
        document = ET.parse(report).getroot()
        current_tests, current_failures, current_errors, current_skipped = _suite_totals(
            document, report
        )
        tests += current_tests
        failures += current_failures
        errors += current_errors
        skipped += current_skipped

    totals = TestTotals(tests, failures, errors, skipped, len(reports))
    if totals.tests <= 0:
        raise ValueError("JUnit reports contained zero tests")
    if totals.executed < 0 or totals.passed < 0:
        raise ValueError(f"inconsistent JUnit totals: {totals}")
    return totals


def collect_coverage(report: Path, metric: str = "INSTRUCTION") -> CoverageTotals:
    if not report.is_file():
        raise FileNotFoundError(f"aggregate JaCoCo report missing: {report}")

    document = ET.parse(report).getroot()
    requested = metric.upper()
    counters = [
        child
        for child in document
        if _local_name(child.tag) == "counter" and child.attrib.get("type") == requested
    ]
    if len(counters) != 1:
        raise ValueError(
            f"expected exactly one aggregate {requested} counter in {report}, found {len(counters)}"
        )

    counter = counters[0]
    covered = _integer(counter, "covered", report)
    missed = _integer(counter, "missed", report)
    total = covered + missed
    if total <= 0:
        raise ValueError(f"aggregate {requested} counter contains no instructions in {report}")
    return CoverageTotals(covered, missed, round(covered * 100.0 / total, 2), requested)


def coverage_color(percent: float) -> str:
    if percent >= 80.0:
        return "brightgreen"
    if percent >= 60.0:
        return "yellow"
    if percent >= 40.0:
        return "orange"
    return "red"


def write_json(path: Path, payload: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def test_badge(totals: TestTotals) -> dict[str, object]:
    failing = totals.failures + totals.errors
    if failing:
        message = f"{totals.tests}, {failing} failing, {totals.skipped} skipped"
        color = "red"
    else:
        message = f"{totals.tests}, {totals.skipped} skipped"
        color = "brightgreen"
    return {"schemaVersion": 1, "label": "tests", "message": message, "color": color}


def coverage_badge(totals: CoverageTotals) -> dict[str, object]:
    return {
        "schemaVersion": 1,
        "label": "coverage",
        "message": f"{totals.percent:.1f}%",
        "color": coverage_color(totals.percent),
    }


def _module_report_links(root: Path) -> list[tuple[str, str]]:
    links: list[tuple[str, str]] = []
    for report in sorted(root.glob("sandbox_*_test/target/site/surefire-report.html")):
        module = report.parents[2].name
        links.append((module, f"{module}/surefire-report.html"))
    return links


def write_test_index(
    output: Path,
    root: Path,
    tests: TestTotals,
    coverage: CoverageTotals,
    commit: str,
    generated_at: str,
) -> None:
    links = _module_report_links(root)
    module_html = "\n".join(
        f'<li><a href="{html.escape(url, quote=True)}">{html.escape(module)}</a></li>'
        for module, url in links
    )
    if not module_html:
        module_html = "<li>No Maven Site module reports were generated.</li>"

    document = f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Sandbox verified test results</title>
<style>
body {{ font: 16px/1.5 system-ui, sans-serif; max-width: 72rem; margin: 2rem auto; padding: 0 1rem; }}
table {{ border-collapse: collapse; margin: 1rem 0 1.5rem; }}
th, td {{ border: 1px solid #bbb; padding: .45rem .7rem; text-align: right; }}
th:first-child, td:first-child {{ text-align: left; }}
code {{ overflow-wrap: anywhere; }}
</style>
</head>
<body>
<h1>Sandbox verified test results</h1>
<p>Source commit: <code>{html.escape(commit)}</code></p>
<p>Generated: {html.escape(generated_at)}</p>
<table>
<thead><tr><th>Metric</th><th>Value</th></tr></thead>
<tbody>
<tr><td>Registered tests</td><td>{tests.tests}</td></tr>
<tr><td>Executed</td><td>{tests.executed}</td></tr>
<tr><td>Passed</td><td>{tests.passed}</td></tr>
<tr><td>Skipped</td><td>{tests.skipped}</td></tr>
<tr><td>Failures</td><td>{tests.failures}</td></tr>
<tr><td>Errors</td><td>{tests.errors}</td></tr>
<tr><td>Instruction coverage</td><td>{coverage.percent:.2f}%</td></tr>
<tr><td>JUnit report files</td><td>{tests.report_files}</td></tr>
</tbody>
</table>
<p><a href="../coverage/">Aggregate JaCoCo report</a> · <a href="../quality-summary.json">Machine-readable summary</a></p>
<h2>Module reports</h2>
<ul>
{module_html}
</ul>
</body>
</html>
"""
    target = output / "tests" / "index.html"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(document, encoding="utf-8")


def generate(
    root: Path,
    output: Path,
    coverage_report: Path,
    commit: str,
    generated_at: str,
) -> dict[str, object]:
    tests = collect_tests(root)
    coverage = collect_coverage(coverage_report)
    summary: dict[str, object] = {
        "schemaVersion": 1,
        "sourceCommit": commit,
        "generatedAt": generated_at,
        "tests": {
            **asdict(tests),
            "executed": tests.executed,
            "passed": tests.passed,
        },
        "coverage": asdict(coverage),
    }

    write_json(output / "badges" / "tests.json", test_badge(tests))
    write_json(output / "badges" / "coverage.json", coverage_badge(coverage))
    write_json(output / "quality-summary.json", summary)
    write_test_index(output, root, tests, coverage, commit, generated_at)
    return summary


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--output", type=Path, default=Path("target/quality-site"))
    parser.add_argument(
        "--coverage-report",
        type=Path,
        default=Path("sandbox_coverage/target/site/jacoco-aggregate/jacoco.xml"),
    )
    parser.add_argument("--commit", default="local")
    parser.add_argument(
        "--generated-at",
        default=datetime.now(timezone.utc).isoformat(timespec="seconds"),
    )
    args = parser.parse_args(argv)

    root = args.root.resolve()
    coverage_report = args.coverage_report
    if not coverage_report.is_absolute():
        coverage_report = root / coverage_report
    summary = generate(
        root,
        args.output.resolve(),
        coverage_report.resolve(),
        args.commit.strip(),
        args.generated_at.strip(),
    )
    print(json.dumps(summary, sort_keys=True))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (FileNotFoundError, ValueError, ET.ParseError) as error:
        print(f"quality badge generation failed: {error}", file=sys.stderr)
        raise SystemExit(1) from error
