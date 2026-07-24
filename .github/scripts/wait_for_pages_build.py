#!/usr/bin/env python3
"""Request and await a legacy GitHub Pages build for one exact commit."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable

API_VERSION = "2022-11-28"
TERMINAL_FAILURE_STATES = {"error", "errored", "failed"}
TERMINAL_STATES = {"built", *TERMINAL_FAILURE_STATES}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", required=True, help="Repository in owner/name form")
    parser.add_argument("--expected-commit", required=True)
    parser.add_argument("--expected-source-branch", default="gh-pages")
    parser.add_argument("--attempts", type=int, default=90)
    parser.add_argument("--delay-seconds", type=float, default=10.0)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--api-url", default=os.environ.get("GITHUB_API_URL", "https://api.github.com"))
    return parser.parse_args()


@dataclass(frozen=True)
class ApiResponse:
    status: int
    payload: object


class GitHubApi:
    def __init__(self, api_url: str, token: str) -> None:
        self.api_url = api_url.rstrip("/")
        self.token = token

    def request(self, method: str, path: str) -> ApiResponse:
        request = urllib.request.Request(
            f"{self.api_url}/{path.lstrip('/')}",
            data=b"" if method != "GET" else None,
            method=method,
            headers={
                "Accept": "application/vnd.github+json",
                "Authorization": f"Bearer {self.token}",
                "User-Agent": "sandbox-pages-build-verifier/1",
                "X-GitHub-Api-Version": API_VERSION,
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                body = response.read()
                payload: object = json.loads(body) if body else None
                return ApiResponse(response.status, payload)
        except urllib.error.HTTPError as error:
            body = error.read()
            payload = json.loads(body) if body else None
            return ApiResponse(error.code, payload)


def require_object(value: object, description: str) -> dict[str, object]:
    if not isinstance(value, dict):
        raise RuntimeError(f"{description} did not return a JSON object")
    return value


def error_message(payload: object) -> str:
    if isinstance(payload, dict):
        message = payload.get("message")
        if isinstance(message, str) and message:
            return message
    return repr(payload)


def request_pages_build(api: GitHubApi, repository: str) -> dict[str, object]:
    response = api.request("POST", f"repos/{repository}/pages/builds")
    if response.status not in {201, 409}:
        raise RuntimeError(
            f"Could not request GitHub Pages build: HTTP {response.status}: {error_message(response.payload)}"
        )
    if response.payload is None:
        return {"statusCode": response.status}
    result = require_object(response.payload, "Pages build request")
    return {"statusCode": response.status, **result}


def get_pages_site(api: GitHubApi, repository: str) -> dict[str, object]:
    response = api.request("GET", f"repos/{repository}/pages")
    if response.status != 200:
        raise RuntimeError(
            f"Could not read GitHub Pages configuration: HTTP {response.status}: {error_message(response.payload)}"
        )
    return require_object(response.payload, "Pages site lookup")


def get_latest_build(api: GitHubApi, repository: str) -> dict[str, object]:
    response = api.request("GET", f"repos/{repository}/pages/builds/latest")
    if response.status != 200:
        raise RuntimeError(
            f"Could not read latest GitHub Pages build: HTTP {response.status}: {error_message(response.payload)}"
        )
    return require_object(response.payload, "Latest Pages build lookup")


def validate_pages_source(site: dict[str, object], expected_branch: str) -> None:
    source = site.get("source")
    if not isinstance(source, dict):
        raise RuntimeError("GitHub Pages is not configured with a legacy branch source")
    actual_branch = source.get("branch")
    actual_path = source.get("path")
    if actual_branch != expected_branch or actual_path != "/":
        raise RuntimeError(
            "GitHub Pages source does not match the release publisher: "
            f"expected {expected_branch!r} at '/', found branch={actual_branch!r}, path={actual_path!r}"
        )


def await_pages_build(
    api: GitHubApi,
    repository: str,
    expected_commit: str,
    attempts: int,
    delay_seconds: float,
    initial_request: dict[str, object],
    sleep: Callable[[float], None] = time.sleep,
) -> tuple[dict[str, object], list[dict[str, object]], list[dict[str, object]]]:
    observations: list[dict[str, object]] = []
    requests = [initial_request]
    outstanding_status = initial_request.get("statusCode")
    for attempt in range(1, attempts + 1):
        build = get_latest_build(api, repository)
        commit = build.get("commit")
        status = build.get("status")
        error = build.get("error")
        observation = {
            "attempt": attempt,
            "commit": commit,
            "status": status,
            "error": error,
            "createdAt": build.get("created_at"),
            "updatedAt": build.get("updated_at"),
        }
        observations.append(observation)
        print(
            f"Pages build attempt {attempt}/{attempts}: "
            f"commit={commit or 'unknown'} status={status or 'unknown'}"
        )
        if commit == expected_commit and status == "built":
            return build, observations, requests
        if commit == expected_commit and status in TERMINAL_FAILURE_STATES:
            raise RuntimeError(f"GitHub Pages build for {expected_commit} failed: {error_message(error)}")

        # HTTP 409 means another Pages build was already queued. Once that build
        # reaches a terminal state, explicitly request ours again instead of
        # waiting forever for a commit that was never queued.
        if outstanding_status == 409 and status in TERMINAL_STATES and commit != expected_commit:
            follow_up = request_pages_build(api, repository)
            requests.append(follow_up)
            outstanding_status = follow_up.get("statusCode")

        if attempt < attempts:
            sleep(delay_seconds)
    latest = observations[-1] if observations else {}
    raise RuntimeError(
        f"Timed out waiting for GitHub Pages build of {expected_commit}; latest={latest}"
    )


def main() -> int:
    args = parse_args()
    if not re.fullmatch(r"[0-9a-fA-F]{40}", args.expected_commit):
        raise ValueError("--expected-commit must be a 40-character Git commit SHA")
    if not re.fullmatch(r"[^/\s]+/[^/\s]+", args.repository):
        raise ValueError("--repository must use owner/name form")
    if args.attempts < 1:
        raise ValueError("--attempts must be at least 1")
    if args.delay_seconds < 0:
        raise ValueError("--delay-seconds may not be negative")

    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if not token:
        raise RuntimeError("GH_TOKEN or GITHUB_TOKEN is required")

    api = GitHubApi(args.api_url, token)
    report: dict[str, object] = {
        "schemaVersion": 1,
        "repository": args.repository,
        "expectedCommit": args.expected_commit,
        "expectedSourceBranch": args.expected_source_branch,
    }
    try:
        site = get_pages_site(api, args.repository)
        report["site"] = site
        validate_pages_source(site, args.expected_source_branch)
        report["requestedAt"] = datetime.now(timezone.utc).isoformat()
        initial_request = request_pages_build(api, args.repository)
        build, observations, requests = await_pages_build(
            api,
            args.repository,
            args.expected_commit,
            args.attempts,
            args.delay_seconds,
            initial_request,
        )
        report["requests"] = requests
        report["observations"] = observations
        report["build"] = build
        report["result"] = "PASS"
        rendered = json.dumps(report, indent=2, sort_keys=True) + "\n"
        print(rendered, end="")
        if args.output:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(rendered, encoding="utf-8")
        return 0
    except Exception as error:
        report["result"] = "FAIL"
        report["failure"] = str(error)
        rendered = json.dumps(report, indent=2, sort_keys=True) + "\n"
        if args.output:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(rendered, encoding="utf-8")
        print(f"GitHub Pages build verification failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
