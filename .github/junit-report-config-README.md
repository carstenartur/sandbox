# JUnit Report Configuration

This file configures the JUnit test report generation, specifically for linking test reports with coverage reports.

## Purpose

The configuration file enables:
1. **Cross-Report Navigation**: Links from test reports to coverage reports
2. **Smart Class Mapping**: Automatically maps test classes to source classes
3. **Module Mapping**: Maps test modules to source modules for accurate links
4. **Customizable URLs**: Configure GitHub Pages base URL and paths
5. **UI Options**: Control visibility of various UI elements

## Configuration Structure

### Project Information

```xml
<project>
    <name>Sandbox</name>
    <repository-url>https://github.com/carstenartur/sandbox</repository-url>
    <github-pages-base>https://carstenartur.github.io/sandbox</github-pages-base>
</project>
```

- **name**: Project name shown in report footer
- **repository-url**: Link to GitHub repository
- **github-pages-base**: Base URL for GitHub Pages (without trailing slash)

### Paths

```xml
<paths>
    <tests-base>/tests</tests-base>
    <coverage-base>/coverage</coverage-base>
</paths>
```

- **tests-base**: Path to test reports on GitHub Pages (relative to github-pages-base)
- **coverage-base**: Path to coverage reports on GitHub Pages (relative to github-pages-base)

### Mapping

```xml
<mapping>
    <test-class-suffix>Test</test-class-suffix>
    <module-mapping>
        <map test="sandbox_common_test" source="sandbox_common"/>
        <map test="sandbox_encoding_quickfix_test" source="sandbox_encoding_quickfix"/>
        <!-- Add more mappings as needed -->
    </module-mapping>
</mapping>
```

- **test-class-suffix**: Suffix to remove from test class names (e.g., "ExpressionHelperTest" → "ExpressionHelper")
- **module-mapping**: Maps test modules to source modules for coverage link generation

#### How Module Mapping Works

When generating a coverage link for a test class:
1. Extract test module name from the TITLE parameter (e.g., "sandbox_common_test")
2. Look up source module in module-mapping (e.g., "sandbox_common")
3. Remove test-class-suffix from test class name (e.g., "ExpressionHelperTest" → "ExpressionHelper")
4. Generate coverage URL: `{github-pages-base}{coverage-base}/{source-module}/{package-path}/{ClassName}.html`

**Example:**
- Test class: `org.sandbox.jdt.internal.corext.util.ExpressionHelperTest`
- Test module: `sandbox_common_test`
- Source module: `sandbox_common`
- Coverage link: `https://carstenartur.github.io/sandbox/coverage/sandbox_common/org/sandbox/jdt/internal/corext/util/ExpressionHelper.html`

### UI Settings

```xml
<ui>
    <show-coverage-links>true</show-coverage-links>
    <show-repository-link>true</show-repository-link>
    <default-theme>auto</default-theme>
</ui>
```

- **show-coverage-links**: Show/hide coverage links (📊 icon) next to test class names
- **show-repository-link**: Show/hide GitHub repository link in header/footer
- **default-theme**: Theme preference (auto, light, dark) - currently auto only

## Fallback Behavior

If the configuration file is not found or cannot be loaded, the stylesheet uses these defaults:

- **project-name**: "Sandbox"
- **repository-url**: "https://github.com/carstenartur/sandbox"
- **github-pages-base**: "https://carstenartur.github.io/sandbox"
- **tests-base**: "/tests"
- **coverage-base**: "/coverage"
- **test-class-suffix**: "Test"
- **show-coverage-links**: true
- **show-repository-link**: true

Module mapping uses a simple convention: remove `_test` suffix from test module name to get source module name.

## Usage in Workflow

The configuration file is used in `.github/workflows/maven.yml`:

```bash
# Copy config to module target directory
cp .github/junit-report-config.xml "$module/target/site/"

# Transform with config
xsltproc \
  --stringparam TITLE "Test Results - $module" \
  --stringparam CONFIG_FILE "$module/target/site/junit-report-config.xml" \
  .github/JUNIT.XSL \
  "$module/target/site/combined-tests.xml" \
  > "$module/target/site/surefire-report.html"
```

## Adding New Test Modules

When adding a new test module:

1. Add a mapping entry in `<module-mapping>`:
   ```xml
   <map test="new_module_test" source="new_module"/>
   ```

2. Ensure the source module has coverage reports deployed to:
   ```
   {github-pages-base}{coverage-base}/{source-module}/
   ```

3. Coverage links will automatically work for all test classes in the new module

## Customization

You can customize the configuration for different environments:

- **Local Development**: Use localhost URLs for testing
- **Staging Environment**: Use staging GitHub Pages URL
- **Production**: Use production URLs (as configured)

Create environment-specific configuration files if needed and pass the appropriate file path using the `CONFIG_FILE` parameter.
