#!/usr/bin/env python3
"""Invoke the Maven/JUnit authority for the pinned JDT UI QA contract."""

from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path


def main() -> int:
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
        "-Dtest=JdtUiMigrationContractTest",
        "test",
    ]
    return subprocess.run(command, cwd=root, check=False).returncode


if __name__ == "__main__":
    raise SystemExit(main())
