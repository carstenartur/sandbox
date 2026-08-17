#!/usr/bin/env python3
"""Contract tests for the checkout-free Zenodo verification workflow."""

from __future__ import annotations

import re
import unittest
from pathlib import Path


class ZenodoWorkflowContractTest(unittest.TestCase):

    def test_release_upload_selects_repository_explicitly(self) -> None:
        command = self._release_upload_command(self._workflow_source())

        self.assertIn('--repo "$GITHUB_REPOSITORY"', command)

    def test_workflow_remains_checkout_free(self) -> None:
        workflow = self._workflow_source()

        self.assertNotRegex(
            workflow,
            re.compile(r"^\s*uses:\s*actions/checkout@", re.MULTILINE),
        )

    @staticmethod
    def _workflow_source() -> str:
        return (
            Path(__file__).resolve().parents[1]
            / "workflows"
            / "verify-zenodo-release.yml"
        ).read_text(encoding="utf-8")

    @staticmethod
    def _release_upload_command(workflow: str) -> str:
        lines = workflow.splitlines()
        for index, raw_line in enumerate(lines):
            line = raw_line.strip()
            if not line.startswith("gh release upload "):
                continue

            parts: list[str] = []
            while True:
                continued = line.endswith("\\")
                parts.append(line[:-1].rstrip() if continued else line)
                if not continued:
                    return " ".join(parts)
                index += 1
                if index >= len(lines):
                    raise AssertionError("gh release upload command ends with a dangling continuation")
                line = lines[index].strip()

        raise AssertionError("Workflow does not contain a gh release upload command")


if __name__ == "__main__":
    unittest.main()
