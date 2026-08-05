#!/usr/bin/env python3
"""Validate the machine-readable distribution evidence produced by Maven."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any
from xml.etree import ElementTree

SHA256 = re.compile(r"[0-9a-f]{64}")


def local_name(tag: str) -> str:
    """Return an XML local name with or without a namespace."""
    return tag.rsplit("}", 1)[-1]


def expected_feature_ids(category_file: Path) -> list[str]:
    root = ElementTree.parse(category_file).getroot()
    return sorted(
        {
            element.attrib["id"]
            for element in root.iter()
            if local_name(element.tag) == "feature"
            and element.attrib.get("id", "").startswith("sandbox_")
        }
    )


def require(condition: bool, message: str, errors: list[str]) -> None:
    if not condition:
        errors.append(message)


def validate(root: Path, evidence_file: Path) -> list[str]:
    errors: list[str] = []
    try:
        document: dict[str, Any] = json.loads(evidence_file.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exception:
        return [f"Cannot read valid JSON evidence from {evidence_file}: {exception}"]

    expected = expected_feature_ids(root / "sandbox_updatesite/category.xml")
    repository = document.get("repository")
    require(isinstance(repository, dict), "repository must be an object", errors)
    entries = repository.get("publishedFeatures") if isinstance(repository, dict) else None
    require(isinstance(entries, list), "repository.publishedFeatures must be an array", errors)
    entries = entries if isinstance(entries, list) else []

    require(document.get("schemaVersion") == 3, "schemaVersion must be 3", errors)
    require(document.get("result") == "PASS", "result must be PASS", errors)
    require(bool(expected), "category.xml must declare at least one Sandbox feature", errors)
    require(
        document.get("publishedFeatureCount") == len(expected),
        "publishedFeatureCount must match category.xml",
        errors,
    )
    require(
        len(entries) == len(expected),
        "repository.publishedFeatures must contain one entry per published feature",
        errors,
    )

    actual_ids: list[str] = []
    for index, entry in enumerate(entries):
        label = f"repository.publishedFeatures[{index}]"
        if not isinstance(entry, dict):
            errors.append(f"{label} must be an object")
            continue
        feature_id = entry.get("id")
        actual_ids.append(feature_id if isinstance(feature_id, str) else "")
        require(
            isinstance(feature_id, str) and feature_id.startswith("sandbox_"),
            f"{label}.id must be a Sandbox feature id",
            errors,
        )
        require(
            entry.get("iu") == f"{feature_id}.feature.group",
            f"{label}.iu must identify the feature group",
            errors,
        )
        require(
            isinstance(entry.get("version"), str) and bool(entry["version"].strip()),
            f"{label}.version must be non-empty",
            errors,
        )
        require(
            isinstance(entry.get("artifactSize"), int) and entry["artifactSize"] > 0,
            f"{label}.artifactSize must be a positive integer",
            errors,
        )
        require(
            isinstance(entry.get("artifactSha256"), str)
            and SHA256.fullmatch(entry["artifactSha256"]) is not None,
            f"{label}.artifactSha256 must be a lowercase SHA-256 digest",
            errors,
        )

    require(len(actual_ids) == len(set(actual_ids)), "published feature ids must be unique", errors)
    require(sorted(actual_ids) == expected, "published feature ids must match category.xml", errors)
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument(
        "--evidence",
        type=Path,
        default=Path("target/distribution-verification/verification.json"),
    )
    args = parser.parse_args()
    root = args.root.resolve()
    evidence_file = args.evidence
    if not evidence_file.is_absolute():
        evidence_file = root / evidence_file

    errors = validate(root, evidence_file)
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print(f"Validated distribution evidence: {evidence_file}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
