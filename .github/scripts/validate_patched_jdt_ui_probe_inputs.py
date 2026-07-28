#!/usr/bin/env python3
"""Validate that runtime-probe inputs refer to one exact patched JDT UI artifact."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def read_object(path: Path) -> dict[str, object]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise SystemExit(f"Expected a JSON object in {path}")
    return value


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--p2-root", required=True, type=Path)
    parser.add_argument("--installation", required=True, type=Path)
    args = parser.parse_args()

    p2_root = args.p2_root.resolve()
    repository_dir = p2_root / "repository"
    repository_report_path = p2_root / "evidence" / "repository-verification.json"
    if not repository_dir.is_dir():
        raise SystemExit(f"Missing downloaded p2 repository: {repository_dir}")
    if not repository_report_path.is_file():
        raise SystemExit(f"Missing repository verification: {repository_report_path}")
    if not args.installation.is_file():
        raise SystemExit(f"Missing installation verification: {args.installation}")

    repository = read_object(repository_report_path)
    installation = read_object(args.installation)
    if repository.get("result") != "PASS":
        raise SystemExit("p2 repository verification is not PASS")
    if installation.get("result") != "PASS":
        raise SystemExit("patched-product installation verification is not PASS")

    repository_bundle = repository.get("bundle")
    installed_bundle = installation.get("patchedBundle")
    repository_feature = repository.get("feature")
    installed_feature = installation.get("feature")
    if not isinstance(repository_bundle, dict) or not isinstance(installed_bundle, dict):
        raise SystemExit("Bundle verification objects are missing")
    if not isinstance(repository_feature, dict) or not isinstance(installed_feature, dict):
        raise SystemExit("Feature verification objects are missing")

    for key in ("id", "version", "sha256"):
        if repository_bundle.get(key) != installed_bundle.get(key):
            raise SystemExit(
                f"Installed bundle {key} differs from the supplied p2 repository: "
                f"{installed_bundle.get(key)!r} != {repository_bundle.get(key)!r}"
            )
    if installed_bundle.get("installedSha256") != repository_bundle.get("sha256"):
        raise SystemExit("Installed bundle bytes differ from the supplied p2 repository")
    if repository_feature.get("groupIU") != installed_feature.get("groupIU"):
        raise SystemExit("Installed feature group differs from the supplied p2 repository")
    if repository_feature.get("version") != installed_feature.get("version"):
        raise SystemExit("Installed feature version differs from the supplied p2 repository")

    # repository-verification.json is created on another runner, so its absolute
    # repository path is intentionally not compared. Artifact identity is the
    # portable trust boundary across download jobs.
    print(
        json.dumps(
            {
                "schemaVersion": 2,
                "result": "PASS",
                "downloadedRepository": str(repository_dir),
                "publisherRepository": repository.get("repository"),
                "bundle": repository_bundle,
                "feature": repository_feature,
            },
            indent=2,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
