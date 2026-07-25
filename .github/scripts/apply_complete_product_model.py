#!/usr/bin/env python3
"""One-time branch migration for the complete IDE product and distribution profile."""

from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

SANDBOX_FEATURES = (
    "sandbox_encoding_quickfix_feature",
    "sandbox_platform_helper_feature",
    "sandbox_functional_converter_feature",
    "sandbox_tools_feature",
    "sandbox_triggerpattern_feature",
    "sandbox_xml_cleanup_feature",
    "sandbox_jface_cleanup_feature",
    "sandbox_junit_cleanup_feature",
    "sandbox_method_reuse_feature",
    "sandbox_usage_view_feature",
    "sandbox_cleanup_application_feature",
    "sandbox_css_cleanup_feature",
    "sandbox_extra_search_feature",
    "sandbox_int_to_enum_feature",
    "sandbox_use_general_type_feature",
)


def read(path: str) -> str:
    return Path(path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    Path(path).write_text(text, encoding="utf-8")


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    if old not in text:
        raise SystemExit(f"Expected text not found in {path}: {old!r}")
    write(path, text.replace(old, new, 1))


def dependency_section(features: tuple[str, ...]) -> str:
    lines = ["\t<dependencies>"]
    for feature in features:
        lines.extend((
            "\t\t<dependency>",
            "\t\t\t<groupId>org.sandbox</groupId>",
            f"\t\t\t<artifactId>{feature}</artifactId>",
            "\t\t</dependency>",
        ))
    lines.append("\t</dependencies>")
    return "\n".join(lines)


# Root profiles: focused product/repo builds plus one canonical distribution.
root_path = "pom.xml"
root = read(root_path)
if "\t\t\t<id>distribution</id>" not in root:
    marker = "\t\t<!-- Product profile: Build Eclipse product with p2-director\n\t\tmaterialization -->"
    profile = """\t\t<!-- Complete distributable: standalone IDE archives, p2 update site and
\t\truntime installation verification. -->
\t\t<profile>
\t\t\t<id>distribution</id>
\t\t\t<activation>
\t\t\t\t<property>
\t\t\t\t\t<name>distribution.smoke</name>
\t\t\t\t\t<value>true</value>
\t\t\t\t</property>
\t\t\t</activation>
\t\t\t<modules>
\t\t\t\t<module>sandbox_product</module>
\t\t\t\t<module>sandbox_updatesite</module>
\t\t\t</modules>
\t\t</profile>

"""
    if marker not in root:
        raise SystemExit("Root product profile marker not found")
    write(root_path, root.replace(marker, profile + marker, 1))

# Both delivery modules declare the complete feature graph.
for path in ("sandbox_product/pom.xml", "sandbox_updatesite/pom.xml"):
    text = read(path)
    updated, count = re.subn(
        r"\t<dependencies>.*?\t</dependencies>",
        dependency_section(SANDBOX_FEATURES),
        text,
        count=1,
        flags=re.DOTALL,
    )
    if count != 1:
        raise SystemExit(f"Could not replace dependency section in {path}")
    write(path, updated)

replace_once(
    "sandbox_product/pom.xml",
    "<finalName>sandbox.repository_${rcp-version}-1.2.2</finalName>",
    "<finalName>sandbox-product-${project.version}</finalName>",
)
replace_once(
    "sandbox_updatesite/pom.xml",
    "<id>distribution-smoke</id>",
    "<id>distribution</id>",
)

# The standalone product opens the IDE normally; the CLI remains explicit.
product_path = "sandbox_product/sandbox.product"
product = read(product_path)
product = product.replace(
    'application="org.sandbox.jdt.core.JavaCleanup"',
    'application="org.eclipse.ui.ide.workbench"',
    1,
)
product = product.replace('includeLaunchers="false"', 'includeLaunchers="true"', 1)
egit_marker = '      <feature id="org.eclipse.pde" installMode="root"/>'
if '<feature id="org.eclipse.egit"' not in product:
    if egit_marker not in product:
        raise SystemExit("Product PDE feature marker not found")
    product = product.replace(
        egit_marker,
        '      <feature id="org.eclipse.egit" installMode="root"/>\n'
        '      <feature id="org.eclipse.jgit" installMode="root"/>\n'
        + egit_marker,
        1,
    )
write(product_path, product)

# Extend the Maven-bound alignment validator with product-model invariants.
validator_path = ".github/scripts/verify_target_repository_alignment.py"
validator = read(validator_path)
helper_marker = "\ndef verify() -> None:\n"
helper = '''

def maven_dependency_artifacts(path: Path) -> set[str]:
    root = ET.parse(path).getroot()
    return {
        text(dependency.find("m:artifactId", MAVEN_NS), f"dependency artifactId in {path.name}")
        for dependency in root.findall("m:dependencies/m:dependency", MAVEN_NS)
    }


def maven_profile_modules(root: ET.Element, profile_id: str) -> set[str]:
    for profile in root.findall("m:profiles/m:profile", MAVEN_NS):
        identifier = profile.find("m:id", MAVEN_NS)
        if identifier is not None and (identifier.text or "").strip() == profile_id:
            return {
                (module.text or "").strip()
                for module in profile.findall("m:modules/m:module", MAVEN_NS)
                if (module.text or "").strip()
            }
    raise ValueError(f"Missing Maven profile {profile_id}")
'''
if "def maven_dependency_artifacts" not in validator:
    if helper_marker not in validator:
        raise SystemExit("Validator helper insertion marker not found")
    validator = validator.replace(helper_marker, helper + helper_marker, 1)

validation_marker = "    product_repositories = [\n"
validation_end = "    ]\n\n    pom_release = single_release"
if "Standalone product application" not in validator:
    start = validator.find(validation_marker)
    if start < 0:
        raise SystemExit("Product repositories block not found")
    end = validator.find(validation_end, start)
    if end < 0:
        raise SystemExit("Product repositories block end not found")
    end += len("    ]\n")
    checks = '''

    # Standalone product application and delivery model.
    if product.attrib.get("application") != "org.eclipse.ui.ide.workbench":
        raise ValueError(
            "Standalone product application must be org.eclipse.ui.ide.workbench, "
            f"found {product.attrib.get('application')!r}"
        )
    if product.attrib.get("includeLaunchers") != "true":
        raise ValueError("Standalone product must include native launchers")

    product_features = {
        feature.attrib["id"]
        for feature in product.findall("./features/feature")
        if feature.attrib.get("id")
    }
    required_base_features = {
        "org.eclipse.platform",
        "org.eclipse.jdt",
        "org.eclipse.pde",
        "org.eclipse.equinox.p2.user.ui",
        "org.eclipse.egit",
        "org.eclipse.jgit",
    }
    missing_base = required_base_features - product_features
    if missing_base:
        raise ValueError(f"Standalone product is missing base features: {sorted(missing_base)}")

    category = ET.parse(ROOT / "sandbox_updatesite/category.xml").getroot()
    published_features = {
        feature.attrib["id"]
        for feature in category.findall("./feature")
        if feature.attrib.get("id", "").startswith("sandbox_")
    }
    product_sandbox_features = {
        feature for feature in product_features if feature.startswith("sandbox_")
    }
    if product_sandbox_features != published_features:
        raise ValueError(
            "Product and update-site Sandbox features differ: "
            f"product-only={sorted(product_sandbox_features - published_features)}, "
            f"update-site-only={sorted(published_features - product_sandbox_features)}"
        )

    for module in ("sandbox_product", "sandbox_updatesite"):
        dependencies = {
            artifact
            for artifact in maven_dependency_artifacts(ROOT / module / "pom.xml")
            if artifact.startswith("sandbox_") and artifact.endswith("_feature")
        }
        if dependencies != published_features:
            raise ValueError(
                f"{module}/pom.xml does not declare the complete feature graph: "
                f"missing={sorted(published_features - dependencies)}, "
                f"extra={sorted(dependencies - published_features)}"
            )

    distribution_modules = maven_profile_modules(pom, "distribution")
    expected_distribution_modules = {"sandbox_product", "sandbox_updatesite"}
    if distribution_modules != expected_distribution_modules:
        raise ValueError(
            "The distribution Maven profile must contain product and update site: "
            f"found {sorted(distribution_modules)}"
        )

    updatesite_pom = ET.parse(ROOT / "sandbox_updatesite/pom.xml").getroot()
    maven_profile_modules(updatesite_pom, "distribution")
'''
    validator = validator[:end] + checks + validator[end:]
write(validator_path, validator)

# Verify the default IDE application at runtime as well as p2 and CLI paths.
smoke_path = ".github/scripts/smoke_test_distribution.sh"
smoke = read(smoke_path)
smoke_marker = "grep -Eq 'org\\.eclipse\\.|sandbox_' \"$EVIDENCE_DIR/materialized-product.log\"\n"
if "default IDE workbench" not in smoke:
    if smoke_marker not in smoke:
        raise SystemExit("Smoke-test materialized-product marker not found")
    ide_probe = r'''

# Prove that a normal product launch starts the IDE workbench rather than the
# cleanup CLI. The healthy workbench remains alive until timeout terminates it.
PRODUCT_CONFIG="$PRODUCT_ROOT/configuration/config.ini"
[[ -f "$PRODUCT_CONFIG" ]] || { echo "Missing product config.ini: $PRODUCT_CONFIG" >&2; exit 1; }
grep -Eq '^eclipse\.application=org\.eclipse\.ui\.ide\.workbench\r?$' "$PRODUCT_CONFIG"
set +e
(
  cd "$PRODUCT_ROOT"
  timeout 30s xvfb-run -a java -Declipse.p2.mirrors=false \
    -jar "$PRODUCT_LAUNCHER" -nosplash -consoleLog \
    -data "$SMOKE_ROOT/ide-workspace"
) > "$EVIDENCE_DIR/ide-workbench.log" 2>&1
IDE_STATUS=$?
set -e
if [[ "$IDE_STATUS" -ne 124 ]]; then
  echo "Default IDE workbench exited before the smoke-test timeout (status $IDE_STATUS)." >&2
  cat "$EVIDENCE_DIR/ide-workbench.log" >&2
  exit 1
fi
if grep -Eq 'No application id has been found|Application ".*" could not be found' \
   "$EVIDENCE_DIR/ide-workbench.log"; then
  cat "$EVIDENCE_DIR/ide-workbench.log" >&2
  exit 1
fi
'''
    smoke = smoke.replace(smoke_marker, smoke_marker + ide_probe, 1)

summary_marker = "- Materialized product started and listed installed roots: **PASS**\n"
if "Default standalone launch opened the Eclipse IDE workbench" not in smoke:
    if summary_marker not in smoke:
        raise SystemExit("Smoke-test summary marker not found")
    smoke = smoke.replace(
        summary_marker,
        summary_marker
        + "- Default standalone launch opened the Eclipse IDE workbench: **PASS**\n",
        1,
    )
write(smoke_path, smoke)

# Workflows become thin wrappers around the same Maven profile.
for path in (
    ".github/workflows/distribution-smoke.yml",
    ".github/workflows/deploy-snapshot.yml",
):
    text = read(path)
    text = text.replace("-Pproduct,repo", "-Pdistribution")
    text = text.replace("            -Ddistribution.smoke=true \\\n", "")
    write(path, text)

release_path = ".github/workflows/deploy-release.yml"
release = read(release_path)
old_release_build = '''          xvfb-run --auto-servernum mvn \\
            -Pproduct,repo,cli-dist,maven-plugin,benchmark \\
            -T 1C \\
            --batch-mode \\
            -Dtycho.localArtifacts=ignore \\
'''
new_release_build = '''          xvfb-run --auto-servernum mvn \\
            -Pdistribution,cli-dist,maven-plugin,benchmark \\
            --batch-mode \\
            -Dtycho.localArtifacts=ignore \\
'''
if old_release_build not in release:
    raise SystemExit("Release Maven build block not found")
release = release.replace(old_release_build, new_release_build, 1)
release = release.replace(
    '''      - name: Validate release repository and Linux product
        run: bash .github/scripts/verify_linux_distribution.sh

      - name: Install, start, and transform with the release distribution
        run: bash .github/scripts/smoke_test_distribution.sh

''',
    "",
    1,
)
write(release_path, release)

# User-facing build documentation.
readme_path = "README.md"
readme = read(readme_path)
readme = readme.replace(
    "- **Full Build**: `mvn -Pproduct,repo -T 1C verify`",
    "- **Standalone IDE Product**: `mvn -Pproduct clean verify`\n"
    "- **Complete Distribution Gate (Linux)**: `xvfb-run --auto-servernum mvn -Pdistribution --batch-mode -Dtycho.localArtifacts=ignore clean verify`",
)
write(readme_path, readme)

compatibility_path = "docs/distribution-compatibility.md"
compatibility = read(compatibility_path)
compatibility = compatibility.replace("-Pproduct,repo", "-Pdistribution")
compatibility = compatibility.replace("  -Ddistribution.smoke=true \\\n", "")
compatibility = compatibility.replace(
    "The `distribution.smoke` property activates the verification bound to the `sandbox_updatesite` Maven `verify` phase.",
    "The `distribution` profile adds both heavy delivery modules and activates the verification bound to the `sandbox_updatesite` Maven `verify` phase.",
)
write(compatibility_path, compatibility)

product_readme_path = "sandbox_product/README.md"
product_readme = read(product_readme_path)
product_readme = product_readme.replace(
    "# Build all modules including product\nmvn clean verify",
    "# Build the standalone Eclipse IDE archives\nmvn -Pproduct clean verify",
)
product_readme = product_readme.replace("    mvn clean verify", "    mvn -Pproduct clean verify")
product_readme = product_readme.replace("- Marketplace client", "- p2 installation UI (`Help` → `Install New Software...`)")
product_readme = product_readme.replace("| macOS | x86_64, arm64 | .tar.gz |", "| macOS | x86_64 | .tar.gz |")
product_readme = product_readme.replace("- **arm64**: Apple Silicon (M1/M2/M3) Macs\n", "")
product_readme = product_readme.replace("├── sandbox-macosx.cocoa.arm64.tar.gz\n", "")
product_readme = product_readme.replace("    ├── macosx/cocoa.arm64/\n", "")
if "### Building and Verifying the Complete Distribution" not in product_readme:
    marker = "### Running the Product\n"
    section = '''### Building and Verifying the Complete Distribution

The complete delivery build creates the standalone IDE archives and the Marketplace-compatible p2 update site, then installs the published Sandbox features into a fresh Eclipse destination and exercises both the default IDE workbench and the cleanup application:

```bash
xvfb-run --auto-servernum mvn \\
  -Pdistribution \\
  --batch-mode \\
  -Dtycho.localArtifacts=ignore \\
  clean verify
```

Use `-Pproduct` when only the standalone IDE archives are needed. Use `-Prepo` when only the p2 update site is needed.

'''
    if marker not in product_readme:
        raise SystemExit("Product README running marker not found")
    product_readme = product_readme.replace(marker, section + marker, 1)
product_readme = product_readme.replace(
    "The **Product** module builds a complete Eclipse product distribution that includes all sandbox cleanup plugins.",
    "The **Product** module builds a complete JDT-based Eclipse IDE distribution that includes every published Sandbox feature.",
)
write(product_readme_path, product_readme)

for path in (
    "pom.xml",
    "sandbox_product/pom.xml",
    "sandbox_updatesite/pom.xml",
    "sandbox_product/sandbox.product",
):
    ET.parse(path)

subprocess.run(["python3", ".github/scripts/verify_target_repository_alignment.py"], check=True)
subprocess.run(["python3", ".github/scripts/validate_capability_inventory.py", "--check"], check=True)
