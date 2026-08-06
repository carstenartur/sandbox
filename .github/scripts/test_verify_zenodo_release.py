#!/usr/bin/env python3
"""Tests for verify_zenodo_release.py."""

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from verify_zenodo_release import (
    ZenodoVerificationError,
    fetch_records,
    find_verified_record,
    main,
)

REPOSITORY_URL = "https://github.com/carstenartur/sandbox"


def new_api_record(
    *,
    version: str = "1.4.0",
    title: str = "Sandbox",
    concept_doi: str = "10.5281/zenodo.100",
    repository_identifier: str = REPOSITORY_URL + "/tree/v1.4.0",
) -> dict[str, object]:
    return {
        "id": "104",
        "metadata": {
            "title": title,
            "version": version,
            "related_identifiers": [
                {
                    "identifier": repository_identifier,
                    "relation": "isSupplementTo",
                }
            ],
        },
        "pids": {"doi": {"identifier": "10.5281/zenodo.104"}},
        "parent": {"pids": {"doi": {"identifier": concept_doi}}},
        "links": {"self_html": "https://zenodo.org/records/104"},
        "versions": {"is_latest": True},
    }


class ZenodoReleaseVerificationTest(unittest.TestCase):

    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def test_accepts_new_api_record_and_preserved_concept_doi(self) -> None:
        payload = {"hits": {"hits": [new_api_record()]}}

        verified = find_verified_record(
            payload,
            REPOSITORY_URL,
            "1.4.0",
            "10.5281/zenodo.100",
        )

        self.assertEqual("1.4.0", verified.version)
        self.assertEqual("10.5281/zenodo.104", verified.version_doi)
        self.assertEqual("10.5281/zenodo.100", verified.concept_doi)
        self.assertEqual("https://zenodo.org/records/104", verified.record_url)

    def test_accepts_legacy_api_record(self) -> None:
        payload = {
            "hits": {
                "hits": [
                    {
                        "id": 204,
                        "title": "Sandbox",
                        "doi": "10.5281/zenodo.204",
                        "conceptdoi": "10.5281/zenodo.200",
                        "metadata": {
                            "title": "Sandbox",
                            "version": "v1.4.0",
                            "related_identifiers": [
                                {"identifier": REPOSITORY_URL}
                            ],
                        },
                        "links": {
                            "html": "https://zenodo.org/records/204"
                        },
                    }
                ]
            }
        }

        verified = find_verified_record(payload, REPOSITORY_URL, "1.4.0")

        self.assertEqual("10.5281/zenodo.204", verified.version_doi)
        self.assertEqual("10.5281/zenodo.200", verified.concept_doi)

    def test_fetch_records_follows_zenodo_pagination(self) -> None:
        next_url = "https://zenodo.example/api/records?page=2"
        first_page = {
            "hits": {
                "hits": [new_api_record(version="1.3.9")],
                "total": 26,
            },
            "links": {"next": next_url},
        }
        second_page = {
            "hits": {
                "hits": [new_api_record()],
                "total": 26,
            },
            "links": {},
        }

        with patch(
            "verify_zenodo_release.fetch_json",
            side_effect=[first_page, second_page],
        ) as fetch_json_mock:
            fetched = fetch_records(
                "https://zenodo.example/api/records",
                REPOSITORY_URL,
                1.0,
            )

        self.assertEqual(2, len(fetched))
        self.assertEqual(2, fetch_json_mock.call_count)
        self.assertEqual(next_url, fetch_json_mock.call_args_list[1].args[0])

    def test_rejects_snapshot_metadata(self) -> None:
        payload = {
            "hits": {
                "hits": [
                    new_api_record(
                        version="1.4.0-SNAPSHOT",
                        title="Sandbox 1.4.0-SNAPSHOT",
                    )
                ]
            }
        }

        with self.assertRaisesRegex(ZenodoVerificationError, "SNAPSHOT"):
            find_verified_record(payload, REPOSITORY_URL, "1.4.0")

    def test_rejects_wrong_repository_and_concept_doi(self) -> None:
        wrong_repository = {
            "hits": {
                "hits": [
                    new_api_record(
                        repository_identifier="https://github.com/example/other"
                    )
                ]
            }
        }
        with self.assertRaisesRegex(ZenodoVerificationError, "no record linked"):
            find_verified_record(wrong_repository, REPOSITORY_URL, "1.4.0")

        wrong_concept = {"hits": {"hits": [new_api_record()]}}
        with self.assertRaisesRegex(ZenodoVerificationError, "concept DOI"):
            find_verified_record(
                wrong_concept,
                REPOSITORY_URL,
                "1.4.0",
                "10.5281/zenodo.999",
            )

    def test_cli_writes_doi_evidence(self) -> None:
        records_path = self.root / "records.json"
        records_path.write_text(
            json.dumps({"hits": {"hits": [new_api_record()]}}),
            encoding="utf-8",
        )
        output = self.root / "report.json"
        arguments = [
            "verify_zenodo_release.py",
            "--repository-url",
            REPOSITORY_URL,
            "--expected-version",
            "1.4.0",
            "--expected-tag",
            "v1.4.0",
            "--expected-concept-doi",
            "10.5281/zenodo.100",
            "--records-json",
            str(records_path),
            "--output",
            str(output),
        ]

        with patch("sys.argv", arguments):
            main()

        report = json.loads(output.read_text(encoding="utf-8"))
        self.assertEqual("PASS", report["status"])
        self.assertEqual(
            "10.5281/zenodo.104", report["record"]["version_doi"]
        )
        self.assertEqual(
            "10.5281/zenodo.100", report["record"]["concept_doi"]
        )

    def test_cli_fails_closed_and_writes_report(self) -> None:
        records_path = self.root / "records.json"
        records_path.write_text(
            json.dumps(
                {
                    "hits": {
                        "hits": [
                            new_api_record(version="1.4.0-SNAPSHOT")
                        ]
                    }
                }
            ),
            encoding="utf-8",
        )
        output = self.root / "report.json"
        arguments = [
            "verify_zenodo_release.py",
            "--repository-url",
            REPOSITORY_URL,
            "--expected-version",
            "1.4.0",
            "--expected-tag",
            "v1.4.0",
            "--records-json",
            str(records_path),
            "--output",
            str(output),
        ]

        with patch("sys.argv", arguments):
            with self.assertRaises(ZenodoVerificationError):
                main()

        report = json.loads(output.read_text(encoding="utf-8"))
        self.assertEqual("FAIL", report["status"])
        self.assertIn("SNAPSHOT", report["failure"])

    def test_cli_reports_all_exhausted_online_attempts(self) -> None:
        output = self.root / "report.json"
        arguments = [
            "verify_zenodo_release.py",
            "--repository-url",
            REPOSITORY_URL,
            "--expected-version",
            "1.4.0",
            "--expected-tag",
            "v1.4.0",
            "--api-url",
            "https://zenodo.example/api/records",
            "--max-attempts",
            "3",
            "--interval-seconds",
            "0",
            "--timeout-seconds",
            "1",
            "--output",
            str(output),
        ]

        with patch("sys.argv", arguments), patch(
            "verify_zenodo_release.fetch_records",
            side_effect=OSError("Zenodo unavailable"),
        ) as fetch_records_mock:
            with self.assertRaises(ZenodoVerificationError):
                main()

        report = json.loads(output.read_text(encoding="utf-8"))
        self.assertEqual("FAIL", report["status"])
        self.assertEqual(3, report["attempts"])
        self.assertEqual(3, fetch_records_mock.call_count)
        self.assertIn("after 3 attempts", report["failure"])


if __name__ == "__main__":
    unittest.main()
