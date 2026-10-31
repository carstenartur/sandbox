# Sandbox Project

Sandbox helps Java developers modernize existing code, turn repeated edits into reusable project rules, and investigate typed calculations and Git history. Built on Eclipse JDT, it includes Eclipse cleanup workflows, headless tools, a separate Git/source-history server, and reusable Java libraries.

**Status:** Experimental and under active development. Supported transformations have regression tests; the supported source shapes, evidence and limitations are documented per module.

## Overview

Choose the workflow that matches your work:

| You want to… | Start here |
|---|---|
| Modernize an existing Java codebase | [Preview a cleanup in Eclipse](#modernize-existing-java) or [review proposals in GitHub](#github-actions) |
| Turn an ordinary code edit into a rule your team can reuse | [Create, try and share a project rule](#turn-a-code-edit-into-a-project-rule) |
| Investigate numerical Java code with explicit semantics and proof evidence | [Mathematics cleanup and headless qualification](#investigate-typed-java-calculations) |
| Search repositories, source versions and Java symbols over Git history | [Run the Git/source-history server](#search-git-and-source-history) |
| Build your own analyzer or transformation tool | [Reuse the analysis and AST libraries](#build-on-the-analysis-and-ast-libraries) |

## Table of Contents

- [Quickstart](#quickstart)
- [GitHub Actions](#github-actions)
- [🚀 Installation](#-installation)
- [Building from Source](#building-from-source)
- [What's Included](#whats-included)
- [Projects](#projects)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [📦 Release Process](#-release-process)
- [🔗 CI Status & Resources](#-ci-status--resources)
- [License](#license)

## Quickstart

<a id="for-users"></a>

### Modernize existing Java

For maintainers updating Java APIs, encodings, tests or loop idioms:

1. [Install the relevant Eclipse feature](#-installation).
2. Open **Source → Clean Up…**, or edit a profile under **Java → Code Style → Clean Up**.
3. Enable the cleanup you need, review its source diff, and run your project's normal tests before accepting the change.
4. Open **Help → Help Contents** for the feature's offline guide, or press **F1** on a Sandbox configuration page.

Start with [explicit encoding](sandbox_encoding_quickfix/README.md), [JUnit migration capabilities](sandbox_junit_cleanup/MIGRATION_CAPABILITIES.md), or [loop conversion](sandbox_functional_converter/README.md) for concrete examples and supported cases.

For terminal or CI use, the [headless application](sandbox_cleanup_application/README.md) offers check/diff reports, and the [GitHub workflow](#github-actions) creates reviewable proposals. These cleanup paths still require an Eclipse workspace with valid project/classpath metadata; an arbitrary Maven or Gradle checkout is not automatically resolved. The default CLI processes one compilation unit per refactoring. A [separate project-wide QA runner](sandbox_cleanup_application/README.md#project-wide-migration-qa) handles complete project scope for supported coordinated migrations.

### Turn a code edit into a project rule

For developers repeating an API replacement or a project convention, the [change-to-rule guide](docs/triggerpattern-change-to-rule.md) follows the complete current workflow:

1. Improve a saved Java file and leave the edit unsaved. Choose **Sandbox TriggerPattern → Generalize this change…**.
2. Review the actual before/after lines. Use **Choose variables to generalize…** to select the names that should become placeholders; nothing is generalized automatically.
3. Inspect the complete `.sandbox-hint` draft, including its conditions and binding policy.
4. Choose **Try on my code…** and select a Java project. Inspect candidate bindings and guard outcomes, then use **Preview** to review the real project diff, including imports and companion edits.
5. Apply the reviewed trial with ordinary Eclipse refactoring Undo available. Finish the wizard to save the rule, then commit the `.sandbox-hint` file with the project so the team can use it through hint Quick Assist and cleanup.

A trial uses the project's source, compiler options and classpath without installing the draft. Language checks and replacement compiler checks do not prove behavioral equivalence: positive/negative examples, project tests and review still matter. The trial supports self-contained text rules; includes, embedded Java and coordinated semantic-plan actions use their specialized paths. The guide records further authoring and comment-preservation limits.

### Investigate typed Java calculations

For numerical-code reviewers and tool authors, [Mathematics Cleanup](sandbox_math_cleanup/README.md) turns bounded Java calculations into typed Regelsuche requests and offers verified Java replacements for review. Start with its actual Bouncy Castle source rewrite, then use Eclipse cleanup/selection Quick Assist or the [headless corpus workflow](qa/math-optimization/README.md).

Sandbox owns Java bindings, source effects and edits; [Regelsuche](https://github.com/carstenartur/Regelsuche) owns mathematical search and independent proof. The cleanup is disabled by default. Strict Java behavior and explicit mathematical opt-in remain separate. Supported straight-line primitive integral tasks can simplify arithmetic while retaining reads, calls, local assignments and stores in their original order; the scanner's optional task summaries remain inspection data. Accepted rewrites or estimated costs do not establish a measured speedup or constant-time behavior. The [dated qualification](docs/superpowers/plans/2026-10-30-math-optimization-qualification.md) records exact sources, SDK bytes, corpus results and remaining boundaries.

### Search Git and source history

For repository tooling and historical Java analysis, start the [separate Git server](sandbox-jgit-server-webapp/README.md#first-start-and-restart), import a repository with ordinary Git push, and use its REST API to search commit messages, changed paths, historical Java types/symbols, file versions and author statistics. This server can be used without the Eclipse workbench.

The [Extra Search views](sandbox_extra_search_help/html/usage.html) are optional Eclipse clients for the same service. Installing that feature does not start the database or server. Semantic/hybrid code search has [additional embedding prerequisites](sandbox-jgit-server-webapp/docs/application-embeddings.md).

### Build on the analysis and AST libraries

For tool builders, [`sandbox_common_core`](sandbox_common_core/README.md) provides JDT-based visitors, TriggerPattern matching, guards and hint parsing without an Eclipse Platform/UI runtime. Use its Maven build and dependency example to embed those APIs; callers still supply the source and binding environment their analysis requires.

[`sandbox-ast-api`](sandbox-ast-api/README.md) provides fluent AST wrapper types, and [`sandbox-functional-converter-core`](sandbox-functional-converter-core/README.md) models loop transformations; both are plain Java modules without Eclipse/JDT dependencies. The [JDT bridge](#fluent-ast-api-jdt-bridge-sandbox-ast-api-jdt) connects the fluent types to existing JDT trees. The [module catalog](#projects) also lists the mining core, mining CLI and shared test infrastructure.

## GitHub Actions

Add automated, reviewable cleanup proposals to your own **Eclipse Java repository** with one workflow file. Existing `.project`, `.classpath` and compiler settings are reused; no Sandbox checkout or copied default profile is required. Proposals use normal GitHub diffs and are accepted together through a cleanup PR, not pushed automatically into your original branch.

**[Set up cleanup proposals in your project](GITHUB_ACTIONS.md)** | **[Standalone Eclipse example](examples/cleanup-review/eclipse)** | **[Action reference](.github/actions/cleanup-review/README.md)**

Maven/Gradle repositories without Eclipse metadata and PDE target-platform dependencies need additional preparation; the guide describes the supported boundaries. Sandbox's own `pr-auto-cleanup.yml` runs automatically and also contains internal tests that consumers should not copy.

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

Each cleanup or quick-fix feature installs its matching offline user documentation. After restarting Eclipse, open **Help** → **Help Contents** and select the corresponding **Sandbox – ...** book. Sandbox-owned configuration pages also support context-sensitive **F1** help.

> **⚠️ Warning**: These plugins are experimental. Test them in a development environment before using in production.

## Building from Source

> **For Contributors/Developers**: Want to build the project locally? See [Building and testing](CONTRIBUTING.md#building-and-testing) in CONTRIBUTING.md for complete build instructions.

**Quick Start:**

- **Requires**: Java 25
- **Quick Build**: `mvn -T 1C verify`
- **Standalone IDE Product**: `mvn -Pproduct clean verify`
- **Complete Distribution**: `mvn -Pdistribution --batch-mode -Dtycho.localArtifacts=ignore clean verify`
- **Eclipse Help and screenshot reproduction**: [docs/ECLIPSE_HELP.md](docs/ECLIPSE_HELP.md)

**Note**: The supported build/runtime is Java 25. Java 21 and earlier are rejected; later JDKs require separate qualification.

**Main Technologies:** Eclipse JDT, Java 25, Maven/Tycho 5.0.4

### For Contributors/Developers

Want to build and run the Eclipse product with bundled plugins? See the [Building and testing](CONTRIBUTING.md#building-and-testing) section in CONTRIBUTING.md for:

- Building the Eclipse product locally
- Running the built Eclipse product
- Using command-line cleanup tools

## What's Included

### Java Version Requirements

| Branch          | Java Version | Tycho Version |
|-----------------|--------------|---------------|
| `main` (2026-09)| Java 25      | 5.0.4         |

**Legacy branches**: Older branches (`2022-06`, `2022-09`, `2022-12`) use Java 11-17 with Tycho 3.x-4.x.

**Note**: Java 25 is the Sandbox project baseline. This does not assert that every Eclipse 2026-09 or Tycho 5.x installation independently requires Java 25. Cleanup targets retain their own source/compliance levels.

---

## Projects

> All projects are considered work in progress unless otherwise noted.

### Cleanup CLI Application (`sandbox_cleanup_application`)

Runs installed Eclipse JDT cleanups from the command line with recursive inputs, properties-based profiles, check/apply/diff modes and patch/JSON evidence. The default `org.sandbox.jdt.core.JavaCleanup` application processes one compilation unit per refactoring. The separate `org.sandbox.jdt.core.ProjectWideJavaCleanup` application runs one refactoring over a complete Eclipse Java project for semantic migration QA; it has its own check/apply contract. Both require a valid Eclipse workspace and resolved project metadata.

**Documentation:** [CLI usage and limits](sandbox_cleanup_application/README.md) | [Project-wide migration QA](sandbox_cleanup_application/README.md#project-wide-migration-qa)

---

### Mathematics Cleanup (`sandbox_math_cleanup`)

Experimental, Regelsuche-backed optimization of method-local Java calculations. The adapter supplies typed computations and original evaluation occurrences to the independent numeric checker, then previews ordinary Java replacements. Strict Java behavior and explicitly enabled mathematical semantics remain separate; estimated savings are not measured speedups or cryptographic constant-time guarantees.

Whole-file strict integral analysis can connect a bounded set of immutable local calculations across unchanged call statements. Source-version-bound provenance and grouped JDT edits keep those calls and comments intact; uncertain effects, memory dependencies and arithmetic exceptions remain conservative boundaries. The cleanup is disabled by default and initially selects BigInteger only; select primitive integral types to use the new connected-slice fragment.

**Documentation:** [Plugin README](sandbox_math_cleanup/README.md) | [JDT/CAS architecture](sandbox_math_cleanup/ARCHITECTURE.md) | [Current qualification](docs/superpowers/plans/2026-10-30-math-optimization-qualification.md) | [Source slices and limits](sandbox_math_cleanup/SOURCE_SLICES.md) | [Source-backed BigInteger rewrites](sandbox_math_cleanup/SOURCE_BACKED_REWRITES.md) | [Practical BC/SWT review profiles](qa/math-optimization/PRACTICAL_REVIEW.md) | [Installed Help source](sandbox_math_cleanup_help/html/cleanup.html)

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

<a id="junit-5-migration-cleanup-sandbox_junit_cleanup"></a>

### JUnit Migration Cleanup (`sandbox_junit_cleanup`)

Automates explicitly modeled JUnit 3/4 migrations to Jupiter, including annotations, classified assertions, lifecycle hooks and rule patterns. Generated Jupiter source can target JUnit 5 or 6 according to the project's runtime dependencies; selected removed JUnit 6 APIs have dedicated compatibility rewrites.

Coordinated support includes ordinary closed JUnit 3 hierarchies, proven `ExternalResource` fixture/consumer chains, modeled suites, selected Parameterized provider/injection closures, and direct private or package-private static assertion/assumption helpers. Each has its own binding, source-scope and execution requirements. Custom harnesses/runners, broader helper APIs and automatic JUnit 4/Vintage dependency removal remain outside general support. See the [executable capability matrix](sandbox_junit_cleanup/MIGRATION_CAPABILITIES.md) for the implementation and tests behind each boundary; ongoing migration work is tracked in [#1217](https://github.com/carstenartur/sandbox/issues/1217).

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

📖 **Full Documentation**: [Change to project rule](docs/triggerpattern-change-to-rule.md) | [Plugin README](sandbox_triggerpattern/README.md) | [Architecture](sandbox_triggerpattern/ARCHITECTURE.md) | [TODO](sandbox_triggerpattern/TODO.md)

---
### Fluent AST API (`sandbox-ast-api`)

Fluent, type-safe AST wrapper API using Java 25 features. Pure Maven module with no Eclipse dependencies, enabling reuse outside Eclipse context. Replaces verbose instanceof checks and nested visitor patterns with modern, readable fluent API for AST operations.

📖 **Full Documentation**: [Plugin README](sandbox-ast-api/README.md)

---
### JMH Performance Benchmarks (`sandbox-benchmarks`)

JMH (Java Microbenchmark Harness) performance benchmarks for the Sandbox project. Provides continuous performance tracking and visualization through GitHub Actions and GitHub Pages. Includes benchmarks for AST parsing, pattern matching, and loop transformation performance.

📖 **Full Documentation**: [Plugin README](sandbox-benchmarks/README.md)

---
### Functional Converter Core (`sandbox-functional-converter-core`)

Plain Java core module providing AST-independent representation of loop structures for transformation into functional/stream-based equivalents. Part of the Unified Loop Representation (ULR) implementation. No Eclipse/JDT dependencies - pure Java module reusable in any context.

📖 **Full Documentation**: [Plugin README](sandbox-functional-converter-core/README.md)

**Relationship**: This core module is used by `sandbox_functional_converter` to provide the underlying loop transformation logic without Eclipse dependencies.

---
### Oomph Workspace Setup (`sandbox_oomph`)

Sandbox is included in the official Eclipse Installer catalog. In Advanced Mode, select Eclipse SDK 4.41 (2026-09) and **Github Projects → Sandbox Project → Main**. Oomph installs the development tools, clones the repository, imports the Maven and Eclipse projects, activates the target platform and builds the workspace. See the [setup guide](sandbox_oomph/README.md) for existing workspaces and development launches.

📖 **Full Documentation**: [Plugin README](sandbox_oomph/README.md) | [Architecture](sandbox_oomph/ARCHITECTURE.md) | [TODO](sandbox_oomph/TODO.md)

---

### Use General Type (`sandbox_use_general_type`)

Eclipse cleanup plugin that suggests replacing specific types with more general supertypes (e.g., `ArrayList` → `List`, `HashMap` → `Map`). Uses the TriggerPattern DSL for rule definitions and promotes programming to interfaces.

📖 **Full Documentation**: [Plugin README](sandbox_use_general_type/README.md)

---

### Int to Enum (`sandbox_int_to_enum`)

Experimental Eclipse cleanup plugin for converting proven integer state domains to Java enum types. It supports a conservative private single-file path and a package-scoped multi-file path that coordinates declarations, comparisons, and callers. Public APIs, persistence/wire values, bit flags, aliases, arbitrary integer arguments, and incomplete scopes are deliberately rejected.

---

### Fluent AST API JDT Bridge (`sandbox-ast-api-jdt`)

Bridge module between Eclipse JDT AST nodes and the sandbox-ast-api fluent types. Provides `JDTConverter` for converting JDT expressions, statements, and bindings to fluent wrapper types. Enables sandbox plugins to use the fluent API without changing their existing JDT-based infrastructure.

---

### Common Core (`sandbox_common_core`)

Eclipse-independent common core module. A plain Maven JAR with no Eclipse Platform/UI dependencies (relies on JDT Core plus non-Eclipse libraries such as JGit, Gson, and SLF4J) containing HelperVisitor API, TriggerPattern engine, pattern matching, guard expressions, hint file parsing (HintFileStore, BuiltInGuards), and LambdaASTVisitor. Enables standalone usage, fast testing without Xvfb, and CLI tool development. Existing Eclipse plugins continue to depend on `sandbox_common` which re-exports this module with `visibility:=reexport`.

📖 **Full Documentation**: [Module README](sandbox_common_core/README.md)

---

### Test Commons (`sandbox_test_commons`)

Shared JUnit 5 test infrastructure for Eclipse JDT–based tests. Provides the `AbstractEclipseJava` JUnit 5 extension and version-specific subclasses (for example, `EclipseJava17`) under `org.sandbox.jdt.ui.tests.quickfix.rules` to configure Eclipse/Java environments for tests. Consumed by other `sandbox_*_test` modules to ensure consistent setup of the Eclipse Java tooling across different Java versions.

📖 **Full Documentation**: [Module README](sandbox_test_commons/README.md)

---

### Refactoring Mining Core (`sandbox_mining_core`)

AI-assisted commit analysis engine for inferring DSL rules from Git diffs. Provides LLM-based rule inference (supports multiple providers), state management with deferred commits, keyword filtering, comparison mode, and automatic HintFile generation.

---

### Refactoring Mining CLI (`sandbox_mining_cli`)

Standalone CLI tool for refactoring mining: clones Git repositories, scans source code against `.sandbox-hint` rules, and generates JSON/Markdown reports.

---

### JGit Storage Backend (`sandbox-jgit-storage-hibernate`)

Minimal OSGi bridge to the released [jgit-storage-hibernate](https://github.com/carstenartur/jgit-storage-hibernate) Core library. Git objects, refs and history are stored in a database; the executable server additionally uses the released Search and Java Analysis modules.

**Documentation:** [Storage boundary](sandbox-jgit-storage-hibernate/README.md) | [Server setup](sandbox-jgit-server-webapp/README.md)

---

### JGit Server WebApp (`sandbox-jgit-server-webapp`)

Store repositories through ordinary Git push, search commit messages and changed paths, inspect historical Java types and symbols, and query file versions and author statistics. Optional local embeddings add semantic and hybrid code search.

The **Extra Search** Eclipse feature provides **Git Database Search**, **Git File History**, **Commit Analytics**, and **Semantic Code Search** clients. Start the separate server, import a repository, then enter its server name in the view. Installing the Eclipse feature alone does not start a database or server.

**Get started:** [Eclipse workflow and controls](sandbox_extra_search_help/html/usage.html) | [Server and REST API](sandbox-jgit-server-webapp/README.md) | [Optional embedding prerequisites](sandbox-jgit-server-webapp/docs/application-embeddings.md)

---

### Additional Tools

The following directories provide supplementary tooling and distribution packaging:

- **`sandbox-maven-plugin`** — Maven plugin with goals for running Sandbox cleanups in CI/CD pipelines
- **`sandbox_cleanup_cli_dist`** — Distribution packaging for the cleanup CLI
- **`sandbox_cleanup_docker`** — Docker container for the cleanup CLI

---

## Documentation

This repository contains extensive documentation organized at multiple levels to help you understand, use, and contribute to the project.

Installed end-user documentation is available offline in Eclipse through **Help** → **Help Contents**. The source layout, validation contract, and checkout-local screenshot reproduction process are documented in [docs/ECLIPSE_HELP.md](docs/ECLIPSE_HELP.md).

📚 **For a complete documentation index covering all plugins, architecture guides, and contributing information**, see [DOCUMENTATION_INVENTORY.md](DOCUMENTATION_INVENTORY.md).

### Quick Documentation Links

- **[Installation](#-installation)** - How to install plugins in Eclipse
- **[Installed Eclipse Help](docs/ECLIPSE_HELP.md)** - Help-bundle architecture, local validation, and screenshot reproduction
- **[Building from Source](CONTRIBUTING.md#building-and-testing)** - How to build the project with Maven/Tycho
- **[Projects](#projects)** - Descriptions and documentation for all plugins
- **[Contributing](CONTRIBUTING.md)** - How to contribute to this project
- **[Release Process](CONTRIBUTING.md#release-process)** - Maintainer guide for creating releases
- **[Distribution Compatibility](docs/distribution-compatibility.md)** - Supported build matrix, runtime coverage, publication gates, and evidence
- **[Historical Multi-file Cleanup QA](docs/qa/multi-file-cleanup-post-merge-qa.md)** - Scope, risks and follow-ups for the cited revisions; use the current module documentation for today's support
- **[Eclipse Version Configuration](CONTRIBUTING.md#updating-eclipse-maven-or-tycho)** - Maintainer guide for updating Eclipse versions

---

## Contributing

Contributions are welcome across the cleanup workflows, rule authoring, Java analysis libraries, mathematics adapter and Git/source-history tools.

**📖 For full contribution guidelines, building instructions, reporting issues, release process, and Eclipse version configuration**, see [CONTRIBUTING.md](CONTRIBUTING.md).

### Quick Start

1. Fork the repository and create a feature branch from `main`
2. Make your changes following existing code structure
3. Test thoroughly with `mvn -Pjacoco verify`
4. Submit a Pull Request with clear description

**Note**: This project primarily serves as an experimental playground. Features that prove stable and useful may be contributed upstream to Eclipse JDT.

---

## 📦 Release Process

> **For Maintainers**: See [Release Process](CONTRIBUTING.md#release-process) in CONTRIBUTING.md for instructions on creating and publishing releases.

## 🔗 CI Status & Resources

[![Java CI with Maven](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml/badge.svg?branch=main)](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml)
[![CodeQL](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml/badge.svg?branch=main)](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml)
[![Coverage](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fcarstenartur%2Fsandbox%2Fgh-pages%2Fbadges%2Fcoverage.json)](https://carstenartur.github.io/sandbox/coverage/)
[![Tests](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fcarstenartur%2Fsandbox%2Fgh-pages%2Fbadges%2Ftests.json)](https://carstenartur.github.io/sandbox/tests/)
[![Benchmarks](https://github.com/carstenartur/sandbox/actions/workflows/benchmark.yml/badge.svg?branch=main)](https://github.com/carstenartur/sandbox/actions/workflows/benchmark.yml)
[![Snapshot Deploy](https://github.com/carstenartur/sandbox/actions/workflows/deploy-snapshot.yml/badge.svg?branch=main)](https://github.com/carstenartur/sandbox/actions/workflows/deploy-snapshot.yml)
[![Commit Mining](https://github.com/carstenartur/sandbox/actions/workflows/mining-core.yml/badge.svg?branch=main)](https://github.com/carstenartur/sandbox/actions/workflows/mining-core.yml)
[![Refactoring Mining](https://github.com/carstenartur/sandbox/actions/workflows/refactoring-mining.yml/badge.svg?branch=main)](https://github.com/carstenartur/sandbox/actions/workflows/refactoring-mining.yml)
[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.20941684.svg)](https://doi.org/10.5281/zenodo.20941684)
[![Eclipse Marketplace](https://img.shields.io/badge/Eclipse%20Marketplace-Sandbox-blue)](https://marketplace.eclipse.org/content/sandbox)
<br>
🏠 **[Project Dashboard](https://carstenartur.github.io/sandbox/)** | 📊 **[Test Results](https://carstenartur.github.io/sandbox/tests/)** | 📈 **[Code Coverage](https://carstenartur.github.io/sandbox/coverage/)** | ⚡ **[Performance Charts](https://carstenartur.github.io/sandbox/dev/bench/)**

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
