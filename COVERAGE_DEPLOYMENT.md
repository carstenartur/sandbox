# JaCoCo Coverage Report Deployment to GitHub Pages

This document describes how the JaCoCo code coverage reports are automatically generated and deployed to GitHub Pages.

## Overview

The project uses JaCoCo Maven Plugin to generate code coverage reports during the build process. These reports are automatically deployed to GitHub Pages whenever code is pushed to the `main` branch.

## Configuration

### Maven Configuration

The project has a `jacoco` profile in the root `pom.xml` that:
- Activates the JaCoCo Maven Plugin
- Includes the `sandbox_coverage` module for aggregated reporting
- Generates coverage reports during the `verify` phase

The `sandbox_coverage` module aggregates coverage from all test modules and generates a comprehensive HTML report.

### GitHub Actions Workflow

The `.github/workflows/maven.yml` workflow:
1. Builds the project with Maven using the `-Pjacoco` profile
2. Generates the aggregated coverage report in `sandbox_coverage/target/site/jacoco-aggregate/`
3. Deploys the report to GitHub Pages under the `coverage/` directory (only on pushes to `main`)

**Important**: The workflow uses `keep_files: true` to preserve other content on the `gh-pages` branch (like releases and snapshots). The `destination_dir: coverage` ensures only the coverage directory is updated.

## GitHub Pages Setup

### Manual Configuration Required

To enable GitHub Pages deployment, the following steps must be performed in the repository settings:

1. Go to repository **Settings** → **Pages**
2. Under "Source", select **Deploy from a branch**
3. Select the `gh-pages` branch
4. Select the `/ (root)` folder
5. Click **Save**

### Accessing the Coverage Report

Once configured and deployed, the coverage report will be available at:
```
https://carstenartur.github.io/sandbox/coverage/
```

## Build Commands

### Local Build with Coverage

To generate coverage reports locally:

```bash
# Set Java 21 (required)
export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

# Build with coverage
xvfb-run --auto-servernum mvn clean verify -Pjacoco

# View the aggregated report
# Open: sandbox_coverage/target/site/jacoco-aggregate/index.html
```

### CI/CD Build

The GitHub Actions workflow automatically:
- Runs on every push to `main` branch
- Runs on pull requests to `main` branch
- Deploys coverage reports only on pushes to `main`

## Coverage Report Structure

The aggregated report includes coverage from all test modules:
- `sandbox_common_test`
- `sandbox_encoding_quickfix_test`
- `sandbox_platform_helper_test`
- `sandbox_functional_converter_test`
- `sandbox_tools_test`
- `sandbox_jface_cleanup_test`
- `sandbox_junit_cleanup_test`
- `sandbox_xml_cleanup_test`
- `sandbox_method_reuse_test`
- `sandbox_triggerpattern_test`
- `sandbox_usage_view_test`

## Troubleshooting

### Coverage Report Not Generated

If the coverage report is not generated:
1. Ensure the `-Pjacoco` profile is activated during the build
2. Check that tests are running successfully
3. Verify the `sandbox_coverage` module is included in the build

### GitHub Pages Deployment Fails

If deployment to GitHub Pages fails:
1. Verify repository settings have GitHub Pages enabled
2. Check that the workflow has necessary permissions (`pages: write`, `id-token: write`)
3. Ensure the `gh-pages` branch exists (it will be created automatically on first deployment)
4. Check GitHub Actions logs for detailed error messages

### Coverage Report Empty or Incomplete

If the coverage report is empty or incomplete:
1. Ensure tests are running successfully (`mvn test`)
2. Verify JaCoCo agent is attached during test execution
3. Check that all test modules are listed as dependencies in `sandbox_coverage/pom.xml`

## Optional: Coverage Badge

To add a coverage badge to the README, you can use a service like [Codecov](https://codecov.io/) or [Coveralls](https://coveralls.io/), or create a custom badge using the coverage percentage from the reports.

Example badge (placeholder):
```markdown
![Coverage](https://img.shields.io/badge/coverage-XX%25-brightgreen)
```

## References

- [JaCoCo Maven Plugin Documentation](https://www.jacoco.org/jacoco/trunk/doc/maven.html)
- [GitHub Pages Documentation](https://docs.github.com/en/pages)
- [peaceiris/actions-gh-pages](https://github.com/peaceiris/actions-gh-pages)
