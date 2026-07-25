#!/usr/bin/env python3
"""Tests for verify_release_identity.py."""

from __future__ import annotations

import json
import tarfile
import tempfile
import unittest
import zipfile
from pathlib import Path

from verify_release_identity import ReleaseIdentityError, main


class ReleaseIdentityTest(unittest.TestCase):

    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        (self.root / "pom.xml").write_text(
            "<project><version>1.4.0</version></project>\n", encoding="utf-8"
        )
        (self.root / "plugin").mkdir()
        (self.root / "plugin" / "MANIFEST.MF").write_text(
            "Bundle-Version: 1.4.0.qualifier\n", encoding="utf-8"
        )

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def invoke(self, *extra: str) -> dict[str, object]:
        import sys
        from unittest.mock import patch

        output = self.root / "report.json"
        arguments = [
            "verify_release_identity.py",
            "--root",
            str(self.root),
            "--expected-version",
            "1.4.0",
            "--forbidden-version",
            "1.4.0-SNAPSHOT",
            "--output",
            str(output),
            *extra,
        ]
        with patch.object(sys, "argv", arguments):
            main()
        return json.loads(output.read_text(encoding="utf-8"))

    def test_accepts_stable_repository_and_archives(self) -> None:
        tar_path = self.root / "source.tar.gz"
        with tarfile.open(tar_path, "w:gz") as archive:
            archive.add(self.root / "pom.xml", arcname="sandbox-1.4.0/pom.xml")
        zip_path = self.root / "source.zip"
        with zipfile.ZipFile(zip_path, "w") as archive:
            archive.write(self.root / "pom.xml", "sandbox-1.4.0/pom.xml")

        report = self.invoke("--archive", str(tar_path), "--archive", str(zip_path))

        self.assertEqual("PASS", report["status"])
        self.assertEqual([], report["findings"])

    def test_rejects_snapshot_in_repository_metadata(self) -> None:
        (self.root / "plugin" / "feature.xml").write_text(
            '<feature version="1.4.0-SNAPSHOT"/>\n', encoding="utf-8"
        )

        with self.assertRaises(ReleaseIdentityError):
            self.invoke()

        report = json.loads((self.root / "report.json").read_text(encoding="utf-8"))
        self.assertEqual("FAIL", report["status"])
        self.assertEqual("plugin/feature.xml", report["findings"][0]["path"])

    def test_rejects_snapshot_in_source_archive(self) -> None:
        snapshot = self.root / "snapshot-pom.xml"
        snapshot.write_text(
            "<project><version>1.4.0-SNAPSHOT</version></project>\n", encoding="utf-8"
        )
        archive_path = self.root / "source.zip"
        with zipfile.ZipFile(archive_path, "w") as archive:
            archive.write(snapshot, "sandbox-1.4.0/pom.xml")

        with self.assertRaises(ReleaseIdentityError):
            self.invoke("--archive", str(archive_path))

    def test_rejects_mismatched_stable_and_snapshot_versions(self) -> None:
        import sys
        from unittest.mock import patch

        with patch.object(
            sys,
            "argv",
            [
                "verify_release_identity.py",
                "--root",
                str(self.root),
                "--expected-version",
                "1.4.1",
                "--forbidden-version",
                "1.4.0-SNAPSHOT",
                "--output",
                str(self.root / "report.json"),
            ],
        ):
            with self.assertRaises(ReleaseIdentityError):
                main()


if __name__ == "__main__":
    unittest.main()
