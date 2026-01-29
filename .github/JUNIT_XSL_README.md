# JUNIT.XSL - Test Report Stylesheet

## Overview

This XSLT stylesheet transforms JUnit XML test results into modern, interactive HTML reports. It is used by the CI/CD workflow to generate test reports without requiring Maven's surefire-report plugin, which can fail in Tycho projects due to target platform resolution issues.

## Features

### Modern Design
- **HTML5**: Updated to modern HTML5 standard
- **Responsive Design**: Works seamlessly on desktop, tablet, and mobile devices
- **Modern Typography**: Clean, readable fonts with proper hierarchy using system-ui font stack
- **Beautiful UI**: Gradient headers, rounded corners, shadows, and smooth animations
- **Dark Mode**: Automatic dark theme support based on system preferences (`prefers-color-scheme: dark`)

### Visual Progress Bar
- **Success Rate Visualization**: Animated progress bar showing test results at a glance
- **Color-Coded Segments**: Green for success, red for errors/failures, orange for skipped tests
- **Percentage Display**: Shows exact percentages within each segment

### Interactive Features
- **Collapsible Stacktraces**: Error details are hidden by default, click "Show Details" to expand
  - Clean monospace formatting with dark background
  - Syntax highlighting for better readability
- **Sortable Tables**: Click any column header to sort ascending/descending
  - Visual sort indicators (▲/▼)
  - Works with text, numbers, and dates
- **Filter Buttons**: Quickly focus on specific test results
  - 📊 Show All - Display all tests
  - ❌ Errors/Failures Only - Show only failed tests
  - ⏭ Skipped Only - Show only skipped tests
  - ✅ Success Only - Show only successful tests
- **Permalinks**: Each test has a unique link icon (🔗)
  - Click to copy direct link to clipboard
  - Hash-based navigation to specific tests
  - Target highlighting when navigating via permalink

### Navigation
- **Navigation Buttons**: Fixed buttons in top-right corner to jump between failures/errors
- **Keyboard Shortcuts**:
  - `Ctrl+.` - Jump to next error/failure
  - `Ctrl+,` - Jump to previous error/failure
- **Properties Display**: Click "Properties »" to view test environment properties in a styled popup window

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
