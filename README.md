# Sandbox Project

A collection of experimental Eclipse JDT (Java Development Tools) cleanup plugins and tools. This repository demonstrates how to build custom JDT cleanups, quick fixes, and related tooling for Eclipse-based Java development.

**Main Technologies:** Eclipse JDT, Java 21, Maven/Tycho 5.0.2

**Status:** Work in Progress – All plugins are experimental and intended for testing purposes.

---

## 🔗 CI Status & Resources

[![Java CI with Maven](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml/badge.svg)](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml)  
[![CodeQL](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml/badge.svg)](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml)
[![Eclipse Marketplace](https://img.shields.io/badge/Eclipse%20Marketplace-Sandbox-blue)](https://marketplace.eclipse.org/content/sandbox)

📊 **[Test Results](https://carstenartur.github.io/sandbox/tests/)** | 📈 **[Code Coverage](https://carstenartur.github.io/sandbox/coverage/)** | ⚡ **[Performance Charts](https://carstenartur.github.io/sandbox/dev/bench/)**

---

## Overview

This project provides:

- **Custom JDT Cleanup Plugins**: Automated code transformations for encoding, JUnit migration, functional programming patterns, and more
- **Eclipse Product Build**: A complete Eclipse product with bundled features
- **P2 Update Site**: Installable plugins via Eclipse update mechanism
- **Test Infrastructure**: JUnit 5-based tests for all cleanup implementations
- **GitHub Actions Integration**: Automated code cleanup for pull requests ([See GITHUB_ACTIONS.md](GITHUB_ACTIONS.md))

All plugins are work-in-progress and intended for experimentation and learning.

## 🚀 Installation

### Update Site URLs

Add one of the following update sites to your Eclipse installation:

#### Stable Releases (Recommended)
```
https://carstenartur.github.io/sandbox/releases/
```
Use this for stable, tested versions suitable for production use.

#### Latest Snapshot (Development)
```
https://carstenartur.github.io/sandbox/snapshots/latest/
```
Use this to test the latest features. Updated automatically on every commit to `main`. May be unstable.

### Installation Steps

1. Open Eclipse IDE
2. Go to **Help** → **Install New Software...**
3. Click **Add...** button
4. Enter:
   - **Name**: `Sandbox` (or any name you prefer)
   - **Location**: One of the update site URLs above
5. Select the features you want to install from the available list
6. Click **Next** and follow the installation wizard
7. Restart Eclipse when prompted

> **⚠️ Warning**: These plugins are experimental. Test them in a development environment before using in production.

## 📦 Release Process

The Sandbox project uses an **automated release workflow**:

1. Navigate to **Actions** → **Release Workflow** → **Run workflow**
2. Enter the release version (e.g., `1.2.2`)
3. Enter the next SNAPSHOT version (e.g., `1.2.3-SNAPSHOT`)
4. Click **Run workflow**

The workflow automatically:
- Updates all version files using `tycho-versions-plugin` (except `sandbox-functional-converter-core`)
- Builds and verifies the release
- Creates git tag and maintenance branch
- Deploys to GitHub Pages
- Generates release notes from closed issues
- Creates GitHub release
- Bumps to next SNAPSHOT version

The new release will be available at `https://carstenartur.github.io/sandbox/releases/X.Y.Z/` within a few minutes.

📖 **Detailed Release Documentation**: [GitHub Workflows README](.github/workflows/README.md#detailed-release-process)

## Table of Contents

- [🔗 CI Status & Resources](#-ci-status--resources)
- [Overview](#overview)
- [🚀 Installation](#-installation)
- [📦 Release Process](#-release-process)
- [GitHub Actions Integration](#github-actions-integration)
- [Build Instructions](#build-instructions)
  - [Troubleshooting](#troubleshooting)
- [Eclipse Version Configuration](#eclipse-version-configuration)
- [Quickstart](#quickstart)
- [What's Included](#whats-included)
- [Projects](#projects)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)
  - [Eclipse Public License 2.0](#eclipse-public-license-20)

## GitHub Actions Integration

This repository includes a **Docker-based GitHub Action** for automated code cleanup on pull requests. The action uses the sandbox cleanup application to apply Eclipse JDT cleanups directly in your GitHub workflows.

### Quick Start

- **Automatic PR Cleanup**: Already configured! Opens/updates to PRs with Java files trigger cleanup automatically
- **Manual Cleanup**: Go to Actions → Manual Cleanup → Run workflow
- **Custom Integration**: Use `./.github/actions/cleanup-action` in your workflows

### Features

✅ Automated cleanup on pull requests  
✅ Configurable profiles (minimal/standard/aggressive)  
✅ All sandbox + Eclipse JDT cleanups included  
✅ Auto-commit changes to PR branch  
✅ Manual trigger with customizable options  

**[📖 Full Documentation](GITHUB_ACTIONS.md)** | **[Workflows Guide](.github/workflows/README.md)** | **[Action Details](.github/actions/cleanup-action/README.md)**

## Build Instructions

### Prerequisites

**IMPORTANT**: This project (main branch, targeting Eclipse 2025-12) requires **Java 21** or later.

The project uses Tycho 5.0.2 which requires Java 21. Building with Java 17 or earlier will fail with:
```
UnsupportedClassVersionError: ... has been compiled by a more recent version of the Java Runtime (class file version 65.0)
```

Verify your Java version:
```bash
java -version  # Should show Java 21 or later
```

### Building

#### Build Profiles

The project supports Maven profiles to optimize build speed:

| Profile | Modules Built | Use Case |
|---------|---------------|----------|
| `dev` (default) | All bundles, features, tests | Fast local development |
| `product` | + Eclipse Product (`sandbox_product`) | Building distributable product |
| `repo` | + P2 Update Site (`sandbox_updatesite`) | Building update site |
| `jacoco` | + Coverage reports | CI/Coverage builds |
| `reports` | + HTML test reports | CI/Test report builds |

#### Build Commands

| Command | Description |
|---------|-------------|
| `mvn -T 1C verify` | Quick dev build (fastest) |
| `mvn -Pproduct -T 1C verify` | Build with Eclipse product |
| `mvn -Prepo -T 1C verify` | Build with P2 update site |
| `mvn -Pproduct,repo -T 1C verify` | Full release build |
| `mvn -Pjacoco,product,repo -T 1C verify` | Full CI build with coverage |
| `mvn -T 1C -DskipTests verify` | Skip tests for local iteration |

#### Using Make (Convenience)

A Makefile is provided for easier build commands:

```bash
make dev         # Fast development build with tests
make dev-notests # Fast development build without tests
make product     # Build with product (requires xvfb for tests)
make repo        # Build with repository (requires xvfb for tests)
make release     # Full release build with coverage (requires xvfb for tests)
make test        # Run tests with coverage (requires xvfb)
make clean       # Clean all build artifacts
make help        # Show all available targets
```

#### Build Flags

- `-T 1C`: Enables parallel builds with 1 thread per CPU core (faster builds)
- `-DskipTests`: Skips test execution (faster iteration)
- `-Pjacoco`: Enables JaCoCo code coverage
- `-Pproduct`: Includes Eclipse product build
- `-Prepo`: Includes p2 repository build

#### Understanding the Profiles

- **Default (no profiles)**: Fast development build - bundles, features, and tests only
- **`product`**: Adds Eclipse product materialization (heavy step, takes time)
- **`repo`**: Adds p2 update site repository assembly (heavy step, takes time)
- **`jacoco`**: Adds code coverage reporting (includes `sandbox_coverage` module)
- **`reports`**: Adds HTML test report generation (use without `-T 1C` to avoid thread-safety warning)
- **`web`**: Adds WAR file with update site (requires `-Dinclude=web` property, also builds `sandbox_product`)

**Backward Compatibility**: The command `mvn -Pproduct,repo verify` produces the same result as the previous full build behavior.

### Troubleshooting

#### Build fails with `UnsupportedClassVersionError` or `TypeNotPresentException`

This error occurs when building with Java 17 or earlier:

```
TypeNotPresentException: Type P2ArtifactRepositoryLayout not present
...class file version 65.0, this version only recognizes class file versions up to 61.0
```

**Solution**: Upgrade to Java 21 or later. Verify with `java -version`.

#### Build fails with `Unable to provision` errors

This usually indicates a Java version mismatch. Check that:
1. `JAVA_HOME` is set to Java 21+
2. `java -version` shows Java 21+
3. Maven is using the correct Java version: `mvn -version`

---

## Eclipse Version Configuration

> **Note for Maintainers/Contributors**: This section contains technical details about Eclipse version migration. Regular users installing from the update site can skip this section.

The Eclipse version (SimRel release) used by this project is **not centrally configured**. When updating to a new Eclipse release, you must update the version reference in **multiple files** throughout the repository.

### Files to Update

When migrating to a new Eclipse version, update the following files:

1. **`pom.xml`** (root)
   - Repository URLs in the `<repositories>` section
   - Example: `https://download.eclipse.org/releases/2025-12/`
   - Also update Orbit repository URL: `https://download.eclipse.org/tools/orbit/simrel/orbit-aggregation/2025-12/`

2. **`sandbox_target/eclipse.target`**
   - Primary Eclipse release repository URL in first `<location>` block
   - Example: `<repository location="https://download.eclipse.org/releases/2025-12/"/>`
   - Also update Orbit repository URL

3. **`sandbox_product/category.xml`**
   - Repository reference location
   - Example: `<repository-reference location="https://download.eclipse.org/releases/2025-12/" .../>`

4. **`sandbox_product/sandbox.product`**
   - Repository locations in `<repositories>` section
   - Example: `<repository location="https://download.eclipse.org/releases/2025-12/" .../>`

5. **`sandbox_oomph/sandbox.setup`**
   - P2 repository URL in the version-specific `<setupTask>` block
   - Example: `<repository url="https://download.eclipse.org/releases/2025-12"/>`

### Version Consistency Guidelines

- **Use HTTPS**: All Eclipse download URLs should use `https://` (not `http://`)
- **Use explicit versions**: Prefer explicit version URLs (e.g., `2025-12`) over `latest` for reproducible builds
- **Keep versions aligned**: All files should reference the same Eclipse SimRel version
- **Git URLs**: Use HTTPS for git clone URLs (e.g., `https://github.com/...`, not `git://`)
- **Main branch**: All Oomph setup files should reference the `main` branch, not `master`

### Current Configuration

- **Eclipse Version**: 2025-12
- **Java Version**: 21
- **Tycho Version**: 5.0.2
- **Default Branch**: `main`

---

## Quickstart

### Using the Eclipse Product

After building the project, you can run the Eclipse product with the bundled cleanup plugins:

```bash
# Navigate to the product directory
cd sandbox_product/target/products/org.sandbox.product/

# Launch Eclipse
./eclipse
```

### Using Cleanup Plugins via Command Line

You can apply cleanup transformations using the Eclipse JDT formatter application pattern:

```bash
eclipse -nosplash -consolelog -debug \
  -application org.eclipse.jdt.core.JavaCodeFormatter \
  -verbose -config MyCleanupSettings.ini MyClassToCleanup.java
```

> **Note**: Replace `MyCleanupSettings.ini` with your cleanup configuration file and `MyClassToCleanup.java` with the Java file you want to process.

### Installing as Eclipse Plugins

You can install the cleanup plugins into your existing Eclipse installation using the P2 update site.

**See the [Installation](#-installation) section above for detailed instructions and update site URLs.**

The update sites provide:
- **Stable Releases**: `https://carstenartur.github.io/sandbox/releases/` - Tested, stable versions
- **Latest Snapshots**: `https://carstenartur.github.io/sandbox/snapshots/latest/` - Latest development builds

> **⚠️ Warning**: These plugins are experimental. Test them in a development environment before using in production.

---

## What's Included

### Java Version by Branch

| Branch          | Java Version | Tycho Version |
|-----------------|--------------|---------------|
| `main` (2025-12)| Java 21      | 5.0.2         |
| `2024-06`+      | Java 21      | 5.0.x         |
| `2022-12`+      | Java 17      | 4.x           |
| Up to `2022-06` | Java 11      | 3.x           |

**Note**: Tycho 5.x requires Java 21+ at build time. Attempting to build with Java 17 will result in `UnsupportedClassVersionError`.

### Topics Covered

- Building for different Eclipse versions via GitHub Actions
- Creating custom JDT cleanups
- Setting up the SpotBugs Maven plugin to fail the build on issues
- Writing JUnit 5-based tests for JDT cleanups
- Configuring JaCoCo for test coverage
- Building an Eclipse product including new features
- Automatically building a WAR file including a P2 update site

---

## Projects

> All projects are considered work in progress unless otherwise noted.

### 1. `sandbox_cleanup_application`

Placeholder for a CLI-based cleanup application, similar to the Java code formatting tool:

```bash
eclipse -nosplash -consolelog -debug -application org.eclipse.jdt.core.JavaCodeFormatter -verbose -config MyCodingStandards.ini MyClassToBeFormatted.java
```

See: https://bugs.eclipse.org/bugs/show_bug.cgi?id=75333

### 2. `sandbox_encoding_quickfix`

Replaces platform-dependent or implicit encoding usage with explicit, safe alternatives using `StandardCharsets.UTF_8` or equivalent constants. Improves code portability and prevents encoding-related bugs across different platforms. Supports three cleanup strategies with Java version-aware transformations for FileReader, FileWriter, Files methods, Scanner, PrintWriter, and more.

📖 **Full Documentation**: [Plugin README](sandbox_encoding_quickfix/README.md) | [Architecture](sandbox_encoding_quickfix/ARCHITECTURE.md) | [TODO](sandbox_encoding_quickfix/TODO.md)

### 3. `sandbox_extra_search`

Experimental search tool for identifying critical classes when upgrading Eclipse or Java versions.

### 4. `sandbox_usage_view`

Provides a table view of code objects, sorted by name, to detect inconsistent naming that could confuse developers.

### 5. `sandbox_platform_helper`

Simplifies Eclipse Platform `Status` object creation by replacing verbose `new Status(...)` constructor calls with cleaner factory methods (Java 11+ / Eclipse 4.20+) or StatusHelper pattern (Java 8). Reduces boilerplate and provides more readable code through automatic selection between StatusHelper or factory methods based on Java version.

📖 **Full Documentation**: [Plugin README](sandbox_platform_helper/README.md) | [Architecture](sandbox_platform_helper/ARCHITECTURE.md) | [TODO](sandbox_platform_helper/TODO.md)

### 6. `sandbox_tools`

**While-to-For** loop converter — already merged into Eclipse JDT.

### 7. `sandbox_jface_cleanup`

Automates migration from deprecated `SubProgressMonitor` to modern `SubMonitor` API. Transforms `beginTask()` + `SubProgressMonitor` to `SubMonitor.convert()` + `split()` with automatic handling of style flags, multiple monitor instances, and variable name collision resolution. The cleanup is idempotent and safe to run multiple times.

📖 **Full Documentation**: [Plugin README](sandbox_jface_cleanup/README.md) | [Architecture](sandbox_jface_cleanup/ARCHITECTURE.md) | [TODO](sandbox_jface_cleanup/TODO.md)

### 8. `sandbox_functional_converter`

Transforms imperative Java loops into functional Java 8 Stream equivalents (`forEach`, `map`, `filter`, `reduce`, `anyMatch`, `allMatch`, etc.). Supports 25+ tested transformation patterns including max/min reductions, nested filters, and compound operations. Maintains semantic safety by excluding complex patterns with labeled breaks, throws, or multiple mutable accumulators.

📖 **Full Documentation**: [Plugin README](sandbox_functional_converter/README.md) | [Architecture](sandbox_functional_converter/ARCHITECTURE.md) | [TODO](sandbox_functional_converter/TODO.md)

### 9. `sandbox_junit_cleanup`

Automates migration of legacy tests from JUnit 3 and JUnit 4 to JUnit 5 (Jupiter). Transforms test classes, methods, annotations, assertions, and lifecycle hooks to use the modern JUnit 5 API. Handles removing `extends TestCase`, converting naming conventions to annotations, assertion parameter reordering, rule migration, and test suite conversion.

📖 **Full Documentation**: [Plugin README](sandbox_junit_cleanup/README.md) | [Architecture](sandbox_junit_cleanup/ARCHITECTURE.md) | [TODO](sandbox_junit_cleanup/TODO.md) | [Testing Guide](sandbox_junit_cleanup_test/TESTING.md)

---
### 10. `sandbox_method_reuse`

Identifies opportunities to reuse existing methods instead of duplicating logic. Uses token-based and AST-based analysis to find code duplication, suggests method calls to replace repeated patterns, and promotes DRY principles. Currently under development with initial focus on method similarity detection and Eclipse cleanup integration.

📖 **Full Documentation**: [Plugin README](sandbox_method_reuse/README.md) | [Architecture](sandbox_method_reuse/ARCHITECTURE.md) | [TODO](sandbox_method_reuse/TODO.md)

---
### 11. `sandbox_xml_cleanup`

Optimizes Eclipse PDE XML files (plugin.xml, feature.xml, etc.) by reducing whitespace and optionally converting leading spaces to tabs. Uses secure XSLT transformation, normalizes excessive empty lines, and only processes PDE-relevant files in project root, OSGI-INF, or META-INF locations. Idempotent and preserves semantic integrity.

📖 **Full Documentation**: [Plugin README](sandbox_xml_cleanup/README.md) | [Architecture](sandbox_xml_cleanup/ARCHITECTURE.md) | [TODO](sandbox_xml_cleanup/TODO.md)

---
### 12. `sandbox_css_cleanup`

Eclipse plugin for CSS validation and formatting using Prettier and Stylelint. Provides automatic formatting, linting, right-click menu integration for .css, .scss, and .less files, and a preferences page for configuration with graceful fallback when npm tools are not installed.

📖 **Full Documentation**: [Plugin README](sandbox_css_cleanup/README.md) | [Architecture](sandbox_css_cleanup/ARCHITECTURE.md) | [TODO](sandbox_css_cleanup/TODO.md)

---
### 13. `sandbox_triggerpattern`

Provides a powerful pattern matching engine for code transformations in Eclipse. Allows defining code patterns using simple syntax with placeholder support (`$x` for any expression), annotation-based hints using `@TriggerPattern` and `@Hint`, and automatic integration with Eclipse Quick Assist for creating custom hints and quick fixes with minimal boilerplate.

📖 **Full Documentation**: [Plugin README](sandbox_triggerpattern/README.md) | [Architecture](sandbox_triggerpattern/ARCHITECTURE.md) | [TODO](sandbox_triggerpattern/TODO.md)

---
### 14. `sandbox_common`

Provides shared utilities, constants, and base classes used across all sandbox cleanup plugins. Serves as the foundation for the entire sandbox ecosystem with AST manipulation utilities, central cleanup constants repository (`MYCleanUpConstants`), reusable base classes, and Eclipse JDT compatibility structure for easy porting.

📖 **Full Documentation**: [Plugin README](sandbox_common/README.md) | [Architecture](sandbox_common/ARCHITECTURE.md) | [TODO](sandbox_common/TODO.md)

---
### 15. `sandbox_oomph`

Provides Eclipse Oomph setup configurations for automated workspace configuration. Enables one-click setup with pre-configured Eclipse settings, automatic installation of required plugins, Git repository cloning and branch setup, and seamless integration with Eclipse Installer.

📖 **Full Documentation**: [Plugin README](sandbox_oomph/README.md) | [Architecture](sandbox_oomph/ARCHITECTURE.md) | [TODO](sandbox_oomph/TODO.md)

---

## Documentation

This repository contains extensive documentation organized at multiple levels to help you understand, use, and contribute to the project.

### 📚 Documentation Index

#### Getting Started
- **[README.md](README.md)** (this file) - Project overview, build instructions, and plugin descriptions
- **[Build Instructions](#build-instructions)** - How to build the project with Maven/Tycho
- **[Quickstart](#quickstart)** - Quick introduction to using the plugins
- **[Installation](#installation)** - How to install plugins in Eclipse

#### Plugin-Specific Documentation

Each plugin has dedicated documentation in its module directory:

| Plugin | README | Architecture | TODO | Test Docs |
|--------|--------|--------------|------|-----------|
| [Cleanup Application](sandbox_cleanup_application) | [README.md](sandbox_cleanup_application/README.md) | [ARCHITECTURE.md](sandbox_cleanup_application/ARCHITECTURE.md) | [TODO.md](sandbox_cleanup_application/TODO.md) | - |
| [Common Infrastructure](sandbox_common) | [README.md](sandbox_common/README.md) | [ARCHITECTURE.md](sandbox_common/ARCHITECTURE.md) | [TODO.md](sandbox_common/TODO.md) | [TESTING.md](sandbox_common_test/TESTING.md) |
| [Coverage](sandbox_coverage) | [README.md](sandbox_coverage/README.md) | [ARCHITECTURE.md](sandbox_coverage/ARCHITECTURE.md) | [TODO.md](sandbox_coverage/TODO.md) | - |
| [Encoding Quickfix](sandbox_encoding_quickfix) | [README.md](sandbox_encoding_quickfix/README.md) | [ARCHITECTURE.md](sandbox_encoding_quickfix/ARCHITECTURE.md) | [TODO.md](sandbox_encoding_quickfix/TODO.md) | - |
| [Extra Search](sandbox_extra_search) | [README.md](sandbox_extra_search/README.md) | [ARCHITECTURE.md](sandbox_extra_search/ARCHITECTURE.md) | [TODO.md](sandbox_extra_search/TODO.md) | - |
| [Functional Converter](sandbox_functional_converter) | [README.md](sandbox_functional_converter/README.md) | [ARCHITECTURE.md](sandbox_functional_converter/ARCHITECTURE.md) | [TODO.md](sandbox_functional_converter/TODO.md) | - |
| [JFace Cleanup](sandbox_jface_cleanup) | [README.md](sandbox_jface_cleanup/README.md) | [ARCHITECTURE.md](sandbox_jface_cleanup/ARCHITECTURE.md) | [TODO.md](sandbox_jface_cleanup/TODO.md) | - |
| [JUnit Cleanup](sandbox_junit_cleanup) | [README.md](sandbox_junit_cleanup/README.md) | [ARCHITECTURE.md](sandbox_junit_cleanup/ARCHITECTURE.md) | [TODO.md](sandbox_junit_cleanup/TODO.md) | [TESTING.md](sandbox_junit_cleanup_test/TESTING.md) |
| [Method Reuse](sandbox_method_reuse) | [README.md](sandbox_method_reuse/README.md) | [ARCHITECTURE.md](sandbox_method_reuse/ARCHITECTURE.md) | [TODO.md](sandbox_method_reuse/TODO.md) | - |
| [Oomph Setup](sandbox_oomph) | [README.md](sandbox_oomph/README.md) | [ARCHITECTURE.md](sandbox_oomph/ARCHITECTURE.md) | [TODO.md](sandbox_oomph/TODO.md) | - |
| [Platform Helper](sandbox_platform_helper) | [README.md](sandbox_platform_helper/README.md) | [ARCHITECTURE.md](sandbox_platform_helper/ARCHITECTURE.md) | [TODO.md](sandbox_platform_helper/TODO.md) | - |
| [Product](sandbox_product) | [README.md](sandbox_product/README.md) | [ARCHITECTURE.md](sandbox_product/ARCHITECTURE.md) | [TODO.md](sandbox_product/TODO.md) | - |
| [Target Platform](sandbox_target) | [README.md](sandbox_target/README.md) | [ARCHITECTURE.md](sandbox_target/ARCHITECTURE.md) | [TODO.md](sandbox_target/TODO.md) | - |
| [Test Commons](sandbox_test_commons) | [README.md](sandbox_test_commons/README.md) | [ARCHITECTURE.md](sandbox_test_commons/ARCHITECTURE.md) | [TODO.md](sandbox_test_commons/TODO.md) | - |
| [Tools](sandbox_tools) | [README.md](sandbox_tools/README.md) | [ARCHITECTURE.md](sandbox_tools/ARCHITECTURE.md) | [TODO.md](sandbox_tools/TODO.md) | - |
| [Trigger Pattern](sandbox_triggerpattern) | [README.md](sandbox_triggerpattern/README.md) | [ARCHITECTURE.md](sandbox_triggerpattern/ARCHITECTURE.md) | [TODO.md](sandbox_triggerpattern/TODO.md) | - |
| [Usage View](sandbox_usage_view) | [README.md](sandbox_usage_view/README.md) | [ARCHITECTURE.md](sandbox_usage_view/ARCHITECTURE.md) | [TODO.md](sandbox_usage_view/TODO.md) | - |
| [Web (P2 Update Site)](sandbox_web) | [README.md](sandbox_web/README.md) | [ARCHITECTURE.md](sandbox_web/ARCHITECTURE.md) | [TODO.md](sandbox_web/TODO.md) | - |
| [XML Cleanup](sandbox_xml_cleanup) | [README.md](sandbox_xml_cleanup/README.md) | [ARCHITECTURE.md](sandbox_xml_cleanup/ARCHITECTURE.md) | [TODO.md](sandbox_xml_cleanup/TODO.md) | - |

**Documentation Structure per Plugin:**
- **README.md** - Quick start guide, features overview, and usage examples
- **ARCHITECTURE.md** - Design overview, implementation details, patterns used
- **TODO.md** - Pending features, known issues, future enhancements
- **TESTING.md** (where applicable) - Test organization, coverage, and running instructions

#### Test Infrastructure Documentation

- **[HelperVisitor API Test Suite](sandbox_common_test/TESTING.md)** - Comprehensive guide to testing with HelperVisitor API
- **[JUnit Migration Test Suite](sandbox_junit_cleanup_test/TESTING.md)** - Test organization for JUnit 4→5 migration
- **[JUnit Migration Implementation Tracking](sandbox_junit_cleanup_test/TODO_TESTING.md)** - Missing features and bugs in migration cleanup

#### Project Governance
- **[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)** - Community guidelines
- **[SECURITY.md](SECURITY.md)** - Security policy and vulnerability reporting
- **[CONTRIBUTING.md](#contributing)** - How to contribute to this project
- **[LICENSE.txt](LICENSE.txt)** - Eclipse Public License 2.0

#### Additional Resources
- **[TRIGGERPATTERN.md](sandbox_common/TRIGGERPATTERN.md)** - Pattern matching engine documentation
- **[Eclipse Version Configuration](#eclipse-version-configuration)** - How to update Eclipse versions
- **[Release Process](#release-process)** - How to create releases

### 📖 Documentation Guidelines for Contributors

When contributing to this project, please maintain documentation quality:

1. **Plugin Requirements**: All plugin directories SHOULD contain:
   - `README.md` - Quick start guide with features and usage examples
   - `ARCHITECTURE.md` - Design and implementation overview
   - `TODO.md` - Open tasks and future work
   
2. **Navigation Headers**: All plugin documentation files include navigation headers linking to:
   - Main README (this file)
   - Plugin's own README (for ARCHITECTURE and TODO files)
   - Sibling documentation files (README ↔ ARCHITECTURE ↔ TODO)

3. **Update Documentation**: When making code changes:
   - Update `README.md` if features or usage changes
   - Update `ARCHITECTURE.md` if design changes
   - Update `TODO.md` when completing tasks or identifying new ones
   - Update main README if adding/removing plugins

4. **Test Documentation**: Test modules with substantial test organization should include:
   - `TESTING.md` - Test structure and organization
   - `TODO_TESTING.md` (if applicable) - Implementation tracking for features being tested

### 🔍 Finding Documentation

**By Topic:**
- **Building & Setup**: [Build Instructions](#build-instructions), [Eclipse Version Configuration](#eclipse-version-configuration)
- **Code Coverage**: [Coverage Deployment](COVERAGE_DEPLOYMENT.md) - JaCoCo reports on GitHub Pages
- **Plugin Usage**: See [Projects](#projects) section for detailed descriptions of each plugin
- **Architecture**: Check `ARCHITECTURE.md` in each plugin directory
- **Testing**: [HelperVisitor API](sandbox_common_test/TESTING.md), [JUnit Migration](sandbox_junit_cleanup_test/TESTING.md)
- **Contributing**: [Contributing](#contributing), [Release Process](#release-process)

**By File Location:**
- **Root level**: Project-wide documentation (this README, CODE_OF_CONDUCT, SECURITY)
- **Plugin directories** (`sandbox_*/`): Plugin-specific ARCHITECTURE.md and TODO.md
- **Test directories** (`sandbox_*_test/`): Test-specific TESTING.md and TODO_TESTING.md

---

## Contributing

Contributions are welcome! This is an experimental sandbox project for testing Eclipse JDT cleanup implementations.

### How to Contribute

1. **Fork the repository** on GitHub
2. **Create a feature branch** from `main` (the default branch):
   ```bash
   git checkout -b feature/my-new-cleanup
   ```
3. **Make your changes** following the existing code structure and conventions
4. **Test your changes** thoroughly:
   ```bash
   mvn -Pjacoco verify
   ```
5. **Commit your changes** with clear commit messages:
   ```bash
   git commit -m "feat: add new cleanup for XYZ pattern"
   ```
6. **Push to your fork** and **create a Pull Request** targeting the `main` branch

### Guidelines

- Follow existing code patterns and cleanup structures
- Add comprehensive test cases for new cleanups
- Update documentation (README, architecture.md, todo.md) as needed
- Ensure SpotBugs, CodeQL, and all tests pass
- Keep changes focused and minimal

### Reporting Issues

Found a bug or have a feature request? Please [open an issue](https://github.com/carstenartur/sandbox/issues) on GitHub with:
- Clear description of the problem or suggestion
- Steps to reproduce (for bugs)
- Expected vs. actual behavior
- Eclipse and Java version information

**Note**: This project primarily serves as an experimental playground. Features that prove stable and useful may be contributed upstream to Eclipse JDT.

---

<details>
<summary>Legacy Branch CI Status</summary>

### 2022-09

[![Java CI with Maven](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml/badge.svg?branch=2022-09)](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml)  
[![CodeQL](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml/badge.svg?branch=2022-09)](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml)

### 2022-06

[![Java CI with Maven](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml/badge.svg?branch=2022-06)](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml)  
[![CodeQL](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml/badge.svg?branch=2022-06)](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml)

</details>

---

## License

This project is licensed under the **Eclipse Public License 2.0 (EPL-2.0)**.

See the [LICENSE.txt](LICENSE.txt) file for the full license text.

### Eclipse Public License 2.0

The Eclipse Public License (EPL) is a free and open-source software license maintained by the Eclipse Foundation. Key points:

- ✅ **Commercial use** allowed
- ✅ **Modification** allowed
- ✅ **Distribution** allowed
- ✅ **Patent grant** included
- ⚠️ **Disclose source** for modifications
- ⚠️ **License and copyright notice** required

For more information, visit: https://www.eclipse.org/legal/epl-2.0/

---

**Copyright © 2021-2025 Carsten Hammer and contributors**
