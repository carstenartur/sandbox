#!/usr/bin/env python3
"""Verify the minimal p2 feature patch for the pinned JDT UI bundle."""

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
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        fail(f"Expected a JSON object in {path}")
    return value


def repository_xml(repository: Path, stem: str) -> ET.Element:
    jar = repository / f"{stem}.jar"
    xml = repository / f"{stem}.xml"
    if jar.is_file():
        with zipfile.ZipFile(jar) as archive:
            try:
                return ET.fromstring(archive.read(f"{stem}.xml"))
            except KeyError as error:
                fail(f"{jar} does not contain {stem}.xml: {error}")
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


def exact_range(version: str) -> set[str]:
    return {version, f"[{version},{version}]"}


def matching_requirements(container: ET.Element | None, identifier: str, version: str) -> list[ET.Element]:
    if container is None:
        return []
    return [
        item
        for item in container.findall("required")
        if item.attrib.get("namespace") in (None, "org.eclipse.equinox.p2.iu")
        and item.attrib.get("name") == identifier
        and item.attrib.get("range", "") in exact_range(version)
    ]


def require_one(container: ET.Element | None, identifier: str, version: str, description: str) -> ET.Element:
    matching = matching_requirements(container, identifier, version)
    if len(matching) != 1:
        fail(f"Expected one exact {description} requirement {identifier} {version}, found {len(matching)}")
    return matching[0]


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


def one_unit(units: list[ET.Element], identifier: str, version: str, description: str) -> ET.Element:
    matching = [
        unit for unit in units
        if unit.attrib.get("id") == identifier and unit.attrib.get("version") == version
    ]
    if len(matching) != 1:
        fail(f"Expected one {description} IU {identifier} {version}, found {len(matching)}")
    return matching[0]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", required=True, type=Path)
    parser.add_argument("--bundle-provenance", required=True, type=Path)
    parser.add_argument("--compatibility", required=True, type=Path)
    parser.add_argument("--feature-id", required=True)
    parser.add_argument("--feature-version", required=True)
    parser.add_argument("--patch-target-feature-id", required=True)
    parser.add_argument("--patch-target-feature-version", required=True)
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
        group_id = f"{args.feature_id}.feature.group"
        jar_id = f"{args.feature_id}.feature.jar"
        target_group_id = f"{args.patch_target_feature_id}.feature.group"
        expected_units = {
            (bundle_id, bundle_version),
            (group_id, args.feature_version),
            (jar_id, args.feature_version),
        }
        actual_units = {
            (unit.attrib.get("id", ""), unit.attrib.get("version", "")) for unit in units
        }
        if actual_units != expected_units:
            fail(f"Unexpected p2 IU set: expected={sorted(expected_units)}, actual={sorted(actual_units)}")

        one_unit(units, bundle_id, bundle_version, "bundle")
        group = one_unit(units, group_id, args.feature_version, "patch feature group")
        one_unit(units, jar_id, args.feature_version, "patch feature jar")
        group_properties = properties(group)
        if group_properties.get("org.eclipse.equinox.p2.type.patch") != "true":
            fail("Feature group is not marked as an Equinox p2 patch")
        if group_properties.get("org.eclipse.equinox.p2.type.group") != "true":
            fail("Feature group is not marked as a p2 group")

        require_one(group.find("requires"), jar_id, args.feature_version, "feature jar")
        lifecycle = require_one(
            group.find("lifeCycle"), target_group_id, args.patch_target_feature_version, "patch lifecycle"
        )
        if lifecycle.attrib.get("greedy") != "false":
            fail("Patch lifecycle target must be non-greedy")
        normal_target = require_one(
            group.find("requires"), target_group_id, args.patch_target_feature_version, "patch target"
        )
        if normal_target.attrib.get("greedy") != "false":
            fail("Patch target requirement must be non-greedy")
        require_one(
            group.find("./patchScope/scope/requires"),
            target_group_id,
            args.patch_target_feature_version,
            "patch scope",
        )

        changes = group.findall("./changes/change")
        if len(changes) != 1:
            fail(f"Expected exactly one patch change, found {len(changes)}")
        from_required = changes[0].find("./from/required")
        to_required = changes[0].find("./to/required")
        if from_required is None or from_required.attrib.get("name") != bundle_id:
            fail("Patch change has no source requirement for the JDT UI bundle")
        if from_required.attrib.get("namespace") not in (None, "org.eclipse.equinox.p2.iu"):
            fail("Patch source requirement uses an unexpected namespace")
        if from_required.attrib.get("range") != "0.0.0":
            fail(f"Patch source requirement must use wildcard range 0.0.0: {from_required.attrib}")
        if to_required is None or to_required.attrib.get("name") != bundle_id:
            fail("Patch change has no replacement requirement for the JDT UI bundle")
        if to_required.attrib.get("namespace") not in (None, "org.eclipse.equinox.p2.iu"):
            fail("Patch replacement requirement uses an unexpected namespace")
        if to_required.attrib.get("range", "") not in exact_range(bundle_version):
            fail(f"Patch replacement does not pin exact bundle version {bundle_version}: {to_required.attrib}")

        artifact_entries = artifacts.findall("./artifacts/artifact")
        expected_artifacts = {
            ("osgi.bundle", bundle_id, bundle_version),
            ("org.eclipse.update.feature", args.feature_id, args.feature_version),
        }
        actual_artifacts = {
            (
                item.attrib.get("classifier", ""),
                item.attrib.get("id", ""),
                item.attrib.get("version", ""),
            )
            for item in artifact_entries
        }
        if actual_artifacts != expected_artifacts:
            fail(
                f"Unexpected p2 artifact keys: expected={sorted(expected_artifacts)}, "
                f"actual={sorted(actual_artifacts)}"
            )

        integrity: dict[str, list[str]] = {}
        for item in artifact_entries:
            path = artifact_file(
                repository, item.attrib["classifier"], item.attrib["id"], item.attrib["version"]
            )
            integrity[path.name] = verify_integrity(path, properties(item))
        bundle_path = repository / "plugins" / f"{bundle_id}_{bundle_version}.jar"
        if sha256(bundle_path) != bundle_sha:
            fail("Published bundle bytes do not match the pinned build provenance")

        feature_path = repository / "features" / f"{args.feature_id}_{args.feature_version}.jar"
        with zipfile.ZipFile(feature_path) as archive:
            feature_xml = ET.fromstring(archive.read("feature.xml"))
        if feature_xml.attrib.get("id") != args.feature_id or feature_xml.attrib.get("version") != args.feature_version:
            fail("Published feature.xml identity differs from the expected patch feature")
        patch_imports = [
            item for item in feature_xml.findall("./requires/import")
            if item.attrib.get("patch") == "true"
        ]
        if len(patch_imports) != 1:
            fail(f"Expected one feature patch import, found {len(patch_imports)}")
        patch_import = patch_imports[0]
        if patch_import.attrib.get("feature") != args.patch_target_feature_id:
            fail("Feature patch targets an unexpected feature ID")
        if patch_import.attrib.get("version") != args.patch_target_feature_version:
            fail("Feature patch targets an unexpected feature version")
        if patch_import.attrib.get("match") not in (None, "", "perfect"):
            fail("Feature patch may not use a non-perfect version match")
        plugin = feature_xml.find(f"./plugin[@id='{bundle_id}']")
        if plugin is None or plugin.attrib.get("version") != bundle_version:
            fail("Patch feature.xml does not pin the exact patched bundle version")

        payload: dict[str, object] = {
            "schemaVersion": 2,
            "result": "PASS",
            "repository": str(repository),
            "sourceCommit": provenance.get("sourceCommit"),
            "bundle": {"id": bundle_id, "version": bundle_version, "sha256": bundle_sha},
            "feature": {
                "id": args.feature_id,
                "version": args.feature_version,
                "groupIU": group_id,
                "jarIU": jar_id,
                "kind": "patch",
                "patchTarget": {
                    "id": args.patch_target_feature_id,
                    "version": args.patch_target_feature_version,
                    "groupIU": target_group_id,
                },
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
            "# Patched JDT UI p2 feature-patch verification",
            "",
            "- Result: **PASS**",
            f"- Patch feature: `{group_id} {args.feature_version}`",
            f"- Patch target: `{target_group_id} {args.patch_target_feature_version}`",
            f"- Replacement bundle: `{bundle_id} {bundle_version}`",
            f"- p2 units: **{len(units)}**",
            f"- p2 artifacts: **{len(artifact_entries)}**",
            "- Equinox patch scope, change and lifecycle metadata: **PASS**",
            "- Bundle bytes and published checksums: **PASS**",
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
        json.JSONDecodeError,
        VerificationError,
    ) as error:
        print(f"patched JDT UI p2 verification failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
