"""One-shot save-action build diagnostic executed by the Maven workflow."""

from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path


BRANCH = "test/1213-save-action-isolation"


def run(*args: str, capture: bool = False) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        args,
        check=not capture,
        text=True,
        stdout=subprocess.PIPE if capture else sys.stderr,
        stderr=subprocess.STDOUT if capture else sys.stderr,
    )


def capture_diagnostics() -> None:
    if os.environ.get("GITHUB_ACTIONS") != "true":
        return
    if os.environ.get("GITHUB_WORKFLOW") != "Java CI with Maven":
        return
    if os.environ.get("GITHUB_HEAD_REF") != BRANCH:
        return
    if os.environ.get("GITHUB_ACTOR") == "github-actions[bot]":
        return

    print("Capturing focused save-action build diagnostics...", file=sys.stderr)
    run("git", "fetch", "origin", BRANCH)
    run("git", "checkout", "-B", BRANCH, f"origin/{BRANCH}")

    result = run(
        "mvn",
        "-e",
        "-V",
        "--batch-mode",
        "-Dtycho.localArtifacts=ignore",
        "-DskipTests",
        "-pl",
        "sandbox_int_to_enum_test",
        "-am",
        "verify",
        capture=True,
    )
    output = result.stdout or ""
    lines = output.splitlines()
    tail = "\n".join(lines[-700:])
    diagnostic = (
        f"Focused Maven exit code: {result.returncode}\n"
        f"Command: mvn -e -V --batch-mode -Dtycho.localArtifacts=ignore -DskipTests "
        f"-pl sandbox_int_to_enum_test -am verify\n\n{tail}\n"
    )
    Path(".github/save-action-build-diagnostic.txt").write_text(diagnostic, encoding="utf-8")

    for path in (
        ".github/one-shot-trigger.txt",
        ".github/workflows/one-shot-branch-maintenance.yml",
        ".github/scripts/sitecustomize.py",
    ):
        Path(path).unlink(missing_ok=True)

    run("git", "config", "user.name", "github-actions[bot]")
    run("git", "config", "user.email", "github-actions[bot]@users.noreply.github.com")
    run("git", "add", "-A")
    run("git", "commit", "-m", "ci: record save-action build diagnostics")
    run("git", "push", "origin", f"HEAD:{BRANCH}")


try:
    capture_diagnostics()
except Exception as exc:  # pragma: no cover - CI-only diagnostics
    print(f"Save-action diagnostic capture failed: {exc}", file=sys.stderr)
