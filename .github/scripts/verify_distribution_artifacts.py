#!/usr/bin/env python3
"""Validate built Sandbox p2 repositories and materialized product artifacts."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
import zipfile
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


class VerificationError(RuntimeError):
    """Raised when built distribution evidence is incomplete or inconsistent."""


def fail(message: str) -> None:
    raise VerificationError(message)


def xml_from_repository(repository: Path, stem: str) -> ET.Element:
    jar = repository / f"{stem}.jar"
    xml = repository / f"{stem}.xml"
    if jar.exists():
        with zipfile.ZipFile(jar) as archive:
            entry = f"{stem}.xml"
            try:
                return ET.fromstring(archive.read(entry))
            except KeyError as exc:
                fail(f"{jar} does not contain {entry}: {exc}")
    if xml.exists():
        return ET.parse(xml).getroot()
    fail(f"Repository is missing both {stem}.jar and {stem}.xml: {repository}")


def feature_ids(path: Path) -> list[str]:
    root = ET.parse(path).getroot()
    result = [element.attrib["id"] for element in root.findall("feature") if element.attrib.get("id")]
    if not result:
        fail(f"No published features declared in {path}")
    if len(result) != len(set(result)):
        fail(f"Duplicate feature declaration in {path}")
    return sorted(result)


def manifest(path: Path) -> dict[str, str]:
    if path.is_dir():
        raw = (path / "META-INF" / "MANIFEST.MF").read_bytes().decode("utf-8")
    else:
        with zipfile.ZipFile(path) as archive:
            raw = archive.read("META-INF/MANIFEST.MF").decode("utf-8")
    logical: list[str] = []
    for line in raw.splitlines():
        if line.startswith(" ") and logical:
            logical[-1] += line[1:]
        else:
            logical.append(line)
    headers: dict[str, str] = {}
    for line in logical:
        if ":" in line:
            name, value = line.split(":", 1)
            headers[name.strip()] = value.strip()
    return headers


def digest(path: Path, algorithm: str) -> str:
    value = hashlib.new(algorithm)
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


def artifact_properties(element: ET.Element) -> dict[str, str]:
    properties = element.find("properties")
    if properties is None:
        return {}
    return {
        item.attrib.get("name", ""): item.attrib.get("value", "")
        for item in properties.findall("property")
        if item.attrib.get("name")
    }


def artifact_path(repository: Path, classifier: str, identifier: str, version: str) -> Path | None:
    if classifier == "osgi.bundle":
        return repository / "plugins" / f"{identifier}_{version}.jar"
    if classifier == "org.eclipse.update.feature":
        return repository / "features" / f"{identifier}_{version}.jar"
    return None


def verify_repository(repository: Path, published_features: list[str]) -> dict[str, object]:
    if not repository.is_dir():
        fail(f"Built p2 repository does not exist: {repository}")

    content = xml_from_repository(repository, "content")
    artifacts = xml_from_repository(repository, "artifacts")
    units = {
        unit.attrib.get("id", ""): unit.attrib.get("version", "")
        for unit in content.findall("./units/unit")
        if unit.attrib.get("id")
    }
    missing_units = [f"{feature}.feature.group" for feature in published_features
                     if f"{feature}.feature.group" not in units]
    if missing_units:
        fail(f"Published feature IUs missing from content metadata: {missing_units}")

    seen_keys: set[tuple[str, str, str]] = set()
    checked_files = 0
    checksum_count = 0
    missing_files: list[str] = []
    checksum_errors: list[str] = []
    for artifact in artifacts.findall("./artifacts/artifact"):
        classifier = artifact.attrib.get("classifier", "")
        identifier = artifact.attrib.get("id", "")
        version = artifact.attrib.get("version", "")
        key = classifier, identifier, version
        if key in seen_keys:
            fail(f"Duplicate p2 artifact key: {key}")
        seen_keys.add(key)
        path = artifact_path(repository, classifier, identifier, version)
        if path is None:
            continue
        checked_files += 1
        if not path.is_file():
            missing_files.append(str(path.relative_to(repository)))
            continue
        properties = artifact_properties(artifact)
        size = properties.get("download.size") or properties.get("artifact.size")
        if size and size.isdigit() and path.stat().st_size != int(size):
            checksum_errors.append(
                f"{path.name}: size {path.stat().st_size}, metadata {size}"
            )
        for name, value in properties.items():
            lowered = name.lower()
            if re.fullmatch(r"[0-9a-fA-F]{64}", value) and ("sha-256" in lowered or "sha256" in lowered):
                checksum_count += 1
                actual = digest(path, "sha256")
                if actual.lower() != value.lower():
                    checksum_errors.append(f"{path.name}: SHA-256 mismatch")
            elif re.fullmatch(r"[0-9a-fA-F]{32}", value) and "md5" in lowered:
                checksum_count += 1
                actual = digest(path, "md5")
                if actual.lower() != value.lower():
                    checksum_errors.append(f"{path.name}: MD5 mismatch")
    if missing_files:
        fail(f"p2 artifact metadata references missing files: {missing_files[:20]}")
    if checksum_errors:
        fail(f"p2 artifact integrity errors: {checksum_errors[:20]}")
    if checked_files == 0:
        fail("No bundle or feature artifacts were validated")

    return {
        "path": str(repository),
        "metadataUnits": len(units),
        "artifactKeys": len(seen_keys),
        "artifactFilesChecked": checked_files,
        "checksumsChecked": checksum_count,
        "publishedFeatures": [
            {"id": feature, "iu": f"{feature}.feature.group", "version": units[f"{feature}.feature.group"]}
            for feature in published_features
        ],
    }


def find_product_root(products: Path) -> Path:
    candidates = sorted({path.parent.parent for path in products.rglob("plugins/org.eclipse.equinox.launcher_*.jar")})
    candidates = [candidate for candidate in candidates if (candidate / "configuration" / "config.ini").is_file()]
    if len(candidates) != 1:
        fail(f"Expected exactly one materialized Linux product root, found: {candidates}")
    return candidates[0]


def installed_bundles(root: Path) -> list[Path]:
    plugins = root / "plugins"
    if not plugins.is_dir():
        return []
    return sorted(
        path for path in plugins.iterdir()
        if (path.is_file() and path.suffix == ".jar")
        or (path.is_dir() and (path / "META-INF" / "MANIFEST.MF").is_file())
    )


def installed_feature_matches(root: Path, feature_id: str) -> list[Path]:
    features = root / "features"
    if not features.is_dir():
        return []
    result: list[Path] = []
    for path in features.glob(f"{feature_id}_*"):
        if path.is_file() and path.suffix == ".jar":
            result.append(path)
        elif path.is_dir() and (path / "feature.xml").is_file():
            result.append(path)
    return sorted(result)


def verify_product(products: Path, published_features: list[str]) -> dict[str, object]:
    if not products.is_dir():
        fail(f"Materialized product directory does not exist: {products}")
    root = find_product_root(products)
    plugins = installed_bundles(root)
    if not plugins:
        fail(f"Materialized product contains no plug-in bundles: {root}")

    singleton_versions: dict[str, list[tuple[str, str]]] = defaultdict(list)
    symbolic_names: set[str] = set()
    for plugin in plugins:
        try:
            headers = manifest(plugin)
        except (KeyError, OSError, UnicodeDecodeError, zipfile.BadZipFile):
            continue
        declaration = headers.get("Bundle-SymbolicName", "")
        name = declaration.split(";", 1)[0].strip()
        if not name:
            continue
        symbolic_names.add(name)
        if "singleton:=true" in declaration.replace(" ", ""):
            singleton_versions[name].append((headers.get("Bundle-Version", ""), plugin.name))

    duplicates = {
        name: values for name, values in singleton_versions.items()
        if len({version for version, _ in values}) > 1 or len(values) > 1
    }
    if duplicates:
        fail(f"Duplicate singleton bundles in materialized product: {duplicates}")

    missing_features: list[str] = []
    ambiguous_features: dict[str, list[str]] = {}
    for feature in published_features:
        matches = installed_feature_matches(root, feature)
        if not matches:
            missing_features.append(feature)
        elif len(matches) != 1:
            ambiguous_features[feature] = [match.name for match in matches]
    if missing_features:
        fail(f"Published features missing from materialized product: {missing_features}")
    if ambiguous_features:
        fail(f"Published features occur more than once in materialized product: {ambiguous_features}")

    launchers = sorted((root / "plugins").glob("org.eclipse.equinox.launcher_*.jar"))
    if len(launchers) != 1:
        fail(f"Expected one Equinox launcher in product, found {launchers}")

    return {
        "root": str(root),
        "launcher": str(launchers[0]),
        "pluginCount": len(plugins),
        "singletonBundleCount": len(singleton_versions),
        "sandboxBundleCount": len([name for name in symbolic_names if name.startswith("sandbox_")]),
    }


def repositories_from_target(path: Path) -> list[str]:
    target = ET.parse(path).getroot()
    repositories = sorted({
        repository.attrib["location"]
        for location in target.findall(".//location[@type='InstallableUnit']")
        for repository in location.findall("repository")
        if repository.attrib.get("location")
    })
    if not repositories:
        fail(f"No InstallableUnit repositories found in {path}")
    return repositories


def write_markdown(payload: dict[str, object], path: Path) -> None:
    repository = payload["repository"]
    product = payload["product"]
    assert isinstance(repository, dict)
    assert isinstance(product, dict)
    lines = [
        "# Sandbox distribution verification",
        "",
        "- Result: **PASS**",
        f"- Published feature IUs: **{len(repository['publishedFeatures'])}**",
        f"- p2 metadata units: **{repository['metadataUnits']}**",
        f"- p2 artifact files checked: **{repository['artifactFilesChecked']}**",
        f"- Integrity checks found in metadata: **{repository['checksumsChecked']}**",
        f"- Product plug-ins: **{product['pluginCount']}**",
        f"- Singleton bundles: **{product['singletonBundleCount']}** (no duplicates)",
        f"- Product root: `{product['root']}`",
        "",
        "The runtime workflow additionally starts the materialized product and provisions every published feature into a fresh p2 destination before publication.",
    ]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", type=Path, default=ROOT / "sandbox_updatesite" / "target" / "repository")
    parser.add_argument("--products", type=Path, default=ROOT / "sandbox_product" / "target" / "products")
    parser.add_argument("--output", type=Path, default=ROOT / "target" / "distribution-verification")
    args = parser.parse_args()

    try:
        published = feature_ids(ROOT / "sandbox_updatesite" / "category.xml")
        payload: dict[str, object] = {
            "schemaVersion": 1,
            "publishedFeatureIds": published,
            "publishedFeatureIUs": [f"{feature}.feature.group" for feature in published],
            "targetRepositories": repositories_from_target(ROOT / "sandbox_target" / "eclipse.target"),
            "repository": verify_repository(args.repository, published),
            "product": verify_product(args.products, published),
        }
        args.output.mkdir(parents=True, exist_ok=True)
        (args.output / "verification.json").write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
        write_markdown(payload, args.output / "verification.md")
        print(json.dumps(payload, indent=2))
        return 0
    except (OSError, ET.ParseError, VerificationError, zipfile.BadZipFile) as error:
        print(f"distribution verification failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
