# JUNIT.XSL - Test Report Stylesheet

## Overview

This XSLT stylesheet transforms JUnit XML test results into HTML reports. It is used by the CI/CD workflow to generate test reports without requiring Maven's surefire-report plugin, which can fail in Tycho projects due to target platform resolution issues.

## Features

- **Summary Section**: Shows total tests, failures, errors, skipped, and success rate
- **Package View**: Lists all packages with their test statistics
- **Test Details**: Shows individual test cases with their status (Success, Failure, Error, Skipped)
- **Navigation Buttons**: Fixed buttons to jump between failures/errors
- **Keyboard Shortcuts**:
  - `Ctrl+.` - Jump to next error/failure
  - `Ctrl+,` - Jump to previous error/failure
- **Properties Display**: Click "Properties »" to view test environment properties in a popup

## Usage

### From Command Line

```bash
xsltproc \
  --stringparam TITLE "Test Results - Module Name" \
  .github/JUNIT.XSL \
  path/to/combined-tests.xml \
  > output.html
```

### In GitHub Actions Workflow

The stylesheet is automatically used in `.github/workflows/maven.yml`:

1. Combines individual `TEST-*.xml` files into a single `combined-tests.xml`
2. Transforms to HTML using `xsltproc`
3. Deploys to GitHub Pages

## Input Format

The stylesheet expects a `<testsuites>` root element containing one or more `<testsuite>` elements:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<testsuites>
  <testsuite name="TestClass" tests="5" errors="0" failures="1" skipped="1" 
             time="0.123" timestamp="2024-01-25T10:30:00" hostname="localhost" 
             package="org.example">
    <properties>
      <property name="java.version" value="21"/>
    </properties>
    <testcase name="testSuccess" classname="org.example.TestClass" time="0.001"/>
    <testcase name="testFailure" classname="org.example.TestClass" time="0.010">
      <failure message="Expected 5 but was 3" type="AssertionError">
        Stack trace here...
      </failure>
    </testcase>
    <!-- more testcases -->
  </testsuite>
  <!-- more testsuites -->
</testsuites>
```

## License

This stylesheet is based on Apache Ant's `junit-noframes.xsl` and is licensed under the Apache License 2.0.

Customizations for the Sandbox project include:
- Navigation buttons with keyboard shortcuts
- Custom header with project links
- Simplified JavaScript escaping compatible with libxslt

## Dependencies

- **xsltproc**: XSLT 1.0 processor (libxslt)
  - Pre-installed on Ubuntu GitHub Actions runners
  - Install on other systems: `apt-get install xsltproc` (Debian/Ubuntu)

No Java or Maven dependencies required.

## References

- [Apache Ant junit-noframes.xsl](https://github.com/apache/ant/blob/master/src/etc/junit-noframes.xsl)
- [Eclipse Platform test reports](https://download.eclipse.org/eclipse/downloads/)
- [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
