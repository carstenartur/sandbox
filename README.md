# Sandbox Project

A collection of experimental Eclipse JDT (Java Development Tools) cleanup plugins and tools. This repository demonstrates how to build custom JDT cleanups, quick fixes, and related tooling for Eclipse-based Java development.

**Main Technologies:** Eclipse JDT, Java 21, Maven/Tycho 5.0.3

**Status:** Work in Progress – All plugins are experimental and intended for testing purposes.

---

## 🔗 CI Status & Resources

[![Java CI with Maven](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml/badge.svg?branch=main)](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml)
[![CodeQL](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml/badge.svg?branch=main)](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml)
[![Coverage](https://github.com/carstenartur/sandbox/actions/workflows/coverage.yml/badge.svg?branch=main)](https://github.com/carstenartur/sandbox/actions/workflows/coverage.yml)
[![Tests](https://github.com/carstenartur/sandbox/actions/workflows/test-report.yml/badge.svg?branch=main)](https://github.com/carstenartur/sandbox/actions/workflows/test-report.yml)
[![Benchmarks](https://github.com/carstenartur/sandbox/actions/workflows/benchmark.yml/badge.svg?branch=main)](https://github.com/carstenartur/sandbox/actions/workflows/benchmark.yml)
[![Snapshot Deploy](https://github.com/carstenartur/sandbox/actions/workflows/deploy-snapshot.yml/badge.svg?branch=main)](https://github.com/carstenartur/sandbox/actions/workflows/deploy-snapshot.yml)
[![Commit Mining](https://github.com/carstenartur/sandbox/actions/workflows/mining-core.yml/badge.svg?branch=main)](https://github.com/carstenartur/sandbox/actions/workflows/mining-core.yml)
[![Refactoring Mining](https://github.com/carstenartur/sandbox/actions/workflows/refactoring-mining.yml/badge.svg?branch=main)](https://github.com/carstenartur/sandbox/actions/workflows/refactoring-mining.yml)
[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.20941684.svg)](https://doi.org/10.5281/zenodo.20941684)
[![Eclipse Marketplace](https://img.shields.io/badge/Eclipse%20Marketplace-Sandbox-blue)](https://marketplace.eclipse.org/content/sandbox)
<br>
🏠 **[Project Dashboard](https://carstenartur.github.io/sandbox/)** | 📊 **[Test Results](https://carstenartur.github.io/sandbox/tests/)** | 📈 **[Code Coverage](https://carstenartur.github.io/sandbox/coverage/)** | ⚡ **[Performance Charts](https://carstenartur.github.io/sandbox/dev/bench/)**

---

## Overview

This project provides:

- **Custom JDT Cleanup Plugins**: Automated code transformations for encoding, JUnit migration, functional programming patterns, and more
- **Eclipse Product Build**: A complete Eclipse product with bundled features
- **P2 Update Site**: Installable plugins via Eclipse update mechanism
- **Test Infrastructure**: JUnit 5-based tests for all cleanup implementations
- **Refactoring Mining Infrastructure**: AI-assisted commit analysis, DSL rule inference from Git diffs, standalone CLI and Eclipse-integrated mining tools
- **Standalone Tooling**: Maven plugin, CLI distributions, Docker packaging, JGit storage backend, and web interface modules

All plugins are work-in-progress and intended for experimentation and learning.

## 🚀 Installation

### Update Site URLs

Add one of the following update sites to your Eclipse installation:

#### Versioned Releases
```
https://carstenartur.github.io/sandbox/releases/
```
Use this for versioned release builds. The plugins remain experimental; validate them in a development workspace before adopting them.

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

> **For Maintainers**: See [Release Process](CONTRIBUTING.md#release-process) in CONTRIBUTING.md for instructions on creating and publishing releases.

## Table of Contents

- [🔗 CI Status & Resources](#-ci-status--resources)
- [Overview](#overview)
- [🚀 Installation](#-installation)
- [📦 Release Process](#-release-process)

- [Building from Source](#building-from-source)
- [Quickstart](#quickstart)
- [What's Included](#whats-included)
- [Projects](#projects)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)
  - [Eclipse Public License 2.0](#eclipse-public-license-20)

## GitHub Actions

The repository includes CI workflows for building, testing, and code quality analysis. A cleanup action for applying Eclipse JDT cleanups via GitHub Actions exists (see [GITHUB_ACTIONS.md](GITHUB_ACTIONS.md)) but is currently **not active for automatic PR cleanup** — it can only be triggered manually via `workflow_dispatch`.

**[📖 Full Documentation](GITHUB_ACTIONS.md)** | **[Workflows Guide](.github/workflows/README.md)** | **[Action Details](.github/actions/cleanup-action/README.md)**

## Building from Source

> **For Contributors/Developers**: Want to build the project locally? See the [Building from Source](CONTRIBUTING.md#building-from-source) section in CONTRIBUTING.md for complete build instructions.

**Quick Start:**
- **Requires**: Java 21 or later
- **Quick Build**: `mvn -T 1C verify`
- **Standalone IDE Product**: `mvn -Pproduct clean verify`
- **P2 Update Site**: `mvn -Prepo clean verify`
- **Complete Distribution**: `mvn -Pdistribution --batch-mode -Dtycho.localArtifacts=ignore clean verify`

The complete distribution command is platform-neutral and does not invoke Bash or Python. A headless Linux runner needs an X display for the SWT workbench launch, but that is runner setup rather than build logic.

**Note**: Building with Java 17 or earlier will fail. This project requires Java 21.

---

## Quickstart

### For Users

1. **Install the plugins** via Eclipse update site (see [Installation](#-installation) above)
2. **Open Eclipse** and navigate to **Source** → **Clean Up...** or use **Preferences** → **Java** → **Code Style** → **Clean Up**
3. **Configure cleanups**: Select the sandbox cleanup profiles you want to enable
4. **Apply cleanups**: Run cleanup on your Java files

### For Contributors/Developers

Want to build and run the Eclipse product with bundled plugins? See the [Building from Source](CONTRIBUTING.md#building-from-source) section in CONTRIBUTING.md for:
- Building the Eclipse product locally
- Running the built Eclipse product
- Using command-line cleanup tools

---

## What's Included

### Java Version Requirements

| Branch          | Java Version | Tycho Version |
|-----------------|--------------|---------------|
| `main` (2026-06)| Java 21      | 5.0.3         |

**Legacy branches**: Older branches (`2022-06`, `2022-09`, `2022-12`) use Java 11-17 with Tycho 3.x-4.x.

**Note**: Tycho 5.x requires Java 21+ at build time. Attempting to build with Java 17 will result in `UnsupportedClassVersionError`.

---

## Projects

> All projects are considered work in progress unless otherwise noted.

### Cleanup CLI Application (`sandbox_cleanup_application`)

It is an Equinox CLI application for running Eclipse JDT cleanup operations on Java files from the command line. It supports recursive directory processing, configurable cleanup profiles via properties files, verbose/quiet modes, and the full Eclipse cleanup registry. The current implementation processes one compilation unit per refactoring; atomic per-project batching for planned multi-file cleanups is tracked in [#1210](https://github.com/carstenartur/sandbox/issues/1210). A valid Eclipse workspace (`-data` parameter) is required.

---

### Encoding Cleanup (`sandbox_encoding_quickfix`)

Replaces platform-dependent or implicit encoding usage with explicit, safe alternatives using `StandardCharsets.UTF_8` or equivalent constants. Improves code portability and prevents encoding-related bugs across different platforms. Supports three cleanup strategies with Java version-aware transformations for FileReader, FileWriter, Files methods, Scanner, PrintWriter, and more.

📖 **Full Documentation**: [Plugin README](sandbox_encoding_quickfix/README.md) | [Architecture](sandbox_encoding_quickfix/ARCHITECTURE.md) | [TODO](sandbox_encoding_quickfix/TODO.md)

---

### Extra Search (`sandbox_extra_search`)

Specialized search view for identifying deprecated and critical API usage during Eclipse or Java version upgrades. Integrates with Eclipse JDT SearchEngine for workspace-wide type, method, and field reference searches. Includes a pre-populated list of commonly deprecated classes (Observable, Hashtable, SecurityManager, Applet, etc.) with jump-to-definition navigation and sortable results table.

📖 **Full Documentation**: [Plugin README](sandbox_extra_search/README.md) | [Architecture](sandbox_extra_search/ARCHITECTURE.md) | [TODO](sandbox_extra_search/TODO.md)

---

### Usage View (`sandbox_usage_view`)

Eclipse view plugin that detects **naming conflicts** in Java code — variables with the same name but different types (e.g., `String userId` vs `int userId`). Uses AST-based analysis with full binding resolution via `AstProcessorBuilder` from `sandbox_common_core`. Features a sortable table with columns for Name, Qualified Name, Package, Deprecated status, and Declaring Method. Automatically updates when switching between editors (`IPartListener2`), supports filtering for naming conflicts only (`NamingConflictFilter`), provides type-aware variable name suggestions (`VariableNameSuggester`), and can optionally auto-show at Eclipse startup via preferences.

📖 **Full Documentation**: [Plugin README](sandbox_usage_view/README.md) | [Architecture](sandbox_usage_view/ARCHITECTURE.md) | [TODO](sandbox_usage_view/TODO.md)

---

### Platform Status Helper (`sandbox_platform_helper`)

Simplifies Eclipse Platform `Status` object creation by replacing verbose `new Status(...)` constructor calls with cleaner factory methods (Java 11+ / Eclipse 4.20+) or StatusHelper pattern (Java 8). Reduces boilerplate and provides more readable code through automatic selection between StatusHelper or factory methods based on Java version.

📖 **Full Documentation**: [Plugin README](sandbox_platform_helper/README.md) | [Architecture](sandbox_platform_helper/ARCHITECTURE.md) | [TODO](sandbox_platform_helper/TODO.md)

---

### While-to-For Converter (`sandbox_tools`)

**While-to-For** loop converter — already merged into Eclipse JDT.

---

### JFace SubMonitor Migration (`sandbox_jface_cleanup`)

Automates migration from deprecated `SubProgressMonitor` to modern `SubMonitor` API. Transforms `beginTask()` + `SubProgressMonitor` to `SubMonitor.convert()` + `split()` with automatic handling of style flags, multiple monitor instances, and variable name collision resolution. The cleanup is idempotent and safe to run multiple times.

📖 **Full Documentation**: [Plugin README](sandbox_jface_cleanup/README.md) | [Architecture](sandbox_jface_cleanup/ARCHITECTURE.md) | [TODO](sandbox_jface_cleanup/TODO.md)

---

### Functional Loop Converter (`sandbox_functional_converter`)

Transforms imperative Java loops into functional Java 8 Stream equivalents (`forEach`, `map`, `filter`, `reduce`, `anyMatch`, `allMatch`, etc.). Supports 34 tested transformation patterns including max/min reductions, nested filters, compound operations, and Math.max/Math.min method references. Automatically **preserves comments** (line, block, Javadoc) during transformations. Supports **bidirectional loop conversions** (Enhanced-For ↔ Iterator-While) with comment preservation. Includes **target format selection** UI (Stream/For/While). Maintains semantic safety with variable scope validation, labeled continue detection, and side-effect analysis.

📖 **Full Documentation**: [Plugin README](sandbox_functional_converter/README.md) | [Architecture](sandbox_functional_converter/ARCHITECTURE.md) | [TODO](sandbox_functional_converter/TODO.md)

---

### JUnit 5 Migration Cleanup (`sandbox_junit_cleanup`)

Automates many migrations from JUnit 3 and JUnit 4 to JUnit 5 (Jupiter), including test classes, annotations, assertions, lifecycle hooks, and several rule patterns. Coordinated multi-file support currently covers named `ExternalResource` implementations and proven `@Rule`/`@ClassRule` consumers. Broader hierarchy, suite, runner, parameterized-test, and dependency migration remains tracked in [#1217](https://github.com/carstenartur/sandbox/issues/1217).

📖 **Full Documentation**: [Plugin README](sandbox_junit_cleanup/README.md) | [Architecture](sandbox_junit_cleanup/ARCHITECTURE.md) | [TODO](sandbox_junit_cleanup/TODO.md) | [Testing Guide](sandbox_junit_cleanup_test/TESTING.md)

---

### Method Reuse Detector (`sandbox_method_reuse`)

Identifies opportunities to reuse existing methods instead of duplicating logic. Uses token-based and AST-based analysis to find code duplication, suggests method calls to replace repeated patterns, and promotes DRY principles. Currently under development with initial focus on method similarity detection and Eclipse cleanup integration.

📖 **Full Documentation**: [Plugin README](sandbox_method_reuse/README.md) | [Architecture](sandbox_method_reuse/ARCHITECTURE.md) | [TODO](sandbox_method_reuse/TODO.md)

---

### PDE XML Cleanup (`sandbox_xml_cleanup`)

Optimizes Eclipse PDE XML files (plugin.xml, feature.xml, etc.) by reducing whitespace and optionally converting leading spaces to tabs. Uses secure XSLT transformation, normalizes excessive empty lines, and only processes PDE-relevant files in project root, OSGI-INF, or META-INF locations. Idempotent and preserves semantic integrity.

📖 **Full Documentation**: [Plugin README](sandbox_xml_cleanup/README.md) | [Architecture](sandbox_xml_cleanup/ARCHITECTURE.md) | [TODO](sandbox_xml_cleanup/TODO.md)

---

### CSS Cleanup (`sandbox_css_cleanup`)

Eclipse plugin for CSS validation and formatting using Prettier and Stylelint. Provides automatic formatting, linting, right-click menu integration for .css, .scss, and .less files, and a preferences page for configuration with graceful fallback when npm tools are not installed.

📖 **Full Documentation**: [Plugin README](sandbox_css_cleanup/README.md) | [Architecture](sandbox_css_cleanup/ARCHITECTURE.md) | [TODO](sandbox_css_cleanup/TODO.md)

---

### TriggerPattern DSL (`sandbox_triggerpattern`)

TriggerPattern is a declarative DSL for matching and transforming Java AST nodes. It supports pattern variables, semantic constraints, replacement templates, reusable rule sets, and Eclipse editor tooling. Rules can be executed through the Eclipse integration or the standalone cleanup application.

📖 **Full Documentation**: [Plugin README](sandbox_triggerpattern/README.md) | [Language Reference](docs/triggerpattern-language.md) | [Architecture](sandbox_triggerpattern/ARCHITECTURE.md)

---

### Int-to-Enum Cleanup (`sandbox_int_to_enum`)

Detects suitable integer constant groups and can migrate coordinated references toward enum-based representations. The implementation includes multi-file planning infrastructure; broader hierarchy and compatibility cases remain experimental.

📖 **Full Documentation**: [Plugin README](sandbox_int_to_enum/README.md) | [Architecture](sandbox_int_to_enum/ARCHITECTURE.md) | [TODO](sandbox_int_to_enum/TODO.md)

---

### Use General Type Cleanup (`sandbox_use_general_type`)

Replaces overly concrete local declaration types with suitable general interfaces when binding analysis proves the change safe.

📖 **Full Documentation**: [Plugin README](sandbox_use_general_type/README.md) | [Architecture](sandbox_use_general_type/ARCHITECTURE.md) | [TODO](sandbox_use_general_type/TODO.md)

---

## Documentation

- [Capability inventory](docs/capabilities.md)
- [Distribution compatibility](docs/distribution-compatibility.md)
- [TriggerPattern language](docs/triggerpattern-language.md)
- [Contributing and build instructions](CONTRIBUTING.md)

## Contributing

Contributions are welcome. Review [CONTRIBUTING.md](CONTRIBUTING.md) before submitting changes.

## License

### Eclipse Public License 2.0

This project is licensed under the Eclipse Public License 2.0. See [LICENSE](LICENSE) for details.
