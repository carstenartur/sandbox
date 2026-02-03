#!/usr/bin/env python3
"""
Extract failed tests from JUnit XML reports and output as Markdown.
This script parses all TEST-*.xml files and extracts failure details.
"""

import os
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
                        failures.append({
                            "class": class_name,
                            "test": test_name,
                            "message": message[:200],  # Truncate long messages
                            "stacktrace": stacktrace[:500]  # Truncate long stacktraces
                        })
                    
                    # Check for error
                    error = testcase.find("error")
                    if error is not None:
                        message = error.get("message", "No message")
                        stacktrace = error.text or "No stacktrace"
                        failures.append({
                            "class": class_name,
                            "test": test_name,
                            "message": message[:200],
                            "stacktrace": stacktrace[:500]
                        })
        except ET.ParseError as e:
            print(f"Warning: Could not parse {xml_path}: {e}", file=sys.stderr)
        except Exception as e:
            print(f"Warning: Error processing {xml_path}: {e}", file=sys.stderr)
    
    return failures

def format_as_markdown(failures):
    """Format failures as Markdown for PR comment."""
    if not failures:
        return "## ✅ All Tests Passed\n\nNo test failures detected."
    
    lines = [
        "## ❌ Failed Tests Details",
        "",
        f"**{len(failures)} test(s) failed:**",
        "",
        "| Test | Class | Message |",
        "|------|-------|---------|"
    ]
    
    for f in failures:
        # Escape pipe characters in message
        message = f["message"].replace("|", "\\|").replace("\n", " ")
        test = f["test"].replace("|", "\\|")
        cls = f["class"].split(".")[-1]  # Short class name
        lines.append(f"| `{test}` | `{cls}` | {message} |")
    
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
