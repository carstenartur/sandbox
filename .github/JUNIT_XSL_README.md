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
  - Improved readability with preserved line breaks
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
- **GitHub Code Links** (optional): When configured with repository information
  - Stacktrace file references become clickable links to GitHub
  - Links point directly to the source code line that caused the failure
  - Opens in new tab for easy investigation

### Navigation
- **Navigation Buttons**: Fixed buttons in top-right corner to jump between failures/errors
- **Keyboard Shortcuts**:
  - `Ctrl+.` - Jump to next error/failure
  - `Ctrl+,` - Jump to previous error/failure
- **Properties Display**: Click "Properties »" to view test environment properties in a styled popup window

### Coverage Integration
- **Coverage Links**: 📊 icon next to each test class name linking to coverage report
  - Automatic test class to source class mapping (removes "Test" suffix)
  - Test module to source module mapping from configuration
  - Opens coverage report in new tab for easy analysis
- **Cross-Report Navigation**: Header and footer links to navigate between test and coverage reports
- **Configurable**: Coverage links can be enabled/disabled via configuration file

## Usage

### Configuration File

The stylesheet can be configured using an external configuration file (`.github/junit-report-config.xml`):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<junit-report-config>
    <project>
        <name>Sandbox</name>
        <repository-url>https://github.com/carstenartur/sandbox</repository-url>
        <github-pages-base>https://carstenartur.github.io/sandbox</github-pages-base>
    </project>
    <paths>
        <tests-base>/tests</tests-base>
        <coverage-base>/coverage</coverage-base>
    </paths>
    <mapping>
        <test-class-suffix>Test</test-class-suffix>
        <module-mapping>
            <map test="sandbox_common_test" source="sandbox_common"/>
            <!-- Add more module mappings -->
        </module-mapping>
    </mapping>
    <ui>
        <show-coverage-links>true</show-coverage-links>
        <show-repository-link>true</show-repository-link>
    </ui>
</junit-report-config>
```

**Configuration Elements:**
- **project**: Project name, repository URL, and GitHub Pages base URL
- **paths**: Base paths for test reports and coverage reports
- **mapping**: Test-to-source module mapping and test class suffix
- **ui**: UI settings for showing coverage and repository links

If the configuration file is not found, the stylesheet uses sensible defaults.

### From Command Line

Basic usage:
```bash
xsltproc \
  --stringparam TITLE "Test Results - Module Name" \
  .github/JUNIT.XSL \
  path/to/combined-tests.xml \
  > output.html
```

With configuration file:
```bash
xsltproc \
  --stringparam TITLE "Test Results - sandbox_common_test" \
  --stringparam CONFIG_FILE "path/to/junit-report-config.xml" \
  .github/JUNIT.XSL \
  path/to/combined-tests.xml \
  > output.html
```

#### Optional: Enable GitHub Code Links

To enable clickable links to source code in stacktraces:

```bash
xsltproc \
  --stringparam TITLE "Test Results - Module Name" \
  --stringparam GITHUB_REPO "owner/repository" \
  --stringparam GITHUB_BRANCH "main" \
  .github/JUNIT.XSL \
  path/to/combined-tests.xml \
  > output.html
```

When `GITHUB_REPO` is provided, stacktrace references like `at org.example.Test.method(Test.java:42)` become clickable links pointing to `https://github.com/owner/repository/blob/main/src/test/java/org/example/Test.java#L42`.

### Coverage Links

When a configuration file is provided and `show-coverage-links` is enabled, the report includes:
- **📊 Coverage Icon**: Next to each test class name, linking to the corresponding source class coverage report
- **Smart Mapping**: Automatically removes test class suffix (e.g., "ExpressionHelperTest" → "ExpressionHelper")
- **JaCoCo Aggregate Structure**: Links point directly to the JaCoCo aggregate report structure without module names (e.g., `/coverage/org/sandbox/...` not `/coverage/sandbox_module/org/sandbox/...`)
- **Filtered Display**: Coverage links only appear for source packages (e.g., `org.sandbox.*`), not for test-only packages (e.g., `org.eclipse.jdt.ui.tests.*`)
- **Navigation**: Header and footer links to coverage reports and test report index

### GitHub Test Source Links

When a repository URL is configured, the report includes:
- **🔗 GitHub Source Icon**: Next to each test class name, linking to the test source code in the GitHub repository
- **Direct Navigation**: Links point directly to the test class file (e.g., `https://github.com/owner/repo/blob/main/module_test/src/org/example/TestClass.java`)
- **Opens in New Tab**: Source links open in a new browser tab for easy code review
- **Contextual Help**: Hover tooltip shows the test class name for confirmation

### In GitHub Actions Workflow

The stylesheet is automatically used in `.github/workflows/maven.yml`:

1. Combines individual `TEST-*.xml` files into a single `combined-tests.xml`
2. Copies configuration file to each module's target directory
3. Transforms to HTML using `xsltproc` with CONFIG_FILE parameter
4. Deploys to GitHub Pages

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
