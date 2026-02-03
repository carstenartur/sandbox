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
                        
                        # If message doesn't have newlines but stacktrace does,
                        # prefer parsing from stacktrace for better formatting
                        parse_source = truncated_stacktrace if '\n' in stacktrace else truncated_message
                        
                        failures.append({
                            "class": class_name,
                            "test": test_name,
                            "message": truncated_message,
                            "stacktrace": truncated_stacktrace,
                            "parse_source": parse_source
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
                        
                        # If message doesn't have newlines but stacktrace does,
                        # prefer parsing from stacktrace for better formatting
                        parse_source = truncated_stacktrace if '\n' in stacktrace else truncated_message
                        
                        failures.append({
                            "class": class_name,
                            "test": test_name,
                            "message": truncated_message,
                            "stacktrace": truncated_stacktrace,
                            "parse_source": parse_source
                        })
        except ET.ParseError as e:
            print(f"Warning: Could not parse {xml_path}: {e}", file=sys.stderr)
        except Exception as e:
            print(f"Warning: Error processing {xml_path}: {e}", file=sys.stderr)
    
    return failures

def parse_assertion_error(message):
    """Parse expected/actual from assertion error message.
    
    Handles AssertionFailedError messages like:
    - "expected: <...> but was: <...>"
    - "Expected: ... Actual: ..."
    
    Returns:
        dict with 'expected' and 'actual' keys, or None if not parseable
    """
    import re
    
    # Pattern 1: "expected: <...> but was: <...>"
    match = re.search(r'expected:\s*<(.*)>\s*but was:\s*<(.*)>', message, re.DOTALL | re.IGNORECASE)
    if match:
        return {
            'expected': match.group(1).strip(),
            'actual': match.group(2).strip()
        }
    
    # Pattern 2: "Expected: ... Actual: ..."
    match = re.search(r'Expected:\s*(.*?)\s*Actual:\s*(.*)', message, re.DOTALL | re.IGNORECASE)
    if match:
        return {
            'expected': match.group(1).strip(),
            'actual': match.group(2).strip()
        }
    
    return None

def format_as_markdown(failures):
    """Format failures as Markdown for PR comment."""
    if not failures:
        return "## ✅ All Tests Passed\n\n<!-- test-failures-comment -->\nNo test failures detected."
    
    lines = [
        "## ❌ Failed Tests Details",
        "",
        "<!-- test-failures-comment -->",
        "",
        f"**{len(failures)} test(s) failed**",
        ""
    ]
    
    for i, f in enumerate(failures, 1):
        short_class = f["class"].split(".")[-1]
        
        lines.extend([
            "---",
            "",
            f"### {i}. {short_class}.{f['test']}",
            ""
        ])
        
        # Try to parse expected/actual from parse_source (prefers stacktrace with newlines)
        parse_source = f.get("parse_source", f["message"])
        parsed = parse_assertion_error(parse_source)
        if parsed:
            lines.extend([
                "**Expected:**",
                "```java",
                parsed['expected'],
                "```",
                "",
                "**Actual:**",
                "```java",
                parsed['actual'],
                "```",
                ""
            ])
        else:
            # Fallback: show message as-is in code block
            lines.extend([
                "**Message:**",
                "```",
                f["message"],
                "```",
                ""
            ])
        
        # Stack trace in collapsible section
        lines.extend([
            "<details>",
            "<summary>📋 Full Stack Trace</summary>",
            "",
            "```",
            f["stacktrace"],
            "```",
            "",
            "</details>",
            ""
        ])
    
    return "\n".join(lines)

def main():
    failures = extract_failures(".")
    markdown = format_as_markdown(failures)
    print(markdown)

if __name__ == "__main__":
    main()
