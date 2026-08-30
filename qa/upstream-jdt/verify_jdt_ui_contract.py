#!/usr/bin/env python3
"""Invoke focused Maven/JUnit tests for the pinned JDT UI corpus contract."""

from __future__ import annotations

import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RUNNER = ROOT / "qa/upstream-jdt/run-jdt-ui-before-after.sh"
TEST = "JdtUiCorpusEvidenceVerifierTest"


def run(command: list[str]) -> int:
    return subprocess.run(command, cwd=ROOT, check=False).returncode


def main() -> int:
    syntax_status = run(["bash", "-n", str(RUNNER)])
    if syntax_status != 0:
        return syntax_status
    return run([
        "mvn",
        "--no-transfer-progress",
        "--batch-mode",
        "-pl",
        "sandbox_common_test",
        "-am",
        f"-Dtest={TEST}",
        "-Dsurefire.failIfNoSpecifiedTests=false",
        "test",
    ])


if __name__ == "__main__":
    raise SystemExit(main())
