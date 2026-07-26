#!/usr/bin/env python3
"""Fail closed when release metadata or source archives retain SNAPSHOT identity."""

from __future__ import annotations

import argparse
import json
import re
import tarfile
import zipfile
from dataclasses import asdict, dataclass
from pathlib import Path, PurePosixPath
from typing import Iterable

VERSIONED_NAMES = {
    "pom.xml",
    "MANIFEST.MF",
    "feature.xml",
    "release.properties",
    "CITATION.cff",
    ".zenodo.json",
}
VERSIONED_SUFFIXES = {".product"}
EXCLUDED_PARTS = {".git", "target", "node_modules"}


class ReleaseIdentityError(RuntimeError):
    """Raised when release content does not represent exactly one stable version."""


@dataclass(frozen=True)
class Finding:
    source: str
    path: str
    forbidden_occurrences: int


def is_versioned_path(path: PurePosixPath) -> bool:
    return not any(part in EXCLUDED_PARTS for part in path.parts) and (
        path.name in VERSIONED_NAMES or path.suffix in VERSIONED_SUFFIXES
    )


def inspect_text(source: str, path: str, text: str, forbidden_version: str) -> Finding | None:
    count = text.count(forbidden_version)
    return Finding(source, path, count) if count else None


def repository_findings(root: Path, forbidden_version: str) -> list[Finding]:
    findings: list[Finding] = []
    for path in sorted(root.rglob("*")):
        if not path.is_file():
            continue
        relative = PurePosixPath(path.relative_to(root).as_posix())
        if not is_versioned_path(relative):
            continue
        finding = inspect_text(
            "repository", relative.as_posix(), path.read_text(encoding="utf-8", errors="replace"), forbidden_version
        )
        if finding:
            findings.append(finding)
    return findings


def archive_entries(archive: Path) -> Iterable[tuple[str, bytes]]:
    if archive.name.endswith((".tar.gz", ".tgz")):
        with tarfile.open(archive, "r:gz") as stream:
            for member in stream.getmembers():
                if member.isfile():
                    extracted = stream.extractfile(member)
                    if extracted is not None:
                        yield member.name, extracted.read()
        return
    if archive.suffix == ".zip":
        with zipfile.ZipFile(archive) as stream:
            for name in sorted(stream.namelist()):
                if not name.endswith("/"):
                    yield name, stream.read(name)
        return
    raise ReleaseIdentityError(f"Unsupported source archive: {archive}")


def archive_findings(archive: Path, forbidden_version: str) -> list[Finding]:
    findings: list[Finding] = []
    for name, payload in archive_entries(archive):
        path = PurePosixPath(name)
        # GitHub/git archives normally contain one top-level directory.
        normalized = PurePosixPath(*path.parts[1:]) if len(path.parts) > 1 else path
        if not is_versioned_path(normalized):
            continue
        finding = inspect_text(
            archive.name, normalized.as_posix(), payload.decode("utf-8", errors="replace"), forbidden_version
        )
        if finding:
            findings.append(finding)
    return findings


def verify_stable_version(expected_version: str, forbidden_version: str) -> None:
    stable = re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", expected_version)
    if stable is None:
        raise ReleaseIdentityError(f"Expected stable version X.Y.Z, found {expected_version!r}")
    if forbidden_version == expected_version or not forbidden_version.endswith("-SNAPSHOT"):
        raise ReleaseIdentityError(
            f"Forbidden version must be the preceding SNAPSHOT identity, found {forbidden_version!r}"
        )
    if forbidden_version.removesuffix("-SNAPSHOT") != expected_version:
        raise ReleaseIdentityError(
            f"Stable version {expected_version!r} does not correspond to {forbidden_version!r}"
        )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--expected-version", required=True)
    parser.add_argument("--forbidden-version", required=True)
    parser.add_argument("--archive", type=Path, action="append", default=[])
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    verify_stable_version(args.expected_version, args.forbidden_version)
    root = args.root.resolve()
    if not root.is_dir():
        raise ReleaseIdentityError(f"Repository root does not exist: {root}")

    findings = repository_findings(root, args.forbidden_version)
    inspected_archives: list[str] = []
    for archive in args.archive:
        resolved = archive.resolve()
        if not resolved.is_file():
            raise ReleaseIdentityError(f"Source archive does not exist: {resolved}")
        inspected_archives.append(str(resolved))
        findings.extend(archive_findings(resolved, args.forbidden_version))

    report = {
        "schemaVersion": 1,
        "status": "PASS" if not findings else "FAIL",
        "expectedVersion": args.expected_version,
        "forbiddenVersion": args.forbidden_version,
        "repositoryRoot": str(root),
        "archives": inspected_archives,
        "findings": [asdict(finding) for finding in findings],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    if findings:
        details = ", ".join(f"{item.source}:{item.path}" for item in findings[:10])
        raise ReleaseIdentityError(
            f"Release content retains forbidden version {args.forbidden_version!r}: {details}"
        )
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
