# Scripts for Sandbox Automation

This directory contains utility scripts used by GitHub Actions workflows in the sandbox project.

## Scripts

### fix-nls.sh

Automatically adds missing `//$NON-NLS-n$` comments to string literals in Java files.

**Purpose:**
- Fixes Eclipse internationalization (i18n) warnings
- Required for proper Eclipse plugin development
- Automatically marks strings that don't need translation

**Usage:**

```bash
# Run from repository root
.github/scripts/fix-nls.sh
```

**What it does:**
- Scans all plugin source directories (`sandbox_*/src/`)
- Skips test modules (directories ending with `_test`)
- Finds Java files with string literals missing NLS comments
- Adds `//$NON-NLS-n$` comments where needed
- Numbers multiple strings on the same line sequentially

**Directories processed:**
- ✅ All `sandbox_*/src/` directories (plugin modules)
- ❌ Excludes `*_test/` (test modules)
- ❌ Excludes `sandbox_test_commons/` (test infrastructure)
- ❌ Excludes `sandbox_web/`, `sandbox_target/`, `sandbox_coverage/`

**Examples:**

```java
// Before:
return "Hello World";

// After:
return "Hello World"; //$NON-NLS-1$

// Multiple strings:
String msg = "First" + "Second";
// After:
String msg = "First" + "Second"; //$NON-NLS-1$ //$NON-NLS-2$
```

**Features:**
- ✅ Preserves existing NLS comments (no duplicates)
- ✅ Skips comment lines
- ✅ Handles multiple strings per line
- ✅ Only processes lines ending with `;`, `)`, or `}`
- ✅ Safe to run multiple times (idempotent)

**GitHub Actions Integration:**

The script is automatically run by the `Fix NLS Comments` workflow (`.github/workflows/fix-nls.yml`):
- Runs on PRs from GitHub Copilot
- Or when PR is labeled with `auto-fix-nls`
- Automatically commits changes back to the PR

**Limitations:**
- Uses simple quote counting (may not handle all edge cases with escape sequences like `\"` or `\\`)
- Only processes lines ending with `;`, `)`, or `}`
- Multi-line string concatenations are **not** fully supported; only the final line is processed if it contains string literals and ends with a terminator
- Does not handle Java 15+ text blocks

### generate_test_report.py

Generates a comprehensive test overview report by scanning all test modules in the repository.

**Features:**
- Scans all `*_test` modules
- Identifies all test methods (`@Test`, `@ParameterizedTest`, `@RepeatedTest`)
- Tracks disabled tests (`@Disabled`) with their reasons
- Generates statistics per plugin and overall
- Outputs both Markdown and JSON formats

**Usage:**

```bash
# Run from repository root
python3 .github/scripts/generate_test_report.py

# Outputs:
# - test-report.md  - Human-readable Markdown report
# - test-report.json - Machine-readable JSON for automation
```

**Generated Report Includes:**
- Overall statistics (total tests, enabled/disabled counts)
- Per-plugin summary table
- Detailed list of all disabled tests with reasons and file locations

### extract_test_failures.py

Extracts failed test details from JUnit XML reports and formats them as Markdown for PR comments.

**Purpose:**
- Makes test failure information directly accessible to GitHub Copilot and other tools
- Provides detailed error messages and stack traces without requiring access to GitHub Check links
- Enables quick debugging by surfacing failures in PR comments

**Features:**
- Recursively scans for JUnit XML reports (`**/target/surefire-reports/TEST-*.xml`)
- Extracts both `<failure>` and `<error>` elements
- Formats output as Markdown with:
  - Summary table showing test name, class (with full name on hover), and error message
  - Collapsible section with complete stack traces
- Truncates long messages (1000 chars) and stack traces (5000 chars) with indicators
- Handles XML parsing errors gracefully with warnings to stderr

**Usage:**

```bash
# Run from repository root
python3 .github/scripts/extract_test_failures.py > test-failures.md

# Output is written to stdout, warnings to stderr
```

**Output Format:**

When tests pass:
```markdown
## ✅ All Tests Passed

<!-- test-failures-comment -->
No test failures detected.
```

When tests fail:
```markdown
## ❌ Failed Tests Details

<!-- test-failures-comment -->

**3 test(s) failed:**

| Test | Class | Message |
|------|-------|---------|
| `testFailure` | <span title="org.example.MyTestClass">`MyTestClass`</span> | Expected <5> but was <3> |

<details>
<summary>📋 Stack Traces (click to expand)</summary>

### org.example.MyTestClass.testFailure

```
java.lang.AssertionError: Expected <5> but was <3>
    at org.junit.Assert.fail(Assert.java:88)
    at org.example.MyTestClass.testFailure(MyTestClass.java:45)
```

</details>
```

**GitHub Actions Integration:**

The script is automatically run by the Maven CI workflow (`.github/workflows/maven.yml`):
- Runs on all pull requests after test execution
- Extracts failures from test reports
- Posts/updates a PR comment with failure details
- Uses unique HTML comment marker (`<!-- test-failures-comment -->`) to identify and update the same comment on subsequent runs

**Security Considerations:**
- Uses `xml.etree.ElementTree` for XML parsing (suitable for trusted CI-generated reports)
- Does not protect against XML entity expansion attacks (XML bombs) or external entity injection
- For untrusted XML sources, consider using the `defusedxml` library instead

**Limitations:**
- Messages are truncated to 1000 characters (with "... (truncated)" indicator)
- Stack traces are truncated to 5000 characters (with "... (truncated)" indicator)
- Only processes JUnit XML format (Maven Surefire/Failsafe output)

## GitHub Actions Integration

The test report is automatically generated by the `Test Report` workflow (`.github/workflows/test-report.yml`):

- **Runs on:** Push to main, pull requests, daily schedule, manual trigger
- **Artifacts:** Reports are uploaded and retained for 90 days
- **PR Comments:** On pull requests, the report is posted as a comment
- **GitHub Summary:** The report is displayed in the workflow run summary

### Viewing Reports

1. **In Pull Requests:** Check the bot comment with the test overview
2. **In GitHub Actions:** Check the "Summary" tab of workflow runs
3. **As Artifacts:** Download from the workflow run artifacts

### Manual Trigger

You can manually trigger the workflow from the Actions tab to generate an up-to-date report.

## Report Format

### Markdown Report (`test-report.md`)

Example:

```markdown
# JUnit Test Overview Report

## Overall Statistics

- **Total Test Modules:** 9
- **Total Test Files:** 25
- **Total Tests:** 257
- **Enabled Tests:** 213 (82%)
- **Disabled Tests:** 44 (17%)

## Test Summary by Plugin

| Plugin | Test Files | Total Tests | Enabled | Disabled | Disabled % |
|--------|------------|-------------|---------|----------|-----------|
| sandbox_encoding_quickfix_test | 3 | 8 | 7 | 1 | 12% |
...

## Disabled Tests Details

### sandbox_encoding_quickfix_test (1 disabled)

- `org.eclipse.jdt.ui.tests.quickfix.Java10.ExplicitEncodingCleanUpTest.testExplicitEncodingParametrizedAggregateUTF8()` - Not Implemented
  - File: `sandbox_encoding_quickfix_test/src/org/eclipse/jdt/ui/tests/quickfix/Java10/ExplicitEncodingCleanUpTest.java:79`
```

### JSON Report (`test-report.json`)

Machine-readable format containing:
- `summary`: Overall statistics
- `plugins`: Per-plugin detailed statistics
- `disabled_tests`: Array of all disabled test details

## Maintenance

### Adding Test Detection Support

The script currently detects:
- `@Test`
- `@ParameterizedTest`
- `@RepeatedTest`

To add support for additional test annotations, modify the `scan_java_file` method in `generate_test_report.py`.

### Modifying Report Format

- **Markdown format:** Edit `generate_markdown_report` method
- **JSON format:** Edit `generate_json_report` method
- **Statistics:** Edit `generate_summary` method

## Notes

- The script uses regex-based parsing (not a full Java parser) for simplicity and speed
- Commented-out `@Disabled` annotations are not counted as disabled
- Disabled test reasons are extracted from `@Disabled("reason")` annotations
- File paths are relative to repository root
