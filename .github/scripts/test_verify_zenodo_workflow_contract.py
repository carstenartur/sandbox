#!/usr/bin/env python3
"""Contract tests for the checkout-free Zenodo verification workflow."""

from __future__ import annotations

import unittest
from pathlib import Path


class ZenodoWorkflowContractTest(unittest.TestCase):

    def test_release_upload_selects_repository_explicitly(self) -> None:
        workflow = (
            Path(__file__).resolve().parents[1]
            / "workflows"
            / "verify-zenodo-release.yml"
        ).read_text(encoding="utf-8")

        self.assertIn(
            '--clobber --repo "$GITHUB_REPOSITORY"',
            workflow,
        )


if __name__ == "__main__":
    unittest.main()
