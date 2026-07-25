#!/usr/bin/env python3
"""One-time repository migration; not part of the Maven build."""

from pathlib import Path
import re
import xml.etree.ElementTree as ET


def update(path: str, transform) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    changed = transform(text)
    if changed == text:
        raise SystemExit(f"No expected change made in {path}")
    file.write_text(changed, encoding="utf-8")


def root_pom(text: str) -> str:
    pattern = re.compile(
        r"\t\t<!-- Complete distributable: standalone IDE archives, p2 update site and\n"
        r"\t\truntime installation verification\. -->\n"
        r"\t\t<profile>\n"
        r"\t\t\t<id>distribution</id>.*?"
        r"\t\t</profile>",
        re.DOTALL,
    )
    replacement = """\t\t<!-- Complete distributable: standalone IDE archives, p2 update site and
\t\tJava-based runtime installation verification. -->
\t\t<profile>
\t\t\t<id>distribution</id>
\t\t\t<modules>
\t\t\t\t<module>sandbox_product</module>
\t\t\t\t<module>sandbox_updatesite</module>
\t\t\t\t<module>sandbox_distribution_verify</module>
\t\t\t</modules>
\t\t</profile>"""
    changed, count = pattern.subn(replacement, text, count=1)
    if count != 1:
        raise SystemExit("Could not replace distribution profile in pom.xml")
    return changed


def readme(text: str) -> str:
    text = text.replace(
        "- **Complete Distribution Gate (Linux)**: `xvfb-run --auto-servernum mvn -Pdistribution --batch-mode -Dtycho.localArtifacts=ignore clean verify`",
        "- **Complete Distribution**: `mvn -Pdistribution --batch-mode -Dtycho.localArtifacts=ignore clean verify`",
    )
    text = text.replace(
        "- **Full Build**: `mvn -Pproduct,repo -T 1C verify`",
        "- **Standalone IDE Product**: `mvn -Pproduct clean verify`\
        "\n- **Complete Distribution**: `mvn -Pdistribution --batch-mode -Dtycho.localArtifacts=ignore clean verify`",
    )
    return text


def compatibility(text: str) -> str:
    block = re.compile(
        r"```bash\n"
        r"xvfb-run --auto-servernum mvn \\\n"
        r"(?:.*\n)*?"
        r"  clean verify\n"
        r"```",
        re.MULTILINE,
    )
    replacement = """```bash
mvn -Pdistribution \\
  --batch-mode \\
  -Dtycho.localArtifacts=ignore \\
  clean verify
```"""
    text, count = block.subn(replacement, text, count=1)
    if count != 1:
        raise SystemExit("Could not replace distribution command in compatibility documentation")
    text = text.replace(
        "On Linux, install the GTK/Xvfb runtime packages listed in `.github/workflows/distribution-smoke.yml`, then run exactly the same Maven entry point as CI:",
        "Run the same Maven entry point on Windows, Linux or macOS. A headless Linux CI runner additionally needs a virtual X display, but that is runner setup rather than part of the build:",
    )
    text = text.replace(
        "The `distribution` profile adds both heavy delivery modules and activates the verification bound to the `sandbox_updatesite` Maven `verify` phase. Do not add Maven parallelism to this command: the product must be materialized before the final update-site module provisions and starts the completed distribution.",
        "The `distribution` profile builds the product and update site, then executes the Java-only `sandbox_distribution_verify` module. Do not add Maven parallelism to this command: product materialization and repository assembly must finish before the final verification module runs.",
    )
    return text


def product_readme(text: str) -> str:
    text = text.replace(
        "xvfb-run --auto-servernum mvn \\\n  -Pdistribution \\\n  --batch-mode \\\n  -Dtycho.localArtifacts=ignore \\\n  clean verify",
        "mvn -Pdistribution \\\n  --batch-mode \\\n  -Dtycho.localArtifacts=ignore \\\n  clean verify",
    )
    text = text.replace(
        "The complete delivery build creates the standalone IDE archives and the Marketplace-compatible p2 update site, then installs the published Sandbox features into a fresh Eclipse destination and exercises both the default IDE workbench and the cleanup application:",
        "The complete delivery build creates the standalone IDE archives and the Marketplace-compatible p2 update site. Its final Java module then installs the published Sandbox features into a fresh Eclipse destination and exercises both the default IDE workbench and the cleanup application. No Bash or Python installation is required:",
    )
    return text


update("pom.xml", root_pom)
update("README.md", readme)
update("docs/distribution-compatibility.md", compatibility)
update("sandbox_product/README.md", product_readme)

for path in (
    "pom.xml",
    "sandbox_product/pom.xml",
    "sandbox_updatesite/pom.xml",
    "sandbox_target/pom.xml",
    "sandbox_distribution_verify/pom.xml",
):
    ET.parse(path)
