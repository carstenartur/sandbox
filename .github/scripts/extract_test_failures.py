#!/usr/bin/env python3
"""
Extract failed tests from JUnit XML reports and output them as Markdown.

This script is intended to be run from CI workflows. It recursively scans the
current working directory for Maven Surefire reports matching
``**/target/surefire-reports/TEST-*.xml``, extracts failures and errors, and
formats them into a Markdown summary suitable for use in pull request
comments or build logs.

Command-line interface
----------------------
- This script currently does **not** accept any command-line arguments.
- The set of reports to scan is determined solely by the current working
  directory when the script is invoked.

Output
------
- The generated Markdown report is written to **stdout** so that callers can
  capture or redirect it in pipelines (for example, into a GitHub PR comment
  body or an artifact file).
- Diagnostic warnings (e.g., XML parse errors) are written to **stderr**.

Security considerations
-----------------------
- This script uses xml.etree.ElementTree for XML parsing. While this is
  generally safe for trusted inputs (like locally generated JUnit reports in
  CI), it does not protect against XML entity expansion attacks (XML bombs) or
  external entity injection. For untrusted XML sources, consider using the
  defusedxml library instead.
"""

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

def extract_failures(reports_dir="."):
    """Extract all test failures from JUnit XML reports."""
    failures = []
    
    # Find all TEST-*.xml files
    for xml_path in Path(reports_dir).rglob("**/target/surefire-reports/TEST-*.xml"):
        try:
            tree = ET.parse(xml_path)
            root = tree.getroot()
            
            # Handle both <testsuite> and <testsuites> root elements
            testsuites = root.findall(".//testsuite") if root.tag == "testsuites" else [root]
            
            for testsuite in testsuites:
                suite_name = testsuite.get("name", "Unknown")
                
                for testcase in testsuite.findall("testcase"):
                    test_name = testcase.get("name", "Unknown")
                    class_name = testcase.get("classname", suite_name)
                    
                    # Check for failure
                    failure = testcase.find("failure")
                    if failure is not None:
                        message = failure.get("message", "No message")
                        stacktrace = failure.text or "No stacktrace"
                        # Truncate with indicators
                        truncated_message = message[:1000]
                        if len(message) > 1000:
                            truncated_message += "... (truncated)"
                        truncated_stacktrace = stacktrace[:5000]
                        if len(stacktrace) > 5000:
                            truncated_stacktrace += "\n... (truncated)"
                        failures.append({
                            "class": class_name,
                            "test": test_name,
                            "message": truncated_message,
                            "stacktrace": truncated_stacktrace
                        })
                    
                    # Check for error
                    error = testcase.find("error")
                    if error is not None:
                        message = error.get("message", "No message")
                        stacktrace = error.text or "No stacktrace"
                        # Truncate with indicators
                        truncated_message = message[:1000]
                        if len(message) > 1000:
                            truncated_message += "... (truncated)"
                        truncated_stacktrace = stacktrace[:5000]
                        if len(stacktrace) > 5000:
                            truncated_stacktrace += "\n... (truncated)"
                        failures.append({
                            "class": class_name,
                            "test": test_name,
                            "message": truncated_message,
                            "stacktrace": truncated_stacktrace
                        })
        except ET.ParseError as e:
            print(f"Warning: Could not parse {xml_path}: {e}", file=sys.stderr)
        except Exception as e:
            print(f"Warning: Error processing {xml_path}: {e}", file=sys.stderr)
    
    return failures

def escape_markdown(text):
    """Escape special markdown characters for use in tables."""
    # Replace characters that could break markdown table formatting
    return (text
        .replace("\\", "\\\\")
        .replace("|", "\\|")
        .replace("\n", " ")
        .replace("\r", "")
        .replace("`", "\\`")
        .replace("*", "\\*")
        .replace("_", "\\_")
        .replace("[", "\\[")
        .replace("]", "\\]"))

def format_as_markdown(failures):
    """Format failures as Markdown for PR comment."""
    if not failures:
        return "## ✅ All Tests Passed\n\n<!-- test-failures-comment -->\nNo test failures detected."
    
    lines = [
        "## ❌ Failed Tests Details",
        "",
        "<!-- test-failures-comment -->",
        "",
        f"**{len(failures)} test(s) failed:**",
        "",
        "| Test | Class | Message |",
        "|------|-------|---------|"
    ]
    
    for f in failures:
        # Escape special characters for markdown safety
        message = escape_markdown(f["message"])
        test = escape_markdown(f["test"])
        full_class = f["class"]
        short_class = full_class.split(".")[-1]
        
        # Escape characters for safe use in HTML title attribute
        class_title = (
            full_class.replace("&", "&amp;")
            .replace('"', "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        )
        class_cell = f'<span title="{class_title}">`{escape_markdown(short_class)}`</span>'
        lines.append(f"| `{test}` | {class_cell} | {message} |")
    
    # Add stacktraces in collapsible section
    lines.extend([
        "",
        "<details>",
        "<summary>📋 Stack Traces (click to expand)</summary>",
        ""
    ])
    
    for f in failures:
        lines.extend([
            f"### {f['class']}.{f['test']}",
            "",
            "```",
            f["stacktrace"],
            "```",
            ""
        ])
    
    lines.append("</details>")
    
    return "\n".join(lines)

def main():
    failures = extract_failures(".")
    markdown = format_as_markdown(failures)
    print(markdown)

if __name__ == "__main__":
    main()
