#!/usr/bin/env python3
"""Collect the exact completed validation workflow runs for one commit."""

from __future__ import annotations

import argparse
import json
import os
import time
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen

EXPECTED_WORKFLOWS = (
    "Java CI with Maven",
    "Core Module Build",
    "CodeQL",
    "Codacy Security Scan",
    "Test Report",
)


def fetch_runs(repository: str, commit_sha: str, token: str) -> list[dict[str, Any]]:
    url = (
        f"https://api.github.com/repos/{repository}/actions/runs"
        f"?head_sha={quote(commit_sha)}&per_page=100"
    )
    request = Request(
        url,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "sandbox-build-provenance",
        },
    )
    try:
        with urlopen(request, timeout=30) as response:
            payload = json.load(response)
    except (HTTPError, URLError) as error:
        raise RuntimeError(f"Could not query GitHub workflow runs: {error}") from error
    return payload.get("workflow_runs", [])


def select_expected_runs(
    runs: list[dict[str, Any]], commit_sha: str
) -> tuple[list[dict[str, Any]], list[str], list[str]]:
    selected: list[dict[str, Any]] = []
    missing: list[str] = []
    incomplete: list[str] = []

    for name in EXPECTED_WORKFLOWS:
        candidates = [
            run
            for run in runs
            if run.get("name") == name
            and run.get("head_sha") == commit_sha
            and run.get("event") == "push"
        ]
        candidates.sort(
            key=lambda run: (run.get("run_number", 0), run.get("run_attempt", 0)),
            reverse=True,
        )
        if not candidates:
            missing.append(name)
            continue
        run = candidates[0]
        normalized = {
            "id": run.get("id"),
            "name": run.get("name"),
            "event": run.get("event"),
            "head_sha": run.get("head_sha"),
            "status": run.get("status"),
            "conclusion": run.get("conclusion"),
            "run_number": run.get("run_number"),
            "run_attempt": run.get("run_attempt"),
            "created_at": run.get("created_at"),
            "updated_at": run.get("updated_at"),
            "html_url": run.get("html_url"),
        }
        selected.append(normalized)
        if run.get("status") != "completed":
            incomplete.append(name)

    selected.sort(key=lambda run: EXPECTED_WORKFLOWS.index(run["name"]))
    return selected, missing, incomplete


def collect(
    repository: str,
    commit_sha: str,
    token: str,
    timeout_seconds: int,
    interval_seconds: int,
) -> list[dict[str, Any]]:
    deadline = time.monotonic() + timeout_seconds
    last_missing: list[str] = []
    last_incomplete: list[str] = []
    while True:
        runs = fetch_runs(repository, commit_sha, token)
        selected, missing, incomplete = select_expected_runs(runs, commit_sha)
        if not missing and not incomplete:
            failures = [
                run for run in selected if run.get("conclusion") != "success"
            ]
            if failures:
                summary = ", ".join(
                    f"{run['name']}={run.get('conclusion')}" for run in failures
                )
                raise RuntimeError(f"Validation workflow failed for {commit_sha}: {summary}")
            return selected

        last_missing = missing
        last_incomplete = incomplete
        if time.monotonic() >= deadline:
            raise TimeoutError(
                "Timed out waiting for commit workflows. "
                f"Missing: {last_missing or 'none'}; "
                f"incomplete: {last_incomplete or 'none'}"
            )
        print(
            "Waiting for commit workflows; "
            f"missing={missing or 'none'}, incomplete={incomplete or 'none'}",
            flush=True,
        )
        time.sleep(interval_seconds)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", default=os.environ.get("GITHUB_REPOSITORY"))
    parser.add_argument("--commit", required=True)
    parser.add_argument("--output", type=Path, default=Path("related-workflow-runs.json"))
    parser.add_argument("--timeout-seconds", type=int, default=3600)
    parser.add_argument("--interval-seconds", type=int, default=30)
    args = parser.parse_args()

    token = os.environ.get("GITHUB_TOKEN")
    if not args.repository:
        raise SystemExit("--repository or GITHUB_REPOSITORY is required")
    if not token:
        raise SystemExit("GITHUB_TOKEN is required")

    selected = collect(
        args.repository,
        args.commit,
        token,
        args.timeout_seconds,
        args.interval_seconds,
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(selected, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
