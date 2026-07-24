#!/usr/bin/env python3
"""Offline regression tests for wait_for_pages_build.py."""

from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("wait_for_pages_build.py")
SPEC = importlib.util.spec_from_file_location("wait_for_pages_build", SCRIPT)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Could not load {SCRIPT}")
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class FakeApi:
    def __init__(self, responses: list[MODULE.ApiResponse]) -> None:
        self.responses = responses
        self.requests: list[tuple[str, str]] = []

    def request(self, method: str, path: str) -> MODULE.ApiResponse:
        self.requests.append((method, path))
        if not self.responses:
            raise AssertionError("Unexpected API request")
        return self.responses.pop(0)


class PagesBuildTest(unittest.TestCase):
    def test_accepts_exact_commit_after_queued_build(self) -> None:
        expected = "a" * 40
        api = FakeApi(
            [
                MODULE.ApiResponse(200, {"commit": "b" * 40, "status": "built"}),
                MODULE.ApiResponse(200, {"commit": expected, "status": "building"}),
                MODULE.ApiResponse(200, {"commit": expected, "status": "built"}),
            ]
        )

        build, observations, requests = MODULE.await_pages_build(
            api,
            "owner/repository",
            expected,
            attempts=3,
            delay_seconds=0,
            initial_request={"statusCode": 201},
            sleep=lambda _: None,
        )

        self.assertEqual(expected, build["commit"])
        self.assertEqual(3, len(observations))
        self.assertEqual([{"statusCode": 201}], requests)

    def test_rejects_failed_exact_commit(self) -> None:
        expected = "c" * 40
        api = FakeApi(
            [MODULE.ApiResponse(200, {"commit": expected, "status": "errored", "error": {"message": "boom"}})]
        )

        with self.assertRaisesRegex(RuntimeError, "boom"):
            MODULE.await_pages_build(
                api,
                "owner/repository",
                expected,
                attempts=1,
                delay_seconds=0,
                initial_request={"statusCode": 201},
                sleep=lambda _: None,
            )

    def test_times_out_on_previous_successful_commit(self) -> None:
        expected = "d" * 40
        api = FakeApi([MODULE.ApiResponse(200, {"commit": "e" * 40, "status": "building"})])

        with self.assertRaisesRegex(RuntimeError, "Timed out"):
            MODULE.await_pages_build(
                api,
                "owner/repository",
                expected,
                attempts=1,
                delay_seconds=0,
                initial_request={"statusCode": 201},
                sleep=lambda _: None,
            )

    def test_accepts_existing_success_for_exact_commit(self) -> None:
        expected = "f" * 40
        api = FakeApi([MODULE.ApiResponse(200, {"commit": expected, "status": "built"})])

        build, observations, requests = MODULE.await_pages_build(
            api,
            "owner/repository",
            expected,
            attempts=1,
            delay_seconds=0,
            initial_request={"statusCode": 409},
            sleep=lambda _: None,
        )

        self.assertEqual(expected, build["commit"])
        self.assertEqual(1, len(observations))
        self.assertEqual([{"statusCode": 409}], requests)

    def test_requeues_after_conflicting_build_finishes(self) -> None:
        expected = "1" * 40
        other = "2" * 40
        api = FakeApi(
            [
                MODULE.ApiResponse(200, {"commit": other, "status": "built"}),
                MODULE.ApiResponse(201, {"status": "queued"}),
                MODULE.ApiResponse(200, {"commit": expected, "status": "built"}),
            ]
        )

        build, observations, requests = MODULE.await_pages_build(
            api,
            "owner/repository",
            expected,
            attempts=2,
            delay_seconds=0,
            initial_request={"statusCode": 409},
            sleep=lambda _: None,
        )

        self.assertEqual(expected, build["commit"])
        self.assertEqual(2, len(observations))
        self.assertEqual([409, 201], [request["statusCode"] for request in requests])
        self.assertIn(("POST", "repos/owner/repository/pages/builds"), api.requests)

    def test_requires_legacy_gh_pages_root_source(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "does not match"):
            MODULE.validate_pages_source({"source": {"branch": "main", "path": "/docs"}}, "gh-pages")

    def test_build_request_accepts_queued_conflict(self) -> None:
        api = FakeApi([MODULE.ApiResponse(409, {"message": "already queued"})])

        result = MODULE.request_pages_build(api, "owner/repository")

        self.assertEqual(409, result["statusCode"])


if __name__ == "__main__":
    unittest.main()
