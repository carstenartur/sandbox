#!/usr/bin/env python3
"""Verify the minimal p2 repository for the pinned patched JDT UI bundle."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path


class VerificationError(RuntimeError):
    """Raised when repository evidence is incomplete or inconsistent."""


def fail(message: str) -> None:
    raise VerificationError(message)


def read_json(path: Path) -> dict[str, object]:
    with path.open(encoding="utf-8") as stream:
        value = json.load(stream)
    if not isinstance(value, dict):
        fail(f"Expected a JSON object in {path}")
    return value


def repository_xml(repository: Path, stem: str) -> ET.Element:
    jar = repository / f"{stem}.jar"
    xml = repository / f"{stem}.xml"
    if jar.is_file():
        with zipfile.ZipFile(jar) as archive:
            try:
                raw = archive.read(f"{stem}.xml")
            except KeyError as error:
                fail(f"{jar} does not contain {stem}.xml: {error}")
        return ET.fromstring(raw)
    if xml.is_file():
        return ET.parse(xml).getroot()
    fail(f"Repository is missing {stem}.jar and {stem}.xml: {repository}")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def md5(path: Path) -> str:
    digest = hashlib.md5(usedforsecurity=False)
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def properties(element: ET.Element) -> dict[str, str]:
    container = element.find("properties")
    if container is None:
        return {}
    return {
        item.attrib.get("name", ""): item.attrib.get("value", "")
        for item in container.findall("property")
        if item.attrib.get("name")
    }


def exact_requirement(unit: ET.Element, identifier: str, version: str) -> bool:
    expected_ranges = {version, f"[{version},{version}]"}
    for requirement in unit.findall("./requires/required"):
        if requirement.attrib.get("name") != identifier:
            continue
        if requirement.attrib.get("namespace") not in (None, "org.eclipse.equinox.p2.iu"):
            continue
        if requirement.attrib.get("range", "") in expected_ranges:
            return True
    return False


def artifact_file(repository: Path, classifier: str, identifier: str, version: str) -> Path:
    if classifier == "osgi.bundle":
        return repository / "plugins" / f"{identifier}_{version}.jar"
    if classifier == "org.eclipse.update.feature":
        return repository / "features" / f"{identifier}_{version}.jar"
    fail(f"Unexpected artifact classifier {classifier!r}")


def verify_integrity(path: Path, metadata: dict[str, str]) -> list[str]:
    if not path.is_file():
        fail(f"Artifact metadata references missing file: {path}")
    checks: list[str] = []
    size = metadata.get("download.size") or metadata.get("artifact.size")
    if size and size.isdigit():
        if path.stat().st_size != int(size):
            fail(f"Size mismatch for {path.name}: file={path.stat().st_size}, metadata={size}")
        checks.append("size")
    for name, value in metadata.items():
        lowered = name.lower()
        if re.fullmatch(r"[0-9a-fA-F]{64}", value) and ("sha-256" in lowered or "sha256" in lowered):
            if sha256(path).lower() != value.lower():
                fail(f"SHA-256 mismatch for {path.name} ({name})")
            checks.append("sha256")
        elif re.fullmatch(r"[0-9a-fA-F]{32}", value) and "md5" in lowered:
            if md5(path).lower() != value.lower():
                fail(f"MD5 mismatch for {path.name} ({name})")
            checks.append("md5")
    if not any(check in {"sha256", "md5"} for check in checks):
        fail(f"No verifiable checksum property was published for {path.name}")
    return sorted(set(checks))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", required=True, type=Path)
    parser.add_argument("--bundle-provenance", required=True, type=Path)
    parser.add_argument("--compatibility", required=True, type=Path)
    parser.add_argument("--feature-id", required=True)
    parser.add_argument("--feature-version", required=True)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    try:
        repository = args.repository.resolve()
        provenance = read_json(args.bundle_provenance)
        compatibility = read_json(args.compatibility)
        if compatibility.get("compatibleForReplacement") is not True:
            fail("Stock-target compatibility report does not authorize p2 replacement")

        bundle_id = str(provenance.get("bundleSymbolicName", ""))
        bundle_version = str(provenance.get("bundleVersion", ""))
        bundle_sha = str(provenance.get("bundleSha256", ""))
        if not bundle_id or not bundle_version or not re.fullmatch(r"[0-9a-f]{64}", bundle_sha):
            fail("Bundle provenance is missing a valid symbolic name, version, or SHA-256")

        patched = compatibility.get("patchedBundle")
        if not isinstance(patched, dict):
            fail("Compatibility report has no patchedBundle object")
        if patched.get("version") != bundle_version or patched.get("sha256") != bundle_sha:
            fail("Compatibility report and bundle provenance refer to different patched artifacts")

        content = repository_xml(repository, "content")
        artifacts = repository_xml(repository, "artifacts")
        units = content.findall("./units/unit")

        bundle_units = [unit for unit in units if unit.attrib.get("id") == bundle_id]
        found_bundle_units = [
            (unit.attrib.get("id"), unit.attrib.get("version")) for unit in bundle_units
        ]
        if found_bundle_units != [(bundle_id, bundle_version)]:
            fail(
                f"Expected exactly one bundle IU {bundle_id} {bundle_version}, "
                f"found {found_bundle_units}"
            )

        feature_group_id = f"{args.feature_id}.feature.group"
        feature_jar_id = f"{args.feature_id}.feature.jar"
        feature_groups = [unit for unit in units if unit.attrib.get("id") == feature_group_id]
        feature_jars = [unit for unit in units if unit.attrib.get("id") == feature_jar_id]
        if [
            (unit.attrib.get("id"), unit.attrib.get("version")) for unit in feature_groups
        ] != [(feature_group_id, args.feature_version)]:
            fail(f"Expected exactly one feature group IU {feature_group_id} {args.feature_version}")
        if [
            (unit.attrib.get("id"), unit.attrib.get("version")) for unit in feature_jars
        ] != [(feature_jar_id, args.feature_version)]:
            fail(f"Expected exactly one feature jar IU {feature_jar_id} {args.feature_version}")
        if not exact_requirement(feature_groups[0], feature_jar_id, args.feature_version):
            fail("Feature group does not require its exact feature jar IU")
        if not exact_requirement(feature_jars[0], bundle_id, bundle_version):
            fail("Patch feature does not require the exact patched JDT UI bundle version")

        artifact_entries = artifacts.findall("./artifacts/artifact")
        expected_keys = {
            ("osgi.bundle", bundle_id, bundle_version),
            ("org.eclipse.update.feature", args.feature_id, args.feature_version),
        }
        actual_keys = {
            (
                item.attrib.get("classifier", ""),
                item.attrib.get("id", ""),
                item.attrib.get("version", ""),
            )
            for item in artifact_entries
        }
        if actual_keys != expected_keys:
            fail(
                f"Unexpected p2 artifact keys: expected={sorted(expected_keys)}, "
                f"actual={sorted(actual_keys)}"
            )

        integrity: dict[str, list[str]] = {}
        for item in artifact_entries:
            classifier = item.attrib["classifier"]
            identifier = item.attrib["id"]
            version = item.attrib["version"]
            path = artifact_file(repository, classifier, identifier, version)
            integrity[path.name] = verify_integrity(path, properties(item))

        bundle_path = repository / "plugins" / f"{bundle_id}_{bundle_version}.jar"
        if sha256(bundle_path) != bundle_sha:
            fail("Published bundle bytes do not match the pinned build provenance")

        feature_path = repository / "features" / f"{args.feature_id}_{args.feature_version}.jar"
        with zipfile.ZipFile(feature_path) as archive:
            feature_xml = ET.fromstring(archive.read("feature.xml"))
        if (
            feature_xml.attrib.get("id") != args.feature_id
            or feature_xml.attrib.get("version") != args.feature_version
        ):
            fail("Published feature.xml identity differs from the expected feature")
        plugin = feature_xml.find(f"./plugin[@id='{bundle_id}']")
        if plugin is None or plugin.attrib.get("version") != bundle_version:
            fail("Published feature.xml does not pin the exact patched bundle version")

        payload: dict[str, object] = {
            "schemaVersion": 1,
            "result": "PASS",
            "repository": str(repository),
            "sourceCommit": provenance.get("sourceCommit"),
            "bundle": {"id": bundle_id, "version": bundle_version, "sha256": bundle_sha},
            "feature": {
                "id": args.feature_id,
                "version": args.feature_version,
                "groupIU": feature_group_id,
                "jarIU": feature_jar_id,
            },
            "artifactIntegrity": integrity,
            "unitCount": len(units),
            "artifactCount": len(artifact_entries),
        }
        args.output.mkdir(parents=True, exist_ok=True)
        (args.output / "repository-verification.json").write_text(
            json.dumps(payload, indent=2) + "\n", encoding="utf-8"
        )
        lines = [
            "# Patched JDT UI p2 repository verification",
            "",
            "- Result: **PASS**",
            f"- Bundle: `{bundle_id} {bundle_version}`",
            f"- Feature group: `{feature_group_id} {args.feature_version}`",
            f"- p2 units: **{len(units)}**",
            f"- p2 artifacts: **{len(artifact_entries)}**",
            "- Bundle bytes match pinned SHA-256 provenance: **PASS**",
            "- Exact-version feature requirement: **PASS**",
            "- Published artifact checksums: **PASS**",
        ]
        (args.output / "repository-verification.md").write_text(
            "\n".join(lines) + "\n", encoding="utf-8"
        )
        print(json.dumps(payload, indent=2))
        return 0
    except (
        OSError,
        KeyError,
        ValueError,
        ET.ParseError,
        zipfile.BadZipFile,
        VerificationError,
    ) as error:
        print(f"patched JDT UI p2 verification failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
