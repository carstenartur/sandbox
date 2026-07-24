#!/usr/bin/env python3
"""Verify exact feature artifacts and composite children at a published p2 URL."""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
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


def expected_features(path: Path) -> list[dict[str, object]]:
    with path.open(encoding="utf-8") as stream:
        report = json.load(stream)
    repository = report.get("repository")
    values = repository.get("publishedFeatures") if isinstance(repository, dict) else None
    if not isinstance(values, list) or not values:
        raise ValueError(f"{path} does not contain repository.publishedFeatures evidence")

    normalized: list[dict[str, object]] = []
    for value in values:
        if not isinstance(value, dict):
            raise ValueError(f"{path} contains a non-object published feature entry")
        identifier = value.get("id")
        iu = value.get("iu")
        version = value.get("version")
        size = value.get("artifactSize")
        sha256 = value.get("artifactSha256")
        if not all(isinstance(item, str) and item for item in (identifier, iu, version)):
            raise ValueError(f"{path} contains incomplete published feature identity evidence")
        if not isinstance(size, int) or size <= 0:
            raise ValueError(f"{path} contains an invalid published feature artifact size")
        if not isinstance(sha256, str) or not re.fullmatch(r"[0-9a-fA-F]{64}", sha256):
            raise ValueError(f"{path} contains an invalid published feature SHA-256")
        normalized.append(
            {
                "id": identifier,
                "iu": iu,
                "version": version,
                "artifactSize": size,
                "artifactSha256": sha256.lower(),
            }
        )

    identities = [(item["id"], item["iu"], item["version"]) for item in normalized]
    if len(identities) != len(set(identities)):
        raise ValueError(f"{path} contains duplicate published feature evidence")
    return sorted(normalized, key=lambda item: (str(item["id"]), str(item["version"])))


def composite_children(root: ET.Element) -> list[str]:
    return sorted(
        child.attrib["location"]
        for child in root.findall(".//child")
        if child.attrib.get("location")
    )


def verify_feature_artifact(repository_url: str, expected: dict[str, object]) -> dict[str, object]:
    identifier = str(expected["id"])
    version = str(expected["version"])
    filename = urllib.parse.quote(f"{identifier}_{version}.jar")
    url = urllib.parse.urljoin(repository_url.rstrip("/") + "/", f"features/{filename}")
    payload = fetch(url)
    expected_size = int(expected["artifactSize"])
    if len(payload) != expected_size:
        raise RuntimeError(
            f"Published feature artifact {identifier}/{version} has size {len(payload)}, expected {expected_size}"
        )
    actual_sha256 = hashlib.sha256(payload).hexdigest()
    expected_sha256 = str(expected["artifactSha256"])
    if actual_sha256 != expected_sha256:
        raise RuntimeError(f"Published feature artifact {identifier}/{version} has a SHA-256 mismatch")
    try:
        with zipfile.ZipFile(io.BytesIO(payload)) as archive:
            feature = ET.fromstring(archive.read("feature.xml"))
    except (KeyError, ET.ParseError, zipfile.BadZipFile) as error:
        raise RuntimeError(f"Published feature artifact {identifier}/{version} is invalid: {error}") from error
    if feature.attrib.get("id") != identifier:
        raise RuntimeError(
            f"Published feature artifact {identifier}/{version} contains feature id {feature.attrib.get('id')!r}"
        )
    return {
        "id": identifier,
        "version": version,
        "url": url,
        "size": len(payload),
        "sha256": actual_sha256,
    }


def verify(args: argparse.Namespace) -> dict[str, object]:
    if not args.expected_json and not args.expected_child:
        raise ValueError("Specify --expected-json and/or --expected-child")

    result: dict[str, object] = {
        "schemaVersion": 3,
        "repositoryUrl": args.repository_url.rstrip("/") + "/",
    }

    if args.expected_json:
        content, content_url = load_xml(args.repository_url, "content")
        artifacts, artifacts_url = load_xml(args.repository_url, "artifacts")
        available_ius = {
            (unit.attrib.get("id"), unit.attrib.get("version"))
            for unit in content.findall(".//unit")
            if unit.attrib.get("id") and unit.attrib.get("version")
        }
        available_feature_artifacts = {
            (artifact.attrib.get("id"), artifact.attrib.get("version"))
            for artifact in artifacts.findall(".//artifact")
            if artifact.attrib.get("classifier") == "org.eclipse.update.feature"
            and artifact.attrib.get("id")
            and artifact.attrib.get("version")
        }
        expected = expected_features(args.expected_json)
        missing_ius = [
            f"{item['iu']}/{item['version']}"
            for item in expected
            if (item["iu"], item["version"]) not in available_ius
        ]
        missing_artifacts = [
            f"{item['id']}/{item['version']}"
            for item in expected
            if (item["id"], item["version"]) not in available_feature_artifacts
        ]
        if missing_ius:
            raise RuntimeError(f"Published repository is missing exact feature IUs: {missing_ius}")
        if missing_artifacts:
            raise RuntimeError(f"Published repository is missing exact feature artifacts: {missing_artifacts}")
        verified_artifacts = [verify_feature_artifact(args.repository_url, item) for item in expected]
        result.update(
            {
                "contentMetadataUrl": content_url,
                "artifactMetadataUrl": artifacts_url,
                "expectedFeatures": expected,
                "verifiedFeatureArtifacts": verified_artifacts,
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
