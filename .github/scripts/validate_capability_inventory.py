#!/usr/bin/env python3
"""Validate and render the repository capability inventory."""

from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
INVENTORY_PATH = ROOT / "docs" / "capabilities.json"
MARKDOWN_PATH = ROOT / "docs" / "capabilities.md"
MAVEN_NS = {"m": "http://maven.apache.org/POM/4.0.0"}

ALLOWED_KIND = {"application", "cleanup", "framework", "tool", "view"}
ALLOWED_SCOPE = {"local", "project", "multi-file", "workspace"}
ALLOWED_SUPPORT = {"yes", "no", "not-assessed"}
ALLOWED_SAFETY = {"conservative", "experimental", "read-only"}
ALLOWED_STATUS = {"experimental", "upstream", "deprecated"}


class ValidationError(RuntimeError):
    """Raised for a deterministic inventory mismatch."""


def fail(message: str) -> None:
    raise ValidationError(message)


def load_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"Cannot read {path.relative_to(ROOT)}: {exc}")
    if not isinstance(value, dict):
        fail("Capability inventory root must be a JSON object")
    return value


def maven_value(root: ET.Element, xpath: str) -> str:
    element = root.find(xpath, MAVEN_NS)
    if element is None or not (element.text or "").strip():
        fail(f"Missing Maven value: {xpath}")
    return (element.text or "").strip()


def repository_metadata() -> tuple[dict[str, str], set[str]]:
    pom = ET.parse(ROOT / "pom.xml").getroot()
    java_version = maven_value(pom, "m:properties/m:java-version")
    tycho_version = maven_value(pom, "m:properties/m:tycho-version")
    modules = {
        (module.text or "").strip()
        for module in pom.findall(".//m:module", MAVEN_NS)
        if (module.text or "").strip()
    }
    release = None
    for url in pom.findall("m:repositories/m:repository/m:url", MAVEN_NS):
        match = re.search(r"/releases/([^/]+)/?", (url.text or "").strip())
        if match:
            release = match.group(1)
            break
    if release is None:
        fail("Cannot derive Eclipse release from pom.xml repositories")
    return {
        "javaVersion": java_version,
        "tychoVersion": tycho_version,
        "eclipseRelease": release,
    }, modules


def manifest_headers(path: Path) -> dict[str, str]:
    try:
        physical = path.read_text(encoding="utf-8").splitlines()
    except OSError as exc:
        fail(f"Cannot read {path.relative_to(ROOT)}: {exc}")
    logical: list[str] = []
    for line in physical:
        if line.startswith(" ") and logical:
            logical[-1] += line[1:]
        else:
            logical.append(line)
    result: dict[str, str] = {}
    for line in logical:
        if ":" not in line:
            continue
        name, value = line.split(":", 1)
        result[name.strip()] = value.strip()
    return result


def feature_ids(path: Path, element_name: str) -> set[str]:
    try:
        root = ET.parse(path).getroot()
    except (OSError, ET.ParseError) as exc:
        fail(f"Cannot parse {path.relative_to(ROOT)}: {exc}")
    return {
        value
        for element in root.findall(f".//{element_name}")
        if (value := element.attrib.get("id"))
    }


def validate_repository_versions(inventory: dict[str, Any]) -> set[str]:
    expected, modules = repository_metadata()
    declared = inventory.get("repository")
    if not isinstance(declared, dict):
        fail("repository metadata must be an object")
    for name, value in expected.items():
        if declared.get(name) != value:
            fail(f"repository.{name} is {declared.get(name)!r}, expected {value!r}")

    readme = (ROOT / "README.md").read_text(encoding="utf-8")
    required_fragments = (
        f"Java {expected['javaVersion']}",
        f"Tycho {expected['tychoVersion']}",
        expected["eclipseRelease"],
    )
    for fragment in required_fragments:
        if fragment not in readme:
            fail(f"README.md does not expose central version value {fragment!r}")
    return modules


def validate_feature(feature_module: str, feature_id: str) -> None:
    path = ROOT / feature_module / "feature.xml"
    try:
        root = ET.parse(path).getroot()
    except (OSError, ET.ParseError) as exc:
        fail(f"Cannot parse {path.relative_to(ROOT)}: {exc}")
    if root.tag != "feature" or root.attrib.get("id") != feature_id:
        fail(f"{path.relative_to(ROOT)} does not declare feature id {feature_id}")


def validate_status_language(capability: dict[str, Any]) -> None:
    if capability["status"] != "experimental":
        return
    readme = ROOT / capability["bundle"] / "README.md"
    if not readme.exists() or capability.get("statusException"):
        return
    text = readme.read_text(encoding="utf-8").lower()
    negative_phrases = (
        "not production-ready",
        "not production ready",
        "not production-suitable",
        "not production suitable",
        "not suitable for production",
    )
    for phrase in negative_phrases:
        text = text.replace(phrase, "")
    if re.search(r"\bproduction[- ](?:ready|suitable)\b", text):
        fail(
            f"{readme.relative_to(ROOT)} claims production suitability while "
            f"{capability['id']} is experimental"
        )


def require_enum(capability: dict[str, Any], field: str, allowed: set[str]) -> None:
    value = capability.get(field)
    if value not in allowed:
        fail(f"{capability.get('id', '<unknown>')}.{field}={value!r}; allowed: {sorted(allowed)}")


def validate_capabilities(inventory: dict[str, Any], modules: set[str]) -> list[dict[str, Any]]:
    capabilities = inventory.get("capabilities")
    if not isinstance(capabilities, list) or not capabilities:
        fail("capabilities must be a non-empty array")

    update_site = feature_ids(ROOT / "sandbox_updatesite" / "category.xml", "feature")
    product = feature_ids(ROOT / "sandbox_product" / "sandbox.product", "feature")
    seen_ids: set[str] = set()
    seen_bundles: set[str] = set()
    seen_features: set[str] = set()

    for capability in capabilities:
        if not isinstance(capability, dict):
            fail("Each capability must be an object")
        identifier = capability.get("id")
        bundle = capability.get("bundle")
        feature = capability.get("feature")
        if not all(isinstance(value, str) and value for value in (identifier, bundle, feature)):
            fail("Each capability requires non-empty id, bundle and feature strings")
        for value, seen, label in (
            (identifier, seen_ids, "id"),
            (bundle, seen_bundles, "bundle"),
            (feature, seen_features, "feature"),
        ):
            if value in seen:
                fail(f"Duplicate capability {label}: {value}")
            seen.add(value)

        require_enum(capability, "kind", ALLOWED_KIND)
        require_enum(capability, "scope", ALLOWED_SCOPE)
        require_enum(capability, "automaticSupport", ALLOWED_SUPPORT)
        require_enum(capability, "saveActionSupport", ALLOWED_SUPPORT)
        require_enum(capability, "safetyLevel", ALLOWED_SAFETY)
        require_enum(capability, "status", ALLOWED_STATUS)

        feature_module = f"{feature}"
        for module in (bundle, feature_module):
            if module not in modules:
                fail(f"{identifier} references module not declared by pom.xml: {module}")
            if not (ROOT / module).is_dir():
                fail(f"{identifier} references missing module directory: {module}")

        headers = manifest_headers(ROOT / bundle / "META-INF" / "MANIFEST.MF")
        symbolic_name = headers.get("Bundle-SymbolicName", "").split(";", 1)[0]
        if symbolic_name != bundle:
            fail(f"{bundle} manifest symbolic name is {symbolic_name!r}")
        expected_java = f"JavaSE-{inventory['repository']['javaVersion']}"
        if headers.get("Bundle-RequiredExecutionEnvironment") != expected_java:
            fail(f"{bundle} must require {expected_java}")
        validate_feature(feature_module, feature)

        delivery = capability.get("delivery")
        if not isinstance(delivery, dict):
            fail(f"{identifier}.delivery must be an object")
        if bool(delivery.get("updateSite")) != (feature in update_site):
            fail(f"{identifier} update-site flag disagrees with sandbox_updatesite/category.xml")
        if bool(delivery.get("product")) != (feature in product):
            fail(f"{identifier} product flag disagrees with sandbox_product/sandbox.product")

        test_module = capability.get("testModule")
        tests = capability.get("tests")
        if not isinstance(tests, dict):
            fail(f"{identifier}.tests must be an object")
        counts = [tests.get(name) for name in ("total", "enabled", "disabled")]
        if not all(isinstance(value, int) and value >= 0 for value in counts):
            fail(f"{identifier} test counts must be non-negative integers")
        if tests["enabled"] + tests["disabled"] != tests["total"]:
            fail(f"{identifier} enabled + disabled test counts do not equal total")
        if test_module is None:
            if tests["total"] != 0:
                fail(f"{identifier} has test counts but no testModule")
        elif not isinstance(test_module, str) or test_module not in modules or not (ROOT / test_module).is_dir():
            fail(f"{identifier} references invalid test module {test_module!r}")

        limitations = capability.get("knownLimitations")
        if not isinstance(limitations, list) or not all(
            isinstance(item, str) and re.fullmatch(r"#[1-9][0-9]*", item) for item in limitations
        ):
            fail(f"{identifier}.knownLimitations must contain GitHub issue numbers such as #1210")
        validate_status_language(capability)

    if update_site != seen_features:
        fail(f"Inventory/update-site feature mismatch: inventory-only={sorted(seen_features - update_site)}, "
             f"update-site-only={sorted(update_site - seen_features)}")
    if product.intersection({name for name in product if name.startswith('sandbox_')}) != seen_features:
        sandbox_product = {name for name in product if name.startswith("sandbox_")}
        fail(f"Inventory/product feature mismatch: inventory-only={sorted(seen_features - sandbox_product)}, "
             f"product-only={sorted(sandbox_product - seen_features)}")
    return capabilities


def markdown(inventory: dict[str, Any], capabilities: list[dict[str, Any]]) -> str:
    repository = inventory["repository"]
    lines = [
        "# Sandbox capability inventory",
        "",
        "<!-- Generated by .github/scripts/validate_capability_inventory.py; edit capabilities.json instead. -->",
        "",
        f"Repository baseline: Java {repository['javaVersion']}, Tycho {repository['tychoVersion']}, "
        f"Eclipse {repository['eclipseRelease']}.",
        "",
        "All status and support fields are explicit. `not-assessed` means that the repository does not yet make a public compatibility promise for that integration surface.",
        "",
        "| Capability | Kind | Scope | Bundle / feature | Tests | Delivery | Automatic / save action | Safety | Status | Limitations |",
        "|---|---|---|---|---:|---|---|---|---|---|",
    ]
    for item in capabilities:
        tests = item["tests"]
        test_text = f"{tests['enabled']}/{tests['total']} enabled"
        delivery_parts = []
        if item["delivery"]["product"]:
            delivery_parts.append("product")
        if item["delivery"]["updateSite"]:
            delivery_parts.append("update site")
        limitations = ", ".join(f"[{issue}](https://github.com/carstenartur/sandbox/issues/{issue[1:]})" for issue in item["knownLimitations"])
        lines.append(
            f"| `{item['id']}` | {item['kind']} | {item['scope']} | "
            f"`{item['bundle']}` / `{item['feature']}` | {test_text} | "
            f"{', '.join(delivery_parts) or 'none'} | {item['automaticSupport']} / {item['saveActionSupport']} | "
            f"{item['safetyLevel']} | {item['status']} | {limitations or '—'} |"
        )
    lines.extend([
        "",
        "Test counts are the last explicitly reviewed Test Report snapshot. The validator checks their internal consistency and referenced test modules; the Test Report workflow remains the source of current execution totals.",
        "",
    ])
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--check", action="store_true", help="fail when generated Markdown is stale")
    mode.add_argument("--write", action="store_true", help="rewrite generated Markdown")
    args = parser.parse_args()

    try:
        inventory = load_json(INVENTORY_PATH)
        if inventory.get("schemaVersion") != 1:
            fail("Unsupported capability inventory schemaVersion")
        modules = validate_repository_versions(inventory)
        capabilities = validate_capabilities(inventory, modules)
        rendered = markdown(inventory, capabilities)
        if args.write:
            MARKDOWN_PATH.write_text(rendered, encoding="utf-8")
        else:
            actual = MARKDOWN_PATH.read_text(encoding="utf-8") if MARKDOWN_PATH.exists() else ""
            if actual != rendered:
                fail("docs/capabilities.md is stale; run validate_capability_inventory.py --write")
    except ValidationError as exc:
        print(f"capability inventory validation failed: {exc}", file=sys.stderr)
        return 1
    print("Capability inventory is consistent.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
