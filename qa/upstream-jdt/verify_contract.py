#!/usr/bin/env python3
"""Entrypoint for the upstream JDT QA contract, including headless SWT lifecycle checks."""

from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

import verify_contract_core as core


def validate_application(root: Path) -> None:
    plugin_path = root / "sandbox_cleanup_application/plugin.xml"
    plugin = ET.parse(plugin_path).getroot()
    application_id = "org.sandbox.jdt.core.ProjectWideJavaCleanup"
    expected_class = (
        "org.sandbox.jdt.core.cleanupapp."
        "HeadlessProjectWideCodeCleanupApplication"
    )
    registered_class = None
    for extension in plugin.findall("extension"):
        if extension.attrib.get("id") != application_id:
            continue
        run = extension.find("./application/run")
        registered_class = None if run is None else run.attrib.get("class")
        break
    if registered_class != expected_class:
        core.fail(
            "Project-wide application registration is "
            f"{registered_class!r}, expected {expected_class!r}"
        )

    source = (
        root
        / "sandbox_cleanup_application/src/org/sandbox/jdt/core/cleanupapp/"
        "ProjectWideCodeCleanupApplication.java"
    ).read_text(encoding="utf-8")
    wrapper = (
        root
        / "sandbox_cleanup_application/src/org/sandbox/jdt/core/cleanupapp/"
        "HeadlessProjectWideCodeCleanupApplication.java"
    ).read_text(encoding="utf-8")
    manifest = (
        root / "sandbox_cleanup_application/META-INF/MANIFEST.MF"
    ).read_text(encoding="utf-8")

    for required in (
        "new CleanUpRefactoring()",
        "refactoring.addCompilationUnit(source.unit())",
        'case "--project-location"',
        "IMultiFileCleanUpDiagnosticsProvider",
        "restore(sources, monitor, errors)",
        "writeReport(arguments.report()",
    ):
        if required not in source:
            core.fail(f"Project-wide application is missing contract fragment: {required}")

    for required in (
        "new ProjectWideCodeCleanupApplication()",
        "delegate.start(context)",
        "delegate.stop()",
        "finally",
        "disposeDisplay()",
        "Display.getDefault()",
        "display.dispose()",
    ):
        if required not in wrapper:
            core.fail(
                f"Headless project-wide application is missing lifecycle fragment: {required}"
            )

    if "sandbox_common_core" not in manifest or "org.eclipse.swt" not in manifest:
        core.fail(
            "Cleanup application does not require the diagnostics and SWT lifecycle bundles"
        )


core.validate_application = validate_application


if __name__ == "__main__":
    try:
        raise SystemExit(core.main())
    except (ET.ParseError, OSError, ValueError, core.json.JSONDecodeError) as exc:
        print(f"Upstream JDT QA contract validation failed: {exc}", file=sys.stderr)
        raise SystemExit(1) from exc
