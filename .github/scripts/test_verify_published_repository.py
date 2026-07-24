#!/usr/bin/env python3
"""Offline regression tests for verify_published_repository.py."""

from __future__ import annotations

import argparse
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("verify_published_repository.py")
SPEC = importlib.util.spec_from_file_location("verify_published_repository", SCRIPT)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Could not load {SCRIPT}")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class PublishedRepositoryVerificationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.repository = Path(self.temporary.name) / "repository"
        self.repository.mkdir()
        self.expected = Path(self.temporary.name) / "verification.json"
        self.expected.write_text(
            json.dumps(
                {
                    "publishedFeatureIds": ["feature.one", "feature.two"],
                    "publishedFeatureIUs": [
                        "feature.one.feature.group",
                        "feature.two.feature.group",
                    ],
                }
            ),
            encoding="utf-8",
        )
        self.write_repository_metadata()
        self.write_composite_metadata()

    def write_repository_metadata(self, *, include_second_artifact: bool = True) -> None:
        (self.repository / "content.xml").write_text(
            """<?xml version="1.0" encoding="UTF-8"?>
<repository>
  <units size="2">
    <unit id="feature.one.feature.group" version="1.0.0"/>
    <unit id="feature.two.feature.group" version="1.0.0"/>
  </units>
</repository>
""",
            encoding="utf-8",
        )
        second = (
            '    <artifact classifier="org.eclipse.update.feature" '
            'id="feature.two" version="1.0.0"/>\n'
            if include_second_artifact
            else ""
        )
        (self.repository / "artifacts.xml").write_text(
            """<?xml version="1.0" encoding="UTF-8"?>
<repository>
  <artifacts>
    <artifact classifier="org.eclipse.update.feature" id="feature.one" version="1.0.0"/>
"""
            + second
            + """  </artifacts>
</repository>
""",
            encoding="utf-8",
        )

    def write_composite_metadata(self, *, artifact_child: str = "1.0.0") -> None:
        (self.repository / "compositeContent.xml").write_text(
            """<?xml version="1.0" encoding="UTF-8"?>
<repository><children><child location="1.0.0"/></children></repository>
""",
            encoding="utf-8",
        )
        (self.repository / "compositeArtifacts.xml").write_text(
            f"""<?xml version="1.0" encoding="UTF-8"?>
<repository><children><child location="{artifact_child}"/></children></repository>
""",
            encoding="utf-8",
        )

    def arguments(self, *, expected_json: bool = True, expected_child: str | None = "1.0.0") -> argparse.Namespace:
        return argparse.Namespace(
            repository_url=self.repository.as_uri() + "/",
            expected_json=self.expected if expected_json else None,
            expected_child=expected_child,
            output=None,
            attempts=1,
            delay_seconds=0.0,
        )

    def test_accepts_matching_metadata_artifacts_and_composites(self) -> None:
        result = MODULE.verify(self.arguments())

        self.assertEqual(2, result["schemaVersion"])
        self.assertEqual(
            ["feature.one.feature.group", "feature.two.feature.group"],
            result["expectedFeatureIUs"],
        )
        self.assertEqual(["feature.one", "feature.two"], result["expectedFeatureArtifacts"])
        self.assertEqual(["1.0.0"], result["metadataChildren"])
        self.assertEqual(["1.0.0"], result["artifactChildren"])

    def test_rejects_feature_iu_without_published_artifact(self) -> None:
        self.write_repository_metadata(include_second_artifact=False)

        with self.assertRaisesRegex(RuntimeError, "missing feature artifacts"):
            MODULE.verify(self.arguments(expected_child=None))

    def test_rejects_child_missing_from_artifact_composite(self) -> None:
        self.write_composite_metadata(artifact_child="0.9.0")

        with self.assertRaisesRegex(RuntimeError, "artifact composite is missing child"):
            MODULE.verify(self.arguments(expected_json=False))


if __name__ == "__main__":
    unittest.main()
