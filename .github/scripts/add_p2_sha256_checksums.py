#!/usr/bin/env python3
"""Add or replace SHA-256 and size metadata for every local p2 artifact."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import tempfile
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path


class MetadataError(RuntimeError):
    """Raised when a p2 artifact cannot be mapped to a local file."""


def artifact_path(repository: Path, classifier: str, identifier: str, version: str) -> Path:
    if classifier == "osgi.bundle":
        return repository / "plugins" / f"{identifier}_{version}.jar"
    if classifier == "org.eclipse.update.feature":
        return repository / "features" / f"{identifier}_{version}.jar"
    if classifier == "binary":
        return repository / "binary" / f"{identifier}_{version}"
    raise MetadataError(f"Unsupported p2 artifact classifier: {classifier!r}")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_metadata(repository: Path) -> tuple[ET.Element, bool]:
    jar = repository / "artifacts.jar"
    xml = repository / "artifacts.xml"
    if jar.is_file():
        with zipfile.ZipFile(jar) as archive:
            try:
                return ET.fromstring(archive.read("artifacts.xml")), True
            except KeyError as error:
                raise MetadataError(f"{jar} does not contain artifacts.xml") from error
    if xml.is_file():
        return ET.parse(xml).getroot(), False
    raise MetadataError(f"Repository contains neither artifacts.jar nor artifacts.xml: {repository}")


def set_properties(artifact: ET.Element, values: dict[str, str]) -> None:
    container = artifact.find("properties")
    if container is None:
        container = ET.SubElement(artifact, "properties")
    properties: dict[str, str] = {
        item.attrib.get("name", ""): item.attrib.get("value", "")
        for item in container.findall("property")
        if item.attrib.get("name")
    }
    properties.update(values)
    for child in list(container):
        container.remove(child)
    for name in sorted(properties):
        ET.SubElement(container, "property", {"name": name, "value": properties[name]})
    container.set("size", str(len(properties)))


def serialized(root: ET.Element) -> bytes:
    body = ET.tostring(root, encoding="utf-8", short_empty_elements=True)
    return (
        b"<?xml version='1.0' encoding='UTF-8'?>\n"
        b"<?artifactRepository version='1.1.0'?>\n"
        + body
        + b"\n"
    )


def write_metadata(repository: Path, root: ET.Element, compressed: bool) -> None:
    payload = serialized(root)
    if not compressed:
        (repository / "artifacts.xml").write_bytes(payload)
        return
    target = repository / "artifacts.jar"
    descriptor, temporary_name = tempfile.mkstemp(prefix="artifacts-", suffix=".jar", dir=repository)
    os.close(descriptor)
    temporary = Path(temporary_name)
    try:
        info = zipfile.ZipInfo("artifacts.xml", date_time=(1980, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = 0o100644 << 16
        with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            archive.writestr(info, payload)
        temporary.replace(target)
    finally:
        temporary.unlink(missing_ok=True)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", required=True, type=Path)
    args = parser.parse_args()

    repository = args.repository.resolve()
    root, compressed = load_metadata(repository)
    evidence: list[dict[str, object]] = []
    artifacts = root.findall("./artifacts/artifact")
    if not artifacts:
        raise MetadataError("p2 repository contains no artifact entries")
    for artifact in artifacts:
        classifier = artifact.attrib.get("classifier", "")
        identifier = artifact.attrib.get("id", "")
        version = artifact.attrib.get("version", "")
        if not classifier or not identifier or not version:
            raise MetadataError(f"Malformed artifact key: {artifact.attrib}")
        path = artifact_path(repository, classifier, identifier, version)
        if not path.is_file():
            raise MetadataError(f"Artifact metadata references a missing file: {path}")
        size = path.stat().st_size
        digest = sha256(path)
        set_properties(
            artifact,
            {
                "artifact.size": str(size),
                "download.size": str(size),
                "download.checksum.sha-256": digest,
            },
        )
        evidence.append(
            {
                "classifier": classifier,
                "id": identifier,
                "version": version,
                "file": str(path.relative_to(repository)),
                "size": size,
                "sha256": digest,
            }
        )
    write_metadata(repository, root, compressed)
    print(json.dumps({"schemaVersion": 1, "artifacts": evidence}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
