#!/usr/bin/env python3
"""Run independent Tycho reactors against the output of CleanupPatchLifecycleTest."""
import argparse
from pathlib import Path
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET


def element(parent, tag, text=None):
    child = ET.SubElement(parent, tag)
    child.text = text
    return child


def plugin(plugins, artifact):
    result = element(plugins, "plugin")
    element(result, "groupId", "org.eclipse.tycho")
    element(result, "artifactId", artifact)
    element(result, "version", "5.0.4")
    return result


def project_pom(artifact, packaging, parent=False):
    root = ET.Element("project", xmlns="http://maven.apache.org/POM/4.0.0")
    element(root, "modelVersion", "4.0.0")
    if parent:
        inherited = element(root, "parent")
        for name, value in (("groupId", "qa"), ("artifactId", "pde-suite"), ("version", "1.0.0")):
            element(inherited, name, value)
    else:
        element(root, "groupId", "qa")
    element(root, "artifactId", artifact)
    if not parent:
        element(root, "version", "1.0.0")
    element(root, "packaging", packaging)
    return root


def save_pom(directory, root):
    ET.indent(root)
    ET.ElementTree(root).write(directory / "pom.xml", encoding="utf-8", xml_declaration=True)


def run(project, phase, repository, maven_args):
    target = project / "feature/test.target"
    original = target.read_text(encoding="utf-8")
    if repository:
        before = 'location="https://download.eclipse.org/releases/2026-09/"'
        if original.count(before) != 1:
            raise ValueError(f"unexpected repository definition in {target}")
        original = original.replace(before, f'location="{repository}"')
    # The input target is evidence. A separate launcher target can redirect its
    # single repository to a local p2 mirror while retaining every actual IU.
    (project / "test.target").write_text(original, encoding="utf-8")
    (project / ".mvn").mkdir(exist_ok=True)
    reactor = project_pom("pde-suite", "pom")
    modules = element(reactor, "modules")
    for name in ("bundle", "feature"):
        element(modules, "module", name)
    plugins = element(element(reactor, "build"), "plugins")
    element(plugin(plugins, "tycho-maven-plugin"), "extensions", "true")
    config = element(plugin(plugins, "target-platform-configuration"), "configuration")
    element(config, "executionEnvironment", "JavaSE-21")
    element(element(config, "target"), "file", "${maven.multiModuleProjectDirectory}/test.target")
    save_pom(project, reactor)
    bundle = project_pom("probe.tests", "eclipse-test-plugin", True)
    plugins = element(element(bundle, "build"), "plugins")
    compiler = element(plugin(plugins, "tycho-compiler-plugin"), "configuration")
    element(compiler, "showWarnings", "true")
    element(compiler, "failOnWarning", "true")
    config = element(plugin(plugins, "tycho-surefire-plugin"), "configuration")
    element(config, "providerHint", "junit4" if phase == "before" else "junit5")
    element(config, "useUIHarness", "false")
    element(config, "useUIThread", "false")
    element(element(config, "includes"), "include", "**/AllTests.class")
    save_pom(project / "bundle", bundle)
    save_pom(project / "feature", project_pom("probe.test.feature", "eclipse-feature", True))
    subprocess.run(["mvn", *maven_args, "verify"], cwd=project, check=True)
    tests = []
    for file in sorted((project / "bundle/target/surefire-reports").glob("TEST-*.xml")):
        for test in ET.parse(file).iter("testcase"):
            if any(child.tag in ("failure", "error", "skipped") for child in test):
                raise AssertionError(f"{phase} failed or skipped {test.attrib}")
            tests.append((test.get("classname"), test.get("name")))
    if tests != [("probe.ExampleTest", "adds")]:
        raise AssertionError(f"{phase} suite discovery returned {tests!r}")
    if not (project / "feature/target/probe.test.feature-1.0.0.jar").is_file():
        raise AssertionError(f"{phase} feature was not packaged")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("capture", type=Path)
    parser.add_argument("--repository", help="optional URI of a p2 repository containing the target IUs")
    arguments, maven = parser.parse_known_args()
    with tempfile.TemporaryDirectory(prefix="sandbox-pde-suite-") as builds:
        for stage in ("before", "after"):
            project = Path(builds) / stage
            exported = arguments.capture.resolve() / stage
            for name in ("bundle", "feature"):
                shutil.copytree(exported / name, project / name, ignore=shutil.ignore_patterns("target", "pom.xml"))
            run(project, stage, arguments.repository, maven)
