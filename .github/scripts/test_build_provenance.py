#!/usr/bin/env python3
"""Unit tests for the build provenance scripts."""

from __future__ import annotations

import importlib.util
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

SCRIPT_DIR = Path(__file__).parent


def load_module(name: str, filename: str):
    specification = importlib.util.spec_from_file_location(name, SCRIPT_DIR / filename)
    if specification is None or specification.loader is None:
        raise RuntimeError(f"Could not load {filename}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


manifest_module = load_module("generate_build_manifest", "generate_build_manifest.py")
workflow_module = load_module("collect_commit_workflows", "collect_commit_workflows.py")


class BuildManifestTest(unittest.TestCase):

    def test_manifest_contains_versions_tests_targets_and_distribution_digests(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            (root / "pom.xml").write_text(
                """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.sandbox</groupId>
  <artifactId>central</artifactId>
  <version>1.3.2-SNAPSHOT</version>
  <properties>
    <tycho-version>5.0.3</tycho-version>
    <java-version>21</java-version>
    <java-execenv>JavaSE-21</java-execenv>
    <spotbugs-version>4.10.3.0</spotbugs-version>
  </properties>
  <repositories>
    <repository><url>https://download.eclipse.org/releases/2025-12/</url></repository>
  </repositories>
</project>
""",
                encoding="utf-8",
            )
            target_dir = root / "sandbox_target"
            target_dir.mkdir()
            (target_dir / "sandbox.target").write_text("target", encoding="utf-8")
            (root / "test-report.json").write_text(
                json.dumps(
                    {
                        "summary": {
                            "total_modules": 2,
                            "total_tests": 10,
                            "enabled_tests": 9,
                            "disabled_tests": 1,
                        }
                    }
                ),
                encoding="utf-8",
            )
            reports = root / "module" / "target" / "surefire-reports"
            reports.mkdir(parents=True)
            (reports / "TEST-example.xml").write_text(
                '<testsuite tests="3" failures="1" errors="0" skipped="1"/>',
                encoding="utf-8",
            )
            products = root / "sandbox_product" / "target" / "products"
            products.mkdir(parents=True)
            (products / "sandbox.zip").write_bytes(b"product")
            update_site = root / "sandbox_updatesite" / "target" / "repository"
            update_site.mkdir(parents=True)
            (update_site / "content.jar").write_bytes(b"content")
            (update_site / "plugins").mkdir()
            (update_site / "plugins" / "bundle.jar").write_bytes(b"bundle")
            workflow_file = root / "related.json"
            workflow_file.write_text(
                json.dumps([{"id": 7, "name": "Java CI with Maven", "conclusion": "success"}]),
                encoding="utf-8",
            )

            environment = {
                "GITHUB_REPOSITORY": "carstenartur/sandbox",
                "GITHUB_SHA": "workflow-context-sha",
                "GITHUB_SERVER_URL": "https://github.com",
                "GITHUB_RUN_ID": "99",
                "GITHUB_WORKFLOW": "Post-merge Build Provenance",
            }
            with patch.dict(os.environ, environment, clear=False), patch.object(
                manifest_module, "command_version", return_value="tool version"
            ):
                manifest = manifest_module.build_manifest(
                    root,
                    workflow_file,
                    commit_sha="validated-main-sha",
                    source_ref="refs/heads/main",
                )

            self.assertEqual("validated-main-sha", manifest["commit_sha"])
            self.assertEqual("refs/heads/main", manifest["ref"])
            self.assertEqual("5.0.3", manifest["toolchain"]["tycho_version"])
            self.assertEqual("2025-12", manifest["toolchain"]["eclipse_release"])
            self.assertEqual(10, manifest["tests"]["inventory"]["total_tests"])
            self.assertEqual(3, manifest["tests"]["executed"]["tests"])
            self.assertEqual(1, manifest["tests"]["executed"]["failures"])
            self.assertEqual("built", manifest["artifacts"]["product_status"])
            self.assertEqual(1, manifest["artifacts"]["product_tree"]["file_count"])
            self.assertEqual("built", manifest["artifacts"]["update_site_status"])
            self.assertEqual(2, manifest["artifacts"]["update_site"]["file_count"])
            self.assertEqual(1, len(manifest["toolchain"]["target_definitions"]))
            self.assertEqual(7, manifest["related_workflows"][0]["id"])

    def test_tree_digest_is_path_order_independent(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            (root / "b.txt").write_text("b", encoding="utf-8")
            (root / "a.txt").write_text("a", encoding="utf-8")
            first = manifest_module.tree_digest(root)
            second = manifest_module.tree_digest(root)
            self.assertEqual(first["sha256_tree"], second["sha256_tree"])
            self.assertEqual(2, first["file_count"])


class WorkflowSelectionTest(unittest.TestCase):

    def test_selects_latest_push_run_for_exact_commit(self):
        commit = "abc123"
        runs = []
        for name in workflow_module.EXPECTED_WORKFLOWS:
            runs.append(
                {
                    "id": len(runs) + 1,
                    "name": name,
                    "head_sha": commit,
                    "event": "push",
                    "status": "completed",
                    "conclusion": "success",
                    "run_number": 10,
                    "run_attempt": 1,
                    "html_url": f"https://example/{name}",
                }
            )
        runs.append(
            {
                "id": 100,
                "name": "Java CI with Maven",
                "head_sha": commit,
                "event": "pull_request",
                "status": "completed",
                "conclusion": "failure",
                "run_number": 99,
                "run_attempt": 1,
            }
        )
        runs.append(
            {
                "id": 101,
                "name": "Java CI with Maven",
                "head_sha": commit,
                "event": "push",
                "status": "completed",
                "conclusion": "success",
                "run_number": 10,
                "run_attempt": 2,
            }
        )

        selected, missing, incomplete = workflow_module.select_expected_runs(runs, commit)

        self.assertEqual([], missing)
        self.assertEqual([], incomplete)
        self.assertEqual(101, selected[0]["id"])
        self.assertEqual(list(workflow_module.EXPECTED_WORKFLOWS), [run["name"] for run in selected])

    def test_reports_missing_and_incomplete_workflows(self):
        selected, missing, incomplete = workflow_module.select_expected_runs(
            [
                {
                    "id": 1,
                    "name": "Java CI with Maven",
                    "head_sha": "abc123",
                    "event": "push",
                    "status": "in_progress",
                    "conclusion": None,
                    "run_number": 1,
                    "run_attempt": 1,
                }
            ],
            "abc123",
        )
        self.assertEqual(1, len(selected))
        self.assertIn("Core Module Build", missing)
        self.assertEqual(["Java CI with Maven"], incomplete)


if __name__ == "__main__":
    unittest.main()
