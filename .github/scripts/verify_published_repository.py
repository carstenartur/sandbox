#!/usr/bin/env python3
"""Verify feature IUs and composite children at a published p2 repository URL."""

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


def load_xml(repository_url: str, base_name: str) -> tuple[ET.Element, str]:
    repository_url = repository_url.rstrip("/") + "/"
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


def expected_features(path: Path) -> tuple[list[str], list[str]]:
    with path.open(encoding="utf-8") as stream:
        report = json.load(stream)
    ius = report.get("publishedFeatureIUs")
    identifiers = report.get("publishedFeatureIds")
    if not isinstance(ius, list) or not all(isinstance(value, str) for value in ius):
        raise ValueError(f"{path} does not contain a publishedFeatureIUs string list")
    if not isinstance(identifiers, list) or not all(isinstance(value, str) for value in identifiers):
        raise ValueError(f"{path} does not contain a publishedFeatureIds string list")
    return sorted(set(ius)), sorted(set(identifiers))


def composite_children(root: ET.Element) -> list[str]:
    return sorted(
        child.attrib["location"]
        for child in root.findall(".//child")
        if child.attrib.get("location")
    )


def verify(args: argparse.Namespace) -> dict[str, object]:
    if not args.expected_json and not args.expected_child:
        raise ValueError("Specify --expected-json and/or --expected-child")

    result: dict[str, object] = {
        "schemaVersion": 2,
        "repositoryUrl": args.repository_url.rstrip("/") + "/",
    }

    if args.expected_json:
        content, content_url = load_xml(args.repository_url, "content")
        artifacts, artifacts_url = load_xml(args.repository_url, "artifacts")
        available_ius = {
            unit.attrib.get("id")
            for unit in content.findall(".//unit")
            if unit.attrib.get("id")
        }
        available_feature_artifacts = {
            artifact.attrib.get("id")
            for artifact in artifacts.findall(".//artifact")
            if artifact.attrib.get("classifier") == "org.eclipse.update.feature"
            and artifact.attrib.get("id")
        }
        expected_ius, expected_ids = expected_features(args.expected_json)
        missing_ius = [iu for iu in expected_ius if iu not in available_ius]
        missing_artifacts = [feature for feature in expected_ids if feature not in available_feature_artifacts]
        if missing_ius:
            raise RuntimeError(f"Published repository is missing feature IUs: {missing_ius}")
        if missing_artifacts:
            raise RuntimeError(f"Published repository is missing feature artifacts: {missing_artifacts}")
        result.update(
            {
                "contentMetadataUrl": content_url,
                "artifactMetadataUrl": artifacts_url,
                "expectedFeatureIUs": expected_ius,
                "expectedFeatureArtifacts": expected_ids,
                "metadataUnitCount": len(available_ius),
                "artifactKeyCount": len(artifacts.findall(".//artifact")),
            }
        )

    if args.expected_child:
        content, content_url = load_xml(args.repository_url, "compositeContent")
        artifacts, artifacts_url = load_xml(args.repository_url, "compositeArtifacts")
        content_children = composite_children(content)
        artifact_children = composite_children(artifacts)
        normalized_expected = args.expected_child.strip("/")
        normalized_content = {child.strip("/") for child in content_children}
        normalized_artifacts = {child.strip("/") for child in artifact_children}
        if normalized_expected not in normalized_content:
            raise RuntimeError(
                f"Published metadata composite is missing child {args.expected_child!r}; "
                f"children={content_children}"
            )
        if normalized_expected not in normalized_artifacts:
            raise RuntimeError(
                f"Published artifact composite is missing child {args.expected_child!r}; "
                f"children={artifact_children}"
            )
        result.update(
            {
                "compositeContentUrl": content_url,
                "compositeArtifactsUrl": artifacts_url,
                "expectedChild": args.expected_child,
                "metadataChildren": content_children,
                "artifactChildren": artifact_children,
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
