#!/usr/bin/env python3
"""Regression tests for freely selectable next release versions."""

from __future__ import annotations

import importlib.util
from pathlib import Path
import unittest

SCRIPT = Path(__file__).with_name("release-version-plan.py")
SPEC = importlib.util.spec_from_file_location("release_version_plan", SCRIPT)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Cannot load {SCRIPT}")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ReleaseVersionPlanTest(unittest.TestCase):
    def test_exact_major_version_is_preserved(self) -> None:
        plan = MODULE.resolve_plan("1.2.9", " 2.0.0-SNAPSHOT ", "patch")
        self.assertEqual("2.0.0-SNAPSHOT", plan["next"])
        self.assertEqual("2.0.0", plan["next_release"])

    def test_arbitrary_later_version_is_allowed(self) -> None:
        plan = MODULE.resolve_plan("1.2.9", "3.7.4-SNAPSHOT", "minor")
        self.assertEqual("3.7.4-SNAPSHOT", plan["next"])

    def test_exact_version_overrides_increment(self) -> None:
        plan = MODULE.resolve_plan("1.2.9", "2.1.3-SNAPSHOT", "not-used")
        self.assertEqual("2.1.3-SNAPSHOT", plan["next"])

    def test_empty_exact_value_uses_patch_fallback(self) -> None:
        plan = MODULE.resolve_plan("1.2.9", "   ", "patch")
        self.assertEqual("1.2.10-SNAPSHOT", plan["next"])

    def test_minor_and_major_fallbacks_are_available(self) -> None:
        self.assertEqual(
            "1.3.0-SNAPSHOT", MODULE.resolve_plan("1.2.9", "", "minor")["next"]
        )
        self.assertEqual(
            "2.0.0-SNAPSHOT", MODULE.resolve_plan("1.2.9", "", "major")["next"]
        )

    def test_next_version_must_be_newer(self) -> None:
        with self.assertRaisesRegex(ValueError, "must be newer"):
            MODULE.resolve_plan("1.2.9", "1.2.9-SNAPSHOT")

    def test_malformed_version_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "X.Y.Z-SNAPSHOT"):
            MODULE.resolve_plan("1.2.9", "2.0-SNAPSHOT")


if __name__ == "__main__":
    unittest.main()
