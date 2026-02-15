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

> **For Contributors/Developers**: Want to build the project locally? See [Building from Source](CONTRIBUTING.md#building-from-source) in CONTRIBUTING.md for complete build instructions.

**Quick Start:**
- **Requires**: Java 21 or later
- **Quick Build**: `mvn -T 1C verify`
- **Full Build**: `mvn -Pproduct,repo -T 1C verify`

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
| `main` (2025-12)| Java 21      | 5.0.2         |

**Legacy branches**: Older branches (`2022-06`, `2022-09`, `2022-12`) use Java 11-17 with Tycho 3.x-4.x.

**Note**: Tycho 5.x requires Java 21+ at build time. Attempting to build with Java 17 will result in `UnsupportedClassVersionError`.

---

## Projects

> All projects are considered work in progress unless otherwise noted.

### Cleanup CLI Application (`sandbox_cleanup_application`)

It is a fully functional Equinox CLI application for running Eclipse JDT cleanup operations on Java files from the command line. It supports recursive directory processing, configurable cleanup profiles via properties files, verbose/quiet modes, and uses the full Eclipse cleanup registry including all sandbox-specific cleanups. Requires a valid Eclipse workspace (`-data` parameter).

---

### Encoding Cleanup (`sandbox_encoding_quickfix`)

Replaces platform-dependent or implicit encoding usage with explicit, safe alternatives using `StandardCharsets.UTF_8` or equivalent constants. Improves code portability and prevents encoding-related bugs across different platforms. Supports three cleanup strategies with Java version-aware transformations for FileReader, FileWriter, Files methods, Scanner, PrintWriter, and more.

📖 **Full Documentation**: [Plugin README](sandbox_encoding_quickfix/README.md) | [Architecture](sandbox_encoding_quickfix/ARCHITECTURE.md) | [TODO](sandbox_encoding_quickfix/TODO.md)

---

### Extra Search (`sandbox_extra_search`)

Experimental search tool for identifying critical classes when upgrading Eclipse or Java versions.

---

### Usage View (`sandbox_usage_view`)

Provides a table view of code objects, sorted by name, to detect inconsistent naming that could confuse developers.

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

Transforms imperative Java loops into functional Java 8 Stream equivalents (`forEach`, `map`, `filter`, `reduce`, `anyMatch`, `allMatch`, etc.). Supports 25+ tested transformation patterns including max/min reductions, nested filters, and compound operations. Maintains semantic safety by excluding complex patterns with labeled breaks, throws, or multiple mutable accumulators.

📖 **Full Documentation**: [Plugin README](sandbox_functional_converter/README.md) | [Architecture](sandbox_functional_converter/ARCHITECTURE.md) | [TODO](sandbox_functional_converter/TODO.md)

---

### JUnit 5 Migration Cleanup (`sandbox_junit_cleanup`)

Automates migration of legacy tests from JUnit 3 and JUnit 4 to JUnit 5 (Jupiter). Transforms test classes, methods, annotations, assertions, and lifecycle hooks to use the modern JUnit 5 API. Handles removing `extends TestCase`, converting naming conventions to annotations, assertion parameter reordering, rule migration, and test suite conversion.

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
### Common Utilities (`sandbox_common`)

Provides shared utilities, constants, and base classes used across all sandbox cleanup plugins. Serves as the foundation for the entire sandbox ecosystem with AST manipulation utilities, central cleanup constants repository (`MYCleanUpConstants`), reusable base classes, and Eclipse JDT compatibility structure for easy porting. Also hosts the TriggerPattern DSL engine for pattern matching, batch processing, and `.sandbox-hint` file support, along with mining infrastructure for git-based refactoring analysis.

📖 **Full Documentation**: [Plugin README](sandbox_common/README.md) | [Architecture](sandbox_common/ARCHITECTURE.md) | [TODO](sandbox_common/TODO.md)

---
### TriggerPattern Engine (`sandbox_triggerpattern`)

Provides a powerful pattern matching engine for code transformations in Eclipse. Allows defining code patterns using simple syntax with placeholder support (`$x` for any expression), `.sandbox-hint` DSL file format for rule definitions with `match`/`replace` blocks and guard expressions, annotation-based hints using `@TriggerPattern` and `@Hint`, batch processing of rules against compilation units, and automatic integration with Eclipse Quick Assist for creating custom hints and quick fixes with minimal boilerplate.

📖 **Full Documentation**: [Plugin README](sandbox_triggerpattern/README.md) | [Architecture](sandbox_triggerpattern/ARCHITECTURE.md) | [TODO](sandbox_triggerpattern/TODO.md)

---
### Fluent AST API (`sandbox-ast-api`)

Fluent, type-safe AST wrapper API using Java 21 features. Pure Maven module with no Eclipse dependencies, enabling reuse outside Eclipse context. Replaces verbose instanceof checks and nested visitor patterns with modern, readable fluent API for AST operations.

📖 **Full Documentation**: [Plugin README](sandbox-ast-api/README.md)

---
### JMH Performance Benchmarks (`sandbox-benchmarks`)

JMH (Java Microbenchmark Harness) performance benchmarks for the Sandbox project. Provides continuous performance tracking and visualization through GitHub Actions and GitHub Pages. Includes benchmarks for AST parsing, pattern matching, and loop transformation performance.

📖 **Full Documentation**: [Plugin README](sandbox-benchmarks/README.md)

---
### Functional Converter Core (`sandbox-functional-converter-core`)

Plain Java core module providing AST-independent representation of loop structures for transformation into functional/stream-based equivalents. Part of the Unified Loop Representation (ULR) implementation. No Eclipse/JDT dependencies - pure Java module reusable in any context.

📖 **Full Documentation**: [Plugin README](sandbox-functional-converter-core/README.md)

**Relationship**: This core module is used by `sandbox_functional_converter` (#8) to provide the underlying loop transformation logic without Eclipse dependencies.

---
### Oomph Workspace Setup (`sandbox_oomph`)

Provides Eclipse Oomph setup configurations for automated workspace configuration. Enables one-click setup with pre-configured Eclipse settings, automatic installation of required plugins, Git repository cloning and branch setup, and seamless integration with Eclipse Installer.

📖 **Full Documentation**: [Plugin README](sandbox_oomph/README.md) | [Architecture](sandbox_oomph/ARCHITECTURE.md) | [TODO](sandbox_oomph/TODO.md)

---

### Use General Type (`sandbox_use_general_type`)

Eclipse cleanup plugin that suggests replacing specific types with more general supertypes (e.g., `ArrayList` → `List`, `HashMap` → `Map`). Uses the TriggerPattern DSL for rule definitions and promotes programming to interfaces.

📖 **Full Documentation**: [Plugin README](sandbox_use_general_type/README.md)

---

### Int to Enum (`sandbox_int_to_enum`)

Experimental Eclipse cleanup plugin for converting integer constants to Java enum types. Identifies groups of related `static final int` constants that represent enumerated values and suggests migration to type-safe enums.

---

### Fluent AST API JDT Bridge (`sandbox-ast-api-jdt`)

Bridge module between Eclipse JDT AST nodes and the sandbox-ast-api fluent types. Provides `JDTConverter` for converting JDT expressions, statements, and bindings to fluent wrapper types. Enables sandbox plugins to use the fluent API without changing their existing JDT-based infrastructure.

---

## Documentation

This repository contains extensive documentation organized at multiple levels to help you understand, use, and contribute to the project.

📚 **For a complete documentation index covering all plugins, architecture guides, and contributing information**, see [DOCUMENTATION_INVENTORY.md](DOCUMENTATION_INVENTORY.md).

### Quick Documentation Links

- **[Installation](#-installation)** - How to install plugins in Eclipse
- **[Building from Source](CONTRIBUTING.md#building-from-source)** - How to build the project with Maven/Tycho
- **[Projects](#projects)** - Descriptions and documentation for all plugins
- **[Contributing](CONTRIBUTING.md)** - How to contribute to this project
- **[Release Process](CONTRIBUTING.md#release-process)** - Maintainer guide for creating releases
- **[Eclipse Version Configuration](CONTRIBUTING.md#eclipse-version-configuration)** - Maintainer guide for updating Eclipse versions

---

## Contributing

Contributions are welcome! This is an experimental sandbox project for testing Eclipse JDT cleanup implementations.

**📖 For full contribution guidelines, building instructions, reporting issues, release process, and Eclipse version configuration**, see [CONTRIBUTING.md](CONTRIBUTING.md).

### Quick Start

1. Fork the repository and create a feature branch from `main`
2. Make your changes following existing code structure
3. Test thoroughly with `mvn -Pjacoco verify`
4. Submit a Pull Request with clear description

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

**Copyright © 2021-2026 Carsten Hammer and contributors**
