# Sandbox Project

A collection of experimental Eclipse JDT (Java Development Tools) cleanup plugins and tools. This repository demonstrates how to build custom JDT cleanups, quick fixes, and related tooling for Eclipse-based Java development.

**Main Technologies:** Eclipse JDT, Java 21, Maven/Tycho 5.0.1

**Status:** Work in Progress – All plugins are experimental and intended for testing purposes.

## Overview

This project provides:

- **Custom JDT Cleanup Plugins**: Automated code transformations for encoding, JUnit migration, functional programming patterns, and more
- **Eclipse Product Build**: A complete Eclipse product with bundled features
- **P2 Update Site**: Installable plugins via Eclipse update mechanism
- **Test Infrastructure**: JUnit 5-based tests for all cleanup implementations

All plugins are work-in-progress and intended for experimentation and learning.

## Table of Contents

- [Overview](#overview)
- [Build Instructions](#build-instructions)
- [Eclipse Version Configuration](#eclipse-version-configuration)
- [Quickstart](#quickstart)
- [CI Status](#ci-status)
- [What's Included](#whats-included)
- [Projects](#projects)
  - [sandbox_cleanup_application](#1-sandbox_cleanup_application)
  - [sandbox_encoding_quickfix](#2-sandbox_encoding_quickfix)
    - [Encoding Cleanup – Replace Platform Encoding with Explicit Charset](#encoding-cleanup-replace-platform-encoding-with-explicit-charset)
    - [Based on Test Coverage](#based-on-test-coverage)
    - [Cleanup Strategies](#cleanup-strategies)
    - [Java Version Awareness](#java-version-awareness)
    - [Supported Classes and APIs](#supported-classes-and-apis)
    - [Examples](#examples)
      - [Example: FileReader Replacement](#example-filereader-replacement)
      - [Example: Channels.newReader (Java 10+)](#example-channelsnewreader-java-10)
      - [Example: Files.readAllLines (Java 10+)](#example-filesreadalllines-java-10)
      - [Example: Scanner (Java 10+)](#example-scanner-java-10)
      - [Example: SAX InputSource](#example-sax-inputsource)
    - [Aggregation Mode Example](#aggregation-mode-example)
    - [Additional Fixes](#additional-fixes)
    - [Cleanup Mode × Java Version Matrix](#cleanup-mode-java-version-matrix)
    - [Usage](#usage)
    - [Encoding Cleanup – Strategy Variants](#encoding-cleanup-strategy-variants)
      - [Strategy: Prefer UTF-8](#strategy-prefer-utf-8)
      - [Strategy: Keep Behavior](#strategy-keep-behavior)
      - [Strategy: Aggregate UTF-8](#strategy-aggregate-utf-8)
      - [Summary Table](#summary-table)
    - [Charset Literal Replacement Table](#charset-literal-replacement-table)
    - [Limitations](#limitations)
  - [sandbox_extra_search](#3-sandbox_extra_search)
  - [sandbox_usage_view](#4-sandbox_usage_view)
  - [sandbox_platform_helper](#5-sandbox_platform_helper)
    - [Platform Status Cleanup – Simplification of `new Status(...)` Calls](#platform-status-cleanup-simplification-of-new-status-calls)
    - [Motivation](#motivation)
    - [Before/After Comparison](#beforeafter-comparison)
    - [Examples](#examples-1)
      - [Java 8: With `StatusHelper`](#java-8-with-statushelper)
      - [Java 11+: With `Status.warning(...)`](#java-11-with-statuswarning)
      - [With Exception](#with-exception)
    - [Cleanup Strategy Selection](#cleanup-strategy-selection)
    - [Requirements](#requirements)
    - [Usage](#usage-1)
    - [Limitations](#limitations-1)
  - [sandbox_tools](#6-sandbox_tools)
  - [sandbox_jface_cleanup](#7-sandbox_jface_cleanup)
    - [JFace Cleanup – SubProgressMonitor to SubMonitor Migration](#jface-cleanup--subprogressmonitor-to-submonitor-migration)
    - [Purpose](#purpose)
    - [Migration Pattern](#migration-pattern)
      - [Basic Transformation](#basic-transformation)
      - [With Style Flags](#with-style-flags)
    - [Unique Variable Name Handling](#unique-variable-name-handling)
    - [Idempotence](#idempotence)
    - [Official Eclipse Documentation](#official-eclipse-documentation)
    - [Requirements](#requirements-2)
    - [Cleanup Name & Activation](#cleanup-name-activation-1)
    - [Limitations](#limitations-4)
    - [Test Coverage](#test-coverage)
  - [sandbox_functional_converter](#8-sandbox_functional_converter)
    - [Functional Converter Cleanup – Transform Imperative Loops into Functional Java 8 Streams](#functional-converter-cleanup-transform-imperative-loops-into-functional-java-8-streams)
    - [Source and Test Basis](#source-and-test-basis)
    - [Supported Transformations](#supported-transformations)
    - [Examples](#examples-2)
    - [Reductions (Accumulators)](#reductions-accumulators)
    - [Not Yet Supported (Disabled Tests)](#not-yet-supported-disabled-tests)
    - [Ignored Cases – No Cleanup Triggered](#ignored-cases-no-cleanup-triggered)
    - [Java Version Compatibility](#java-version-compatibility)
    - [Cleanup Name & Activation](#cleanup-name-activation)
    - [Limitations](#limitations-2)
    - [Summary](#summary)
  - [sandbox_junit](#9-sandbox_junit)
    - [JUnit Cleanup – Feature Overview](#junit-cleanup-feature-overview)
    - [Migration Summary](#migration-summary)
    - [JUnit 3 Classes and Methods](#junit-3-classes-and-methods)
      - [JUnit 3 Migration Summary Table](#junit-3-migration-summary-table)
      - [Class Structure Transformations](#class-structure-transformations)
      - [Test Method Transformations](#test-method-transformations)
      - [Setup and Teardown Methods](#setup-and-teardown-methods)
      - [Test Suite Migration](#test-suite-migration)
    - [JUnit 4 Annotations and Classes](#junit-4-annotations-and-classes)
      - [JUnit 4 Migration Summary Table](#junit-4-migration-summary-table)
      - [Lifecycle Annotations](#lifecycle-annotations)
      - [Test Annotations](#test-annotations)
      - [Test Suite Annotations](#test-suite-annotations)
      - [Rule Annotations](#rule-annotations)
    - [JUnit Assertion Migration – JUnit 3 and 4 to JUnit 5](#junit-assertion-migration-junit-3-and-4-to-junit-5)
      - [Supported Assertion Methods](#supported-assertion-methods)
      - [Parameter Order Differences](#parameter-order-differences)
      - [Assertion Mapping Table](#assertion-mapping-table)
      - [Example Transformations](#example-transformations)
        - [Equality Check](#equality-check)
        - [Null Check](#null-check)
        - [Boolean Assertions](#boolean-assertions)
        - [Identity Assertions](#identity-assertions)
        - [NotNull Assertions](#notnull-assertions)
        - [Fail Statements](#fail-statements)
    - [JUnit Assumption Migration](#junit-assumption-migration)
      - [Supported Assumption Methods](#supported-assumption-methods)
      - [Assumption Mapping Table](#assumption-mapping-table)
    - [Notes](#notes)
    - [Limitations](#limitations-3)
    - [Usage](#usage-2)
  - [sandbox_method_reuse](#10-sandbox_method_reuse)
  - [sandbox_xml_cleanup](#11-sandbox_xml_cleanup)
- [Installation](#installation)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [Release Process](#release-process)
- [License](#license)

## Build Instructions

### Prerequisites

**IMPORTANT**: This project (main branch, targeting Eclipse 2025-09) requires **Java 21** or later.

The project uses Tycho 5.0.1 which requires Java 21. Building with Java 17 or earlier will fail with:
```
UnsupportedClassVersionError: ... has been compiled by a more recent version of the Java Runtime (class file version 65.0)
```

Verify your Java version:
```bash
java -version  # Should show Java 21 or later
```

### Building

To build the project, including a WAR file that contains the update site, run:

```bash
mvn -Dinclude=web -Pjacoco verify
```

- The product will be located in `sandbox_product/target`
- The WAR file will be located in `sandbox_web/target`

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

The Eclipse version (SimRel release) used by this project is **not centrally configured**. When updating to a new Eclipse release, you must update the version reference in **multiple files** throughout the repository.

### Files to Update

When migrating to a new Eclipse version, update the following files:

1. **`pom.xml`** (root)
   - Repository URLs in the `<repositories>` section
   - Example: `https://download.eclipse.org/releases/2025-09/`
   - Also update Orbit repository URL: `https://download.eclipse.org/tools/orbit/simrel/orbit-aggregation/2025-09/`

2. **`sandbox_target/eclipse.target`**
   - Primary Eclipse release repository URL in first `<location>` block
   - Example: `<repository location="https://download.eclipse.org/releases/2025-09/"/>`
   - Also update Orbit repository URL

3. **`sandbox_product/category.xml`**
   - Repository reference location
   - Example: `<repository-reference location="https://download.eclipse.org/releases/2025-09/" .../>`

4. **`sandbox_product/sandbox.product`**
   - Repository locations in `<repositories>` section
   - Example: `<repository location="https://download.eclipse.org/releases/2025-09/" .../>`

5. **`sandbox_oomph/sandbox.setup`**
   - P2 repository URL in the version-specific `<setupTask>` block
   - Example: `<repository url="https://download.eclipse.org/releases/2025-09"/>`

### Version Consistency Guidelines

- **Use HTTPS**: All Eclipse download URLs should use `https://` (not `http://`)
- **Use explicit versions**: Prefer explicit version URLs (e.g., `2025-09`) over `latest` for reproducible builds
- **Keep versions aligned**: All files should reference the same Eclipse SimRel version
- **Git URLs**: Use HTTPS for git clone URLs (e.g., `https://github.com/...`, not `git://`)
- **Main branch**: All Oomph setup files should reference the `main` branch, not `master`

### Current Configuration

- **Eclipse Version**: 2025-09
- **Java Version**: 21
- **Tycho Version**: 5.0.1
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

You can install the cleanup plugins into your existing Eclipse installation using the P2 update site:

1. In Eclipse, go to **Help** → **Install New Software...**
2. Click **Add...** and enter the update site URL (see [Installation](#installation) section)
3. Select the desired cleanup features
4. Follow the installation wizard

> **Warning**: Use only with a test Eclipse installation. These plugins are experimental and may affect your IDE stability.

---

## CI Status

### main (2025-09)

[![Java CI with Maven](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml/badge.svg)](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml)  
[![CodeQL](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml/badge.svg)](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml)

### 2022-09

[![Java CI with Maven](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml/badge.svg?branch=2022-09)](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml)  
[![CodeQL](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml/badge.svg?branch=2022-09)](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml)

### 2022-06

[![Java CI with Maven](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml/badge.svg?branch=2022-06)](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml)  
[![CodeQL](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml/badge.svg?branch=2022-06)](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml)

---

## What's Included

### Java Version by Branch

| Branch          | Java Version | Tycho Version |
|-----------------|--------------|---------------|
| `main` (2025-09)| Java 21      | 5.0.1         |
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

Replaces platform-dependent or implicit encoding usage with explicit, safe alternatives using `StandardCharsets.UTF_8` or equivalent constants. Improves code portability and prevents encoding-related bugs across different platforms.

**Key Features:**
- Three cleanup strategies: Prefer UTF-8, Keep Behavior, or Aggregate UTF-8
- Java version-aware transformations (Java 7-21+)
- Supports FileReader, FileWriter, Files methods, Scanner, PrintWriter, and more
- Automatically adds imports and removes unnecessary exceptions

**Quick Example:**
```java
// Before
Reader r = new FileReader(file);
List<String> lines = Files.readAllLines(path);

// After
Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
```

📖 **Full Documentation**: [Plugin README](sandbox_encoding_quickfix/README.md) | [Architecture](sandbox_encoding_quickfix/ARCHITECTURE.md) | [TODO](sandbox_encoding_quickfix/TODO.md)

### 3. `sandbox_extra_search`

Experimental search tool for identifying critical classes when upgrading Eclipse or Java versions.

### 4. `sandbox_usage_view`

Provides a table view of code objects, sorted by name, to detect inconsistent naming that could confuse developers.

### 5. `sandbox_platform_helper`

Simplifies Eclipse Platform `Status` object creation by replacing verbose `new Status(...)` constructor calls with cleaner factory methods (Java 11+ / Eclipse 4.20+) or StatusHelper pattern (Java 8).

**Key Features:**
- Java version-aware transformations
- Reduces boilerplate in Status object creation
- Automatic selection between StatusHelper or factory methods
- Cleaner, more readable code

**Quick Example:**
```java
// Before
IStatus status = new Status(IStatus.ERROR, "plugin.id", "Error message", exception);

// After (Java 11+ / Eclipse 4.20+)
IStatus status = Status.error("Error message", exception);
```

📖 **Full Documentation**: [Plugin README](sandbox_platform_helper/README.md) | [Architecture](sandbox_platform_helper/ARCHITECTURE.md) | [TODO](sandbox_platform_helper/TODO.md)

### 6. `sandbox_tools`

**While-to-For** loop converter — already merged into Eclipse JDT.

### 7. `sandbox_jface_cleanup`

Automates migration from deprecated `SubProgressMonitor` to modern `SubMonitor` API. The cleanup is idempotent and handles variable name collisions.

**Key Features:**
- Transforms `beginTask()` + `SubProgressMonitor` to `SubMonitor.convert()` + `split()`
- Handles style flags and multiple monitor instances
- Idempotent - safe to run multiple times
- Automatic variable name conflict resolution

**Quick Example:**
```java
// Before
monitor.beginTask("Task", 100);
IProgressMonitor sub = new SubProgressMonitor(monitor, 60);

// After
SubMonitor subMonitor = SubMonitor.convert(monitor, "Task", 100);
IProgressMonitor sub = subMonitor.split(60);
```

📖 **Full Documentation**: [Plugin README](sandbox_jface_cleanup/README.md) | [Architecture](sandbox_jface_cleanup/ARCHITECTURE.md) | [TODO](sandbox_jface_cleanup/TODO.md)

### 8. `sandbox_functional_converter`

Converts imperative enhanced for-loops into functional Java 8 Stream pipelines. Transforms traditional loop patterns into more concise and expressive stream operations using `map`, `filter`, `reduce`, `forEach`, and terminal operations like `anyMatch`, `noneMatch`, and `allMatch`.

**Key Features:**
- Simple forEach conversion
- Filter and map operations from if-statements and variable declarations
- Reduce operations for accumulators (sum, product, Math.max, Math.min)
- Match operations from early return patterns
- Java 8+ API support with comprehensive test coverage (34 test cases)

**Quick Example:**
```java
// Before
for (Integer l : list) {
    if (l != null) {
        String s = l.toString();
        System.out.println(s);
    }
}

// After
list.stream()
    .filter(l -> (l != null))
    .map(l -> l.toString())
    .forEachOrdered(s -> {
        System.out.println(s);
    });
```

📖 **Full Documentation**: [Plugin README](sandbox_functional_converter/README.md) | [Architecture](sandbox_functional_converter/ARCHITECTURE.md) | [TODO](sandbox_functional_converter/TODO.md)

### 9. `sandbox_junit`

Automates migration of legacy tests from JUnit 3 and JUnit 4 to JUnit 5 (Jupiter). Transforms test classes, methods, annotations, assertions, and lifecycle hooks to use the modern JUnit 5 API.

**Key Features:**
- JUnit 3 → 5: Remove `extends TestCase`, convert naming conventions to annotations
- JUnit 4 → 5: Update annotations (`@Before` → `@BeforeEach`, `@Ignore` → `@Disabled`)
- Assertion parameter reordering (message-last pattern)
- Lifecycle method transformations
- Rule migration (`@Rule` → `@RegisterExtension`)
- Test suite conversion

**Quick Example:**
```java
// Before (JUnit 3)
public class MyTest extends TestCase {
    protected void setUp() throws Exception {
        super.setUp();
    }
    
    public void testSomething() {
        assertEquals("message", expected, actual);
    }
}

// After (JUnit 5)
public class MyTest {
    @BeforeEach
    void setUp() {
    }
    
    @Test
    void testSomething() {
        assertEquals(expected, actual, "message");
    }
}
```

📖 **Full Documentation**: [Plugin README](sandbox_junit_cleanup/README.md) | [Architecture](sandbox_junit_cleanup/ARCHITECTURE.md) | [TODO](sandbox_junit_cleanup/TODO.md) | [Testing Guide](sandbox_junit_cleanup_test/TESTING.md)

---

### 10. `sandbox_method_reuse`

#### Method Reusability Finder – Code Duplication Detection

The **Method Reusability Finder** is an Eclipse JDT cleanup plugin that analyzes selected methods to identify potentially reusable code patterns across the codebase. It helps developers discover duplicate or similar code that could be refactored to improve code quality and maintainability.

---

#### Purpose

- **Code Duplication Detection**: Identify similar code patterns using both token-based and AST-based analysis
- **Intelligent Matching**: Recognize code similarity even when variable names differ
- **Eclipse Integration**: Seamlessly integrate as a cleanup action in Eclipse JDT
- **Performance**: Efficient analysis that scales to large codebases

---

#### Key Features

##### Similarity Analysis
- **Token-based similarity**: Compares normalized token sequences
- **AST-based similarity**: Compares abstract syntax tree structures
- **Variable name normalization**: Ignores variable name differences
- **Control flow analysis**: Matches similar control structures

##### Inline Code Detection
- Searches method bodies for inline code sequences
- Finds code that matches a target method's body
- Identifies refactoring opportunities within methods

##### Safety Analysis
- Analyzes semantic safety of replacements
- Detects field modifications and side effects
- Checks for complex control flow

---

#### Components

| Component                      | Purpose                                           |
|--------------------------------|---------------------------------------------------|
| `MethodReuseFinder`            | Searches project for similar methods             |
| `MethodSignatureAnalyzer`      | Analyzes and compares method signatures          |
| `CodePatternMatcher`           | AST-based pattern matching                        |
| `InlineCodeSequenceFinder`     | Finds inline code sequences                       |
| `CodeSequenceMatcher`          | Matches statement sequences with normalization    |
| `VariableMapping`              | Tracks variable name mappings                     |
| `MethodCallReplacer`           | Generates method invocation replacement code      |
| `SideEffectAnalyzer`           | Analyzes safety of replacements                   |

---

#### Configuration

Cleanup options are defined in `MYCleanUpConstants`:
- `METHOD_REUSE_CLEANUP` - Enable/disable the cleanup
- `METHOD_REUSE_INLINE_SEQUENCES` - Enable inline code sequence detection

---

#### Usage

This cleanup is available as part of the JDT Clean Up framework:
- **Eclipse UI** → Source → Clean Up
- **Automated build tools** using Eclipse JDT APIs

---

#### Implementation Status

This is a new plugin currently under development. The initial implementation focuses on:
- Basic method similarity detection
- AST-based pattern matching
- Integration with Eclipse cleanup framework

See `sandbox_method_reuse/TODO.md` for pending features and improvements.

---

### 11. `sandbox_xml_cleanup`

#### XML Cleanup – PDE File Optimization

The **XML Cleanup** plugin provides automated refactoring and optimization for PDE-relevant XML files in Eclipse projects. It focuses on reducing file size while maintaining semantic integrity through XSLT transformation, whitespace normalization, and optional indentation.

---

#### Purpose

- Optimize PDE XML configuration files for size and consistency
- Apply secure XSLT transformations with whitespace normalization
- Convert leading spaces to tabs (4 spaces → 1 tab)
- Provide optional indentation control (default: OFF for size reduction)
- Integrate with Eclipse workspace APIs for safe file updates

---

#### Supported XML Types (PDE Files Only)

The plugin **only** processes PDE-relevant XML files:

**Supported File Names:**
- `plugin.xml` - Eclipse plugin manifests
- `feature.xml` - Eclipse feature definitions
- `fragment.xml` - Eclipse fragment manifests

**Supported File Extensions:**
- `*.exsd` - Extension point schema definitions
- `*.xsd` - XML schema definitions

**Supported Locations:**
Files must be in one of these locations:
- **Project root** - Files directly in project folder
- **OSGI-INF** - OSGi declarative services directory
- **META-INF** - Manifest and metadata directory

> **Note**: All other XML files (e.g., `pom.xml`, `build.xml`) are **ignored** to avoid unintended transformations.

---

#### Transformation Process

##### 1. XSLT Transformation
- Uses secure XML processing (external DTD/entities disabled)
- Preserves XML structure, comments, and content
- **Default: `indent="no"`** - Produces compact output for size reduction
- **Optional: `indent="yes"`** - Enabled via `XML_CLEANUP_INDENT` preference

##### 2. Whitespace Normalization
- **Reduce excessive empty lines** - Maximum 2 consecutive empty lines
- **Leading space to tab conversion** - Only at line start (not inline text)
  - Converts groups of 4 leading spaces to 1 tab
  - Preserves remainder spaces (e.g., 5 spaces → 1 tab + 1 space)
  - **Does NOT touch inline text or content nodes**

##### 3. Change Detection
- Only writes file if content actually changed
- Uses Eclipse workspace APIs (`IFile.setContents()`)
- Maintains file history (`IResource.KEEP_HISTORY`)
- Refreshes resource after update

---

#### Configuration

**Default Behavior** (when `XML_CLEANUP` is enabled):
- `indent="no"` - Compact output, no extra whitespace
- Reduces file size by removing unnecessary whitespace
- Converts leading spaces to tabs
- Preserves semantic content

**Optional Behavior** (when `XML_CLEANUP_INDENT` is enabled):
- `indent="yes"` - Minimal indentation applied
- Still converts leading spaces to tabs
- Slightly larger file size but more readable

**Constants** (defined in `MYCleanUpConstants`):
- `XML_CLEANUP` - Enable XML cleanup (default: OFF)
- `XML_CLEANUP_INDENT` - Enable indentation (default: OFF)

---

#### Security Features

The plugin implements secure XML processing:
- External DTD access disabled
- External entity resolution disabled
- DOCTYPE declarations disallowed
- Secure processing mode enabled

---

#### Tab Conversion Rule

Tab conversion is **only** applied to leading whitespace:

✅ **Converted**:
```xml
    <element>  <!-- 4 leading spaces → 1 tab -->
```

❌ **Not Converted**:
```xml
<element attr="value    with    spaces"/>  <!-- Inline spaces preserved -->
```

This ensures that:
- Indentation is normalized to tabs
- XML attribute values are not modified
- Text content spacing is preserved
- Only structural whitespace is affected

---

#### Usage

This cleanup is available as part of the JDT Clean Up framework:
- **Eclipse UI** → Source → Clean Up
- Configure via cleanup preferences: `XML_CLEANUP` and `XML_CLEANUP_INDENT`

---

#### Limitations

1. **PDE Files Only**: Only processes plugin.xml, feature.xml, fragment.xml, *.exsd, *.xsd
2. **Location Restricted**: Files must be in project root, OSGI-INF, or META-INF
3. **Leading Tabs Only**: Tab conversion only applies to leading whitespace, not inline content
4. **No Schema Validation**: Doesn't validate against XML schemas (relies on Eclipse PDE validation)

---

#### Test Coverage

The `sandbox_xml_cleanup_test` module contains comprehensive test cases for:
- Size reduction verification
- Semantic equality (using XMLUnit, ignoring whitespace)
- Idempotency (second run produces no change)
- Leading-indent-only tab conversion
- PDE file filtering accuracy

---

## Installation

You can use the P2 update site:

```
https://github.com/carstenartur/sandbox/raw/main
```

> **Warning:**  
> Use only with a fresh Eclipse installation that can be discarded after testing.  
> It may break your setup. Don’t say you weren’t warned...
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

## Release Process

This section describes how to create and publish a new release of the Sandbox project.

### Prerequisites

- Write access to the repository
- Local environment with Java 21 and Maven configured
- All tests passing on the `main` branch

### Release Steps

#### 1. Update Version Numbers

Update the version in all `pom.xml` files from `X.Y.Z-SNAPSHOT` to `X.Y.Z`:

```bash
# Example: Updating from 1.2.2-SNAPSHOT to 1.2.2
mvn versions:set -DnewVersion=1.2.2
mvn versions:commit
```

#### 2. Verify the Build

Ensure all tests pass and the build completes successfully:

```bash
# Run full build with tests and coverage
mvn clean verify -Pjacoco

# Build with WAR file
mvn -Dinclude=web -Pjacoco verify
```

#### 3. Commit Version Changes

Commit the version updates:

```bash
git add .
git commit -m "Release version 1.2.2"
git push origin main
```

#### 4. Create a Git Tag

Tag the release commit:

```bash
git tag -a v1.2.2 -m "Release version 1.2.2"
git push origin v1.2.2
```

#### 5. Create GitHub Release

1. Go to the [GitHub Releases page](https://github.com/carstenartur/sandbox/releases)
2. Click **"Draft a new release"**
3. Select the tag you just created (e.g., `v1.2.2`)
4. Set the release title (e.g., `Release 1.2.2`)
5. Add release notes describing:
   - New features
   - Bug fixes
   - Breaking changes (if any)
   - Known issues
6. Click **"Publish release"**

#### 6. Automated Publishing

When a GitHub release is created, the `maven-publish.yml` workflow automatically:
- Builds the project with Maven
- Publishes artifacts to GitHub Packages
- Makes the P2 update site available

#### 7. Prepare for Next Development Iteration

Update versions to the next SNAPSHOT version:

```bash
# Example: Updating to 1.2.3-SNAPSHOT for next development cycle
mvn versions:set -DnewVersion=1.2.3-SNAPSHOT
mvn versions:commit

git add .
git commit -m "Prepare for next development iteration: 1.2.3-SNAPSHOT"
git push origin main
```

### Version Numbering

This project follows [Semantic Versioning](https://semver.org/):

- **MAJOR** version (X.0.0): Incompatible API changes
- **MINOR** version (0.X.0): New functionality in a backward-compatible manner
- **PATCH** version (0.0.X): Backward-compatible bug fixes

### Release Artifacts

Each release produces:
- **Eclipse Product**: Installable Eclipse IDE with bundled plugins (`sandbox_product/target`)
- **P2 Update Site**: For installing plugins into existing Eclipse (`sandbox_web/target`)
- **WAR File**: Web-deployable update site
- **Maven Artifacts**: Published to GitHub Packages

### Troubleshooting

**Build fails during release:**
- Ensure all tests pass locally: `mvn clean verify -Pjacoco`
- Check Java version: `java -version` (must be 21+)
- Verify Maven version: `mvn -version` (3.9.x recommended)

**GitHub Actions workflow fails:**
- Check workflow run logs in the Actions tab
- Ensure the tag was pushed correctly: `git ls-remote --tags origin`
- Verify permissions for GitHub Packages publishing

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
