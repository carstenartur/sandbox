#!/usr/bin/env python3
"""Fail when Maven, PDE target, product and Oomph use different Eclipse baselines."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MAVEN_NS = {"m": "http://maven.apache.org/POM/4.0.0"}
RELEASE_PATTERN = re.compile(r"/releases/(\d{4}-\d{2})/?$")
ORBIT_PATTERN = re.compile(r"/orbit-aggregation/(\d{4}-\d{2})/?$")
BOUNCY_CASTLE_IDS = ("bcutil", "bcprov", "bcpkix", "bcpg")


def single_release(urls: list[str], pattern: re.Pattern[str], label: str) -> str:
    matches = [(url, match.group(1)) for url in urls if (match := pattern.search(url))]
    if len(matches) != 1:
        rendered = ", ".join(urls) if urls else "<none>"
        raise ValueError(f"Expected exactly one {label} repository, found: {rendered}")
    return matches[0][1]


def text(element: ET.Element | None, label: str) -> str:
    if element is None or not element.text or not element.text.strip():
        raise ValueError(f"Missing {label}")
    return element.text.strip()


def osgi_version(version: str) -> str:
    """Normalize Maven's 1.84 spelling to the OSGi 1.84.0 spelling."""
    if re.fullmatch(r"\d+\.\d+", version):
        return f"{version}.0"
    if re.fullmatch(r"\d+\.\d+\.\d+", version):
        return version
    raise ValueError(f"Unsupported Bouncy Castle version syntax: {version}")


def verify() -> None:
    pom = ET.parse(ROOT / "pom.xml").getroot()
    pom_repositories = [
        text(repository.find("m:url", MAVEN_NS), "Maven repository URL")
        for repository in pom.findall("m:repositories/m:repository", MAVEN_NS)
        if text(repository.find("m:layout", MAVEN_NS), "Maven repository layout") == "p2"
    ]

    target = ET.parse(ROOT / "sandbox_target/eclipse.target").getroot()
    target_repositories = [
        repository.attrib["location"].rstrip("/")
        for repository in target.findall(".//location[@type='InstallableUnit']/repository")
        if repository.attrib.get("location")
    ]

    product = ET.parse(ROOT / "sandbox_product/sandbox.product").getroot()
    product_repositories = [
        repository.attrib["location"].rstrip("/")
        for repository in product.findall("./repositories/repository")
        if repository.attrib.get("location")
    ]

    pom_release = single_release(pom_repositories, RELEASE_PATTERN, "Maven Eclipse release")
    target_release = single_release(target_repositories, RELEASE_PATTERN, "target Eclipse release")
    product_release = single_release(product_repositories, RELEASE_PATTERN, "product Eclipse release")

    oomph_text = (ROOT / "sandbox_oomph/sandbox.setup").read_text(encoding="utf-8")
    oomph_match = re.search(
        r'name="eclipse\.target\.version".*?defaultValue="(\d{4}-\d{2})"',
        oomph_text,
        flags=re.DOTALL,
    )
    if not oomph_match:
        raise ValueError("Missing eclipse.target.version defaultValue in Oomph setup")
    oomph_release = oomph_match.group(1)

    releases = {
        "root pom.xml": pom_release,
        "sandbox_target/eclipse.target": target_release,
        "sandbox_product/sandbox.product": product_release,
        "sandbox_oomph/sandbox.setup": oomph_release,
    }
    if len(set(releases.values())) != 1:
        details = ", ".join(f"{path}={release}" for path, release in releases.items())
        raise ValueError(f"Eclipse release repositories are inconsistent: {details}")

    pom_orbit = single_release(pom_repositories, ORBIT_PATTERN, "Maven Orbit aggregation")
    target_orbit = single_release(target_repositories, ORBIT_PATTERN, "target Orbit aggregation")
    if pom_orbit != target_orbit:
        raise ValueError(
            f"Orbit aggregation is inconsistent: pom.xml={pom_orbit}, "
            f"sandbox_target/eclipse.target={target_orbit}"
        )

    properties = pom.find("m:properties", MAVEN_NS)
    assert properties is not None
    declared_bc = text(properties.find("m:bouncycastle.version", MAVEN_NS), "bouncycastle.version")
    declared_bc_osgi = osgi_version(declared_bc)

    target_bc = {
        unit.attrib["id"]: unit.attrib["version"]
        for unit in target.findall(".//unit")
        if unit.attrib.get("id") in BOUNCY_CASTLE_IDS
    }
    if set(target_bc) != set(BOUNCY_CASTLE_IDS):
        raise ValueError(f"Target Bouncy Castle units are incomplete: {target_bc}")
    if set(target_bc.values()) != {declared_bc_osgi}:
        raise ValueError(
            f"Bouncy Castle Maven version {declared_bc} ({declared_bc_osgi} as OSGi) "
            f"does not match target units {target_bc}"
        )

    pom_bc: dict[str, str] = {}
    for requirement in pom.findall(".//m:extraRequirements/m:requirement", MAVEN_NS):
        identifier = text(requirement.find("m:id", MAVEN_NS), "extra requirement id")
        if identifier in BOUNCY_CASTLE_IDS:
            pom_bc[identifier] = text(
                requirement.find("m:versionRange", MAVEN_NS),
                f"versionRange for {identifier}",
            )
    if pom_bc != target_bc:
        raise ValueError(
            f"Bouncy Castle Maven extra requirements do not match target units: "
            f"expected {target_bc}, found {pom_bc}"
        )

    print(
        f"Repository alignment verified: Eclipse {target_release}, "
        f"Orbit {target_orbit}, Bouncy Castle Maven {declared_bc} / OSGi {declared_bc_osgi}."
    )


def main() -> int:
    try:
        verify()
    except (OSError, ET.ParseError, ValueError) as error:
        print(f"Repository alignment error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
