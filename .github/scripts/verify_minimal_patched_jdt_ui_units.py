#!/usr/bin/env python3
"""Require the patched JDT UI repository to contain exactly three IUs."""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path


def repository_xml(repository: Path) -> ET.Element:
    jar = repository / "content.jar"
    xml = repository / "content.xml"
    if jar.is_file():
        with zipfile.ZipFile(jar) as archive:
            return ET.fromstring(archive.read("content.xml"))
    if xml.is_file():
        return ET.parse(xml).getroot()
    raise RuntimeError(f"Repository is missing content.jar and content.xml: {repository}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", required=True, type=Path)
    parser.add_argument("--bundle-id", required=True)
    parser.add_argument("--bundle-version", required=True)
    parser.add_argument("--feature-id", required=True)
    parser.add_argument("--feature-version", required=True)
    args = parser.parse_args()

    try:
        root = repository_xml(args.repository.resolve())
        actual = {
            (unit.attrib.get("id", ""), unit.attrib.get("version", ""))
            for unit in root.findall("./units/unit")
        }
        expected = {
            (args.bundle_id, args.bundle_version),
            (f"{args.feature_id}.feature.jar", args.feature_version),
            (f"{args.feature_id}.feature.group", args.feature_version),
        }
        if actual != expected:
            raise RuntimeError(
                f"Unexpected p2 IU set: expected={sorted(expected)}, actual={sorted(actual)}"
            )
        print(f"Verified exact p2 IU set: {sorted(actual)}")
        return 0
    except (OSError, ET.ParseError, RuntimeError, zipfile.BadZipFile, KeyError) as error:
        print(f"minimal patched JDT UI IU verification failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
