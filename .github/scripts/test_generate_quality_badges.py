#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).with_name("generate_quality_badges.py")
SPEC = importlib.util.spec_from_file_location("generate_quality_badges", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class QualityBadgeGeneratorTest(unittest.TestCase):
    def write(self, root: Path, relative: str, content: str) -> Path:
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        return path

    def coverage_report(self, root: Path, covered: int = 82, missed: int = 18) -> Path:
        return self.write(
            root,
            "sandbox_coverage/target/site/jacoco-aggregate/jacoco.xml",
            f"""<?xml version="1.0" encoding="UTF-8"?>
<report name="aggregate">
  <package name="example"><counter type="INSTRUCTION" missed="999" covered="1"/></package>
  <counter type="BRANCH" missed="5" covered="5"/>
  <counter type="INSTRUCTION" missed="{missed}" covered="{covered}"/>
</report>
""",
        )

    def test_collects_surefire_and_failsafe_without_double_counting(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write(
                root,
                "module-a/target/surefire-reports/TEST-A.xml",
                '<testsuite tests="4" failures="0" errors="0" skipped="1"/>',
            )
            self.write(
                root,
                "module-b/target/failsafe-reports/TEST-B.xml",
                """<testsuites>
<testsuite tests="2" failures="0" errors="0" skipped="0"/>
<testsuite tests="3" failures="1" errors="0" skipped="1"/>
</testsuites>""",
            )

            totals = MODULE.collect_tests(root)

            self.assertEqual(9, totals.tests)
            self.assertEqual(1, totals.failures)
            self.assertEqual(0, totals.errors)
            self.assertEqual(2, totals.skipped)
            self.assertEqual(7, totals.executed)
            self.assertEqual(6, totals.passed)
            self.assertEqual(2, totals.report_files)

    def test_uses_only_the_aggregate_jacoco_counter(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = self.coverage_report(root)

            coverage = MODULE.collect_coverage(report)

            self.assertEqual(82, coverage.covered)
            self.assertEqual(18, coverage.missed)
            self.assertEqual(82.0, coverage.percent)

    def test_generates_exact_badges_summary_and_html(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write(
                root,
                "module-a/target/surefire-reports/TEST-A.xml",
                '<testsuite tests="10" failures="0" errors="0" skipped="2"/>',
            )
            report = self.coverage_report(root, covered=7, missed=3)
            output = root / "site"

            summary = MODULE.generate(
                root,
                output,
                report,
                "abc123",
                "2026-08-23T12:00:00+00:00",
            )

            tests_badge = json.loads((output / "badges/tests.json").read_text())
            coverage_badge = json.loads((output / "badges/coverage.json").read_text())
            persisted_summary = json.loads((output / "quality-summary.json").read_text())
            report_html = (output / "tests/index.html").read_text()

            self.assertEqual("10, 2 skipped", tests_badge["message"])
            self.assertEqual("brightgreen", tests_badge["color"])
            self.assertEqual("70.0%", coverage_badge["message"])
            self.assertEqual("yellow", coverage_badge["color"])
            self.assertEqual(summary, persisted_summary)
            self.assertEqual(8, persisted_summary["tests"]["passed"])
            self.assertIn("<td>Skipped</td><td>2</td>", report_html)
            self.assertIn("abc123", report_html)

    def test_marks_test_failures_red(self) -> None:
        totals = MODULE.TestTotals(tests=8, failures=1, errors=1, skipped=2, report_files=1)
        badge = MODULE.test_badge(totals)
        self.assertEqual("8, 2 failing, 2 skipped", badge["message"])
        self.assertEqual("red", badge["color"])

    def test_rejects_missing_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaises(FileNotFoundError):
                MODULE.collect_tests(root)
            with self.assertRaises(FileNotFoundError):
                MODULE.collect_coverage(root / "missing.xml")

    def test_rejects_inconsistent_test_totals(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write(
                root,
                "module/target/surefire-reports/TEST-Broken.xml",
                '<testsuite tests="1" failures="1" errors="1" skipped="0"/>',
            )
            with self.assertRaises(ValueError):
                MODULE.collect_tests(root)


if __name__ == "__main__":
    unittest.main()
