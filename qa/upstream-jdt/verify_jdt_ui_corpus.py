#!/usr/bin/env python3
"""Invoke the Maven/JUnit authority for pinned JDT UI corpus evidence."""

from __future__ import annotations

import argparse
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TEST = "JdtUiCorpusEvidenceTest"
PROPERTY_PREFIX = "sandbox.jdt.ui.corpus."


def absolute(path: Path) -> str:
    return str(path.resolve())


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

    expected_contract = ROOT / "qa/upstream-jdt/jdt-ui-junit4-corpus.json"
    if args.contract.resolve() != expected_contract.resolve():
        parser.error(f"contract must be the checked-in Maven/JUnit contract: {expected_contract}")

    properties = {
        "repository": absolute(args.repository),
        "baselineSources": absolute(args.baseline_sources),
        "mode": args.mode,
        "changedFiles": absolute(args.changed_files),
        "checkReport": absolute(args.check_report),
        "applyReport": absolute(args.apply_report),
        "output": absolute(args.output),
    }
    command = [
        "mvn",
        "--no-transfer-progress",
        "--batch-mode",
        "-pl",
        "sandbox_common_test",
        "-am",
        f"-Dtest={TEST}",
        "-Dsurefire.failIfNoSpecifiedTests=false",
        *[f"-D{PROPERTY_PREFIX}{name}={value}" for name, value in properties.items()],
        "test",
    ]
    return subprocess.run(command, cwd=ROOT, check=False).returncode


if __name__ == "__main__":
    raise SystemExit(main())
