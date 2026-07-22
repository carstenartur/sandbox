#!/usr/bin/env python3
"""Generate machine-readable build provenance for one repository commit."""

from __future__ import annotations

import argparse
import hashlib
from html import escape
import json
import os
import platform
import re
import subprocess
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable
import xml.etree.ElementTree as ET

MAVEN_NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}
DISTRIBUTION_SUFFIXES = (".zip", ".tar.gz", ".tgz", ".dmg", ".exe")
P2_METADATA_NAMES = {
    "artifacts.jar",
    "artifacts.xml",
    "content.jar",
    "content.xml",
    "p2.index",
}


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def tree_digest(root: Path) -> dict[str, Any]:
    files = sorted(path for path in root.rglob("*") if path.is_file())
    digest = hashlib.sha256()
    total_bytes = 0
    for path in files:
        relative = path.relative_to(root).as_posix()
        file_digest = sha256_file(path)
        size = path.stat().st_size
        total_bytes += size
        digest.update(relative.encode("utf-8"))
        digest.update(b"\0")
        digest.update(file_digest.encode("ascii"))
        digest.update(b"\n")
    return {
        "path": root.as_posix(),
        "file_count": len(files),
        "total_bytes": total_bytes,
        "sha256_tree": digest.hexdigest(),
    }


def parse_pom(repo_root: Path) -> dict[str, Any]:
    pom = repo_root / "pom.xml"
    root = ET.parse(pom).getroot()
    properties = root.find("m:properties", MAVEN_NAMESPACE)

    def property_value(name: str) -> str | None:
        if properties is None:
            return None
        element = properties.find(f"m:{name}", MAVEN_NAMESPACE)
        return element.text.strip() if element is not None and element.text else None

    version = root.findtext("m:version", default="", namespaces=MAVEN_NAMESPACE).strip()
    repository_urls = [
        element.text.strip()
        for element in root.findall("m:repositories/m:repository/m:url", MAVEN_NAMESPACE)
        if element.text
    ]
    release = None
    for url in repository_urls:
        match = re.search(r"/releases/([^/]+)/?", url)
        if match:
            release = match.group(1)
            break

    return {
        "project_version": version,
        "java_version": property_value("java-version"),
        "java_execution_environment": property_value("java-execenv"),
        "tycho_version": property_value("tycho-version"),
        "spotbugs_maven_plugin_version": property_value("spotbugs-version"),
        "eclipse_release": release,
        "p2_repositories": repository_urls,
    }


def target_definitions(repo_root: Path) -> list[dict[str, Any]]:
    target_dir = repo_root / "sandbox_target"
    if not target_dir.is_dir():
        return []
    return [
        {
            "path": path.relative_to(repo_root).as_posix(),
            "size_bytes": path.stat().st_size,
            "sha256": sha256_file(path),
        }
        for path in sorted(target_dir.glob("*.target"))
    ]


def load_test_inventory(repo_root: Path) -> dict[str, Any] | None:
    path = repo_root / "test-report.json"
    if not path.is_file():
        return None
    with path.open(encoding="utf-8") as stream:
        report = json.load(stream)
    return report.get("summary")


def executed_test_totals(repo_root: Path) -> dict[str, int]:
    totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0, "report_files": 0}
    combined_reports = sorted(repo_root.glob("**/combined-tests.xml"))
    report_paths = combined_reports or sorted(
        repo_root.glob("**/target/surefire-reports/TEST-*.xml")
    )
    for path in report_paths:
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        suites: Iterable[ET.Element]
        if root.tag.rsplit("}", 1)[-1] == "testsuite":
            suites = (root,)
        else:
            suites = [child for child in root if child.tag.rsplit("}", 1)[-1] == "testsuite"]
        totals["report_files"] += 1
        for suite in suites:
            for key in ("tests", "failures", "errors", "skipped"):
                try:
                    totals[key] += int(suite.attrib.get(key, "0"))
                except ValueError:
                    pass
    return totals


def distribution_artifacts(repo_root: Path) -> dict[str, Any]:
    product_root = repo_root / "sandbox_product" / "target" / "products"
    product_tree = None
    product_files: list[dict[str, Any]] = []
    if product_root.is_dir():
        product_tree = tree_digest(product_root)
        product_tree["path"] = product_root.relative_to(repo_root).as_posix()
        for path in sorted(candidate for candidate in product_root.rglob("*") if candidate.is_file()):
            if not path.name.lower().endswith(DISTRIBUTION_SUFFIXES):
                continue
            product_files.append({
                "path": path.relative_to(repo_root).as_posix(),
                "size_bytes": path.stat().st_size,
                "sha256": sha256_file(path),
            })

    update_site_root = repo_root / "sandbox_updatesite" / "target" / "repository"
    update_site = None
    if update_site_root.is_dir():
        update_site = tree_digest(update_site_root)
        update_site["path"] = update_site_root.relative_to(repo_root).as_posix()
        update_site["metadata"] = [
            {
                "path": path.relative_to(repo_root).as_posix(),
                "size_bytes": path.stat().st_size,
                "sha256": sha256_file(path),
            }
            for path in sorted(update_site_root.rglob("*"))
            if path.is_file() and path.name in P2_METADATA_NAMES
        ]

    return {
        "products": product_files,
        "product_tree": product_tree,
        "product_status": "built" if product_tree else "not_built_in_this_run",
        "update_site": update_site,
        "update_site_status": "built" if update_site else "not_built_in_this_run",
    }


def command_version(command: list[str]) -> str | None:
    try:
        result = subprocess.run(command, check=True, capture_output=True, text=True)
    except (OSError, subprocess.CalledProcessError):
        return None
    output = result.stdout.strip() or result.stderr.strip()
    return output.splitlines()[0] if output else None


def load_related_workflows(path: Path | None) -> list[dict[str, Any]]:
    if path is None or not path.is_file():
        return []
    with path.open(encoding="utf-8") as stream:
        value = json.load(stream)
    if not isinstance(value, list):
        raise ValueError("Related workflow data must be a JSON array")
    return value


def build_manifest(repo_root: Path, related_workflows: Path | None) -> dict[str, Any]:
    repository = os.environ.get("GITHUB_REPOSITORY", "local/sandbox")
    commit_sha = os.environ.get("GITHUB_SHA") or command_version(["git", "rev-parse", "HEAD"])
    run_id = os.environ.get("GITHUB_RUN_ID")
    server_url = os.environ.get("GITHUB_SERVER_URL", "https://github.com")
    workflow_run_url = (
        f"{server_url}/{repository}/actions/runs/{run_id}" if run_id else None
    )

    return {
        "schema_version": 1,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "repository": repository,
        "commit_sha": commit_sha,
        "commit_url": f"{server_url}/{repository}/commit/{commit_sha}" if commit_sha else None,
        "ref": os.environ.get("GITHUB_REF"),
        "event_name": os.environ.get("GITHUB_EVENT_NAME"),
        "provenance_workflow": {
            "name": os.environ.get("GITHUB_WORKFLOW"),
            "run_id": run_id,
            "run_number": os.environ.get("GITHUB_RUN_NUMBER"),
            "run_attempt": os.environ.get("GITHUB_RUN_ATTEMPT"),
            "run_url": workflow_run_url,
            "source_java_ci_run_id": os.environ.get("SOURCE_JAVA_CI_RUN_ID"),
            "source_java_ci_run_url": os.environ.get("SOURCE_JAVA_CI_RUN_URL"),
        },
        "toolchain": {
            **parse_pom(repo_root),
            "runtime_python": platform.python_version(),
            "runtime_java": command_version(["java", "-version"]),
            "runtime_maven": command_version(["mvn", "--version"]),
            "target_definitions": target_definitions(repo_root),
        },
        "tests": {
            "inventory": load_test_inventory(repo_root),
            "executed": executed_test_totals(repo_root),
        },
        "artifacts": distribution_artifacts(repo_root),
        "related_workflows": load_related_workflows(related_workflows),
        "patched_jdt_ui": {
            "commit": os.environ.get("PATCHED_JDT_UI_COMMIT"),
            "version": os.environ.get("PATCHED_JDT_UI_VERSION"),
        },
    }


def render_html(manifest: dict[str, Any]) -> str:
    commit_sha = escape(str(manifest.get("commit_sha") or "unknown"))
    commit_url = escape(str(manifest.get("commit_url") or "#"), quote=True)
    workflows = manifest.get("related_workflows", [])
    rows = "\n".join(
        "<tr><td>{name}</td><td>{conclusion}</td><td><a href=\"{url}\">run {run_id}</a></td></tr>".format(
            name=escape(str(workflow.get("name", "unknown"))),
            conclusion=escape(str(workflow.get("conclusion") or workflow.get("status", "unknown"))),
            url=escape(str(workflow.get("html_url", "#")), quote=True),
            run_id=escape(str(workflow.get("id", "?"))),
        )
        for workflow in workflows
    )
    inventory = manifest.get("tests", {}).get("inventory") or {}
    return f"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Sandbox build provenance</title>
  <style>
    body {{ font-family: system-ui, sans-serif; max-width: 1100px; margin: 0 auto; padding: 2rem; }}
    code {{ background: #f3f3f3; padding: .15rem .3rem; }}
    table {{ border-collapse: collapse; width: 100%; }}
    th, td {{ border: 1px solid #ddd; padding: .6rem; text-align: left; }}
    th {{ background: #f3f3f3; }}
  </style>
</head>
<body>
  <h1>Sandbox build provenance</h1>
  <p>Commit: <a href="{commit_url}"><code>{commit_sha}</code></a></p>
  <p>Static test inventory: {inventory.get('total_tests', 'N/A')} tests,
     {inventory.get('disabled_tests', 'N/A')} disabled.</p>
  <p><a href="latest.json">Machine-readable manifest</a></p>
  <h2>Validated workflow runs</h2>
  <table>
    <thead><tr><th>Workflow</th><th>Conclusion</th><th>Run</th></tr></thead>
    <tbody>{rows}</tbody>
  </table>
</body>
</html>
"""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository-root", type=Path, default=Path.cwd())
    parser.add_argument("--related-workflows", type=Path)
    parser.add_argument("--output", type=Path, default=Path("build-manifest.json"))
    parser.add_argument("--html-output", type=Path)
    args = parser.parse_args()

    repo_root = args.repository_root.resolve()
    manifest = build_manifest(repo_root, args.related_workflows)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    if args.html_output:
        args.html_output.parent.mkdir(parents=True, exist_ok=True)
        args.html_output.write_text(render_html(manifest), encoding="utf-8")


if __name__ == "__main__":
    main()
