#!/usr/bin/env python3
"""Invoke the Maven/JUnit authority for pinned JDT UI corpus evidence."""

from __future__ import annotations

import argparse
import os
import subprocess
from pathlib import Path


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

    root = Path(__file__).resolve().parents[2]
    maven = os.environ.get("MAVEN_BIN", "mvn")
    command = [
        maven,
        "--batch-mode",
        "--no-transfer-progress",
        "-pl",
        "sandbox_common_test",
        "-am",
        "-DskipTests=false",
        "-DfailIfNoTests=false",
        "-Dsurefire.failIfNoSpecifiedTests=false",
        "-Dtest=JdtUiCorpusEvidenceExecutionTest",
        "-Dsandbox.jdt.ui.evidence.enabled=true",
        f"-Dsandbox.jdt.ui.evidence.repository={args.repository.resolve()}",
        f"-Dsandbox.jdt.ui.evidence.baselineSources={args.baseline_sources.resolve()}",
        f"-Dsandbox.jdt.ui.evidence.contract={args.contract.resolve()}",
        f"-Dsandbox.jdt.ui.evidence.mode={args.mode}",
        f"-Dsandbox.jdt.ui.evidence.changedFiles={args.changed_files.resolve()}",
        f"-Dsandbox.jdt.ui.evidence.checkReport={args.check_report.resolve()}",
        f"-Dsandbox.jdt.ui.evidence.applyReport={args.apply_report.resolve()}",
        f"-Dsandbox.jdt.ui.evidence.output={args.output.resolve()}",
        "test",
    ]
    return subprocess.run(command, cwd=root, check=False).returncode


if __name__ == "__main__":
    raise SystemExit(main())
