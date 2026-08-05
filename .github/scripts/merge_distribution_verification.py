#!/usr/bin/env python3
"""Merge exact p2 artifact evidence into runtime distribution verification."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


def load_json_object(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as stream:
        value = json.load(stream)
    if not isinstance(value, dict):
        raise ValueError(f"{path} must contain a JSON object")
    return value


def merge_evidence(
    runtime_evidence: dict[str, Any], artifact_evidence: dict[str, Any]
) -> dict[str, Any]:
    repository = artifact_evidence.get("repository")
    if not isinstance(repository, dict):
        raise ValueError("Artifact evidence does not contain a repository object")

    published_features = repository.get("publishedFeatures")
    if not isinstance(published_features, list) or not published_features:
        raise ValueError("Artifact evidence does not contain repository.publishedFeatures")

    published_feature_count = runtime_evidence.get("publishedFeatureCount")
    if (
        isinstance(published_feature_count, int)
        and published_feature_count != len(published_features)
    ):
        raise ValueError(
            "Runtime and artifact evidence disagree about the published feature count: "
            f"{published_feature_count} != {len(published_features)}"
        )

    schema_version = runtime_evidence.get("schemaVersion", 0)
    if not isinstance(schema_version, int):
        raise ValueError("Runtime evidence schemaVersion must be an integer")

    merged = dict(runtime_evidence)
    merged["schemaVersion"] = max(schema_version, 3)
    merged["repository"] = repository
    for key in ("publishedFeatureIds", "publishedFeatureIUs", "targetRepositories"):
        if key in artifact_evidence:
            merged[key] = artifact_evidence[key]
    return merged


def write_merged_evidence(
    runtime_path: Path, artifact_path: Path
) -> dict[str, Any]:
    merged = merge_evidence(
        load_json_object(runtime_path), load_json_object(artifact_path)
    )
    temporary_path = runtime_path.with_suffix(runtime_path.suffix + ".tmp")
    temporary_path.write_text(
        json.dumps(merged, indent=2) + "\n", encoding="utf-8"
    )
    temporary_path.replace(runtime_path)
    return merged


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--runtime-json", type=Path, required=True)
    parser.add_argument("--artifact-json", type=Path, required=True)
    args = parser.parse_args()

    merged = write_merged_evidence(args.runtime_json, args.artifact_json)
    print(json.dumps(merged, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
