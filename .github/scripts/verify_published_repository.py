#!/usr/bin/env python3
"""Verify feature IUs or composite children at a published p2 repository URL."""

from __future__ import annotations

import argparse
import io
import json
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path
from xml.etree import ElementTree as ET


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository-url", required=True)
    parser.add_argument("--expected-json", type=Path)
    parser.add_argument("--expected-child")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--attempts", type=int, default=12)
    parser.add_argument("--delay-seconds", type=float, default=10.0)
    return parser.parse_args()


def fetch(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": "sandbox-distribution-verifier/1"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read()


def load_xml(repository_url: str, composite: bool) -> tuple[ET.Element, str]:
    repository_url = repository_url.rstrip("/") + "/"
    base_name = "compositeContent" if composite else "content"
    failures: list[str] = []
    for suffix in (".jar", ".xml"):
        url = urllib.parse.urljoin(repository_url, base_name + suffix)
        try:
            payload = fetch(url)
            if suffix == ".jar":
                with zipfile.ZipFile(io.BytesIO(payload)) as archive:
                    payload = archive.read(base_name + ".xml")
            return ET.fromstring(payload), url
        except (OSError, KeyError, ET.ParseError, urllib.error.URLError, zipfile.BadZipFile) as error:
            failures.append(f"{url}: {error}")
    raise RuntimeError("; ".join(failures))


def expected_feature_ius(path: Path) -> list[str]:
    with path.open(encoding="utf-8") as stream:
        report = json.load(stream)
    values = report.get("publishedFeatureIUs")
    if not isinstance(values, list) or not all(isinstance(value, str) for value in values):
        raise ValueError(f"{path} does not contain a publishedFeatureIUs string list")
    return sorted(set(values))


def verify(args: argparse.Namespace) -> dict[str, object]:
    if not args.expected_json and not args.expected_child:
        raise ValueError("Specify --expected-json and/or --expected-child")

    result: dict[str, object] = {
        "schemaVersion": 1,
        "repositoryUrl": args.repository_url.rstrip("/") + "/",
    }

    if args.expected_json:
        root, metadata_url = load_xml(args.repository_url, composite=False)
        available = {unit.attrib.get("id") for unit in root.findall(".//unit")}
        expected = expected_feature_ius(args.expected_json)
        missing = [iu for iu in expected if iu not in available]
        if missing:
            raise RuntimeError(f"Published repository is missing feature IUs: {missing}")
        result.update(
            {
                "metadataUrl": metadata_url,
                "expectedFeatureIUs": expected,
                "metadataUnitCount": len(available),
            }
        )

    if args.expected_child:
        root, metadata_url = load_xml(args.repository_url, composite=True)
        children = sorted(
            child.attrib["location"]
            for child in root.findall(".//child")
            if child.attrib.get("location")
        )
        normalized_expected = args.expected_child.strip("/")
        normalized_children = {child.strip("/") for child in children}
        if normalized_expected not in normalized_children:
            raise RuntimeError(
                f"Published composite is missing child {args.expected_child!r}; children={children}"
            )
        result.update(
            {
                "compositeMetadataUrl": metadata_url,
                "expectedChild": args.expected_child,
                "children": children,
            }
        )

    return result


def main() -> int:
    args = parse_args()
    if args.attempts < 1:
        raise ValueError("--attempts must be at least 1")

    last_error: Exception | None = None
    for attempt in range(1, args.attempts + 1):
        try:
            result = verify(args)
            result["attempt"] = attempt
            rendered = json.dumps(result, indent=2, sort_keys=True) + "\n"
            print(rendered, end="")
            if args.output:
                args.output.parent.mkdir(parents=True, exist_ok=True)
                args.output.write_text(rendered, encoding="utf-8")
            return 0
        except Exception as error:  # Deliberately retry transient CDN/publication failures.
            last_error = error
            if attempt == args.attempts:
                break
            print(f"Attempt {attempt}/{args.attempts} failed: {error}")
            time.sleep(args.delay_seconds)

    raise SystemExit(f"Published repository verification failed: {last_error}")


if __name__ == "__main__":
    raise SystemExit(main())
