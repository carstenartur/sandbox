# Sandbox Project

A collection of experimental Eclipse JDT (Java Development Tools) cleanup plugins and tools. This repository demonstrates how to build custom JDT cleanups, quick fixes, and related tooling for Eclipse-based Java development.

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
  - [sandbox_functional_converter](#7-sandbox_functional_converter)
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
  - [sandbox_junit](#8-sandbox_junit)
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
- [Installation](#installation)

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

#### Encoding Cleanup – Replace Platform Encoding with Explicit Charset

The **Encoding Cleanup** replaces platform-dependent or implicit encoding usage with explicit, safe alternatives using `StandardCharsets.UTF_8` or equivalent constants.  
It supports multiple strategies and is Java-version-aware.

---

#### Based on Test Coverage

The cleanup logic is tested and verified by the following test files:

- `Java22/ExplicitEncodingPatterns.java`
- `Java10/ExplicitEncodingPatternsPreferUTF8.java`
- `Java10/ExplicitEncodingPatternsKeepBehavior.java`
- `Java10/ExplicitEncodingPatternsAggregateUTF8.java`
- `Java10/ExplicitEncodingCleanUpTest.java`

---

#### Cleanup Strategies

| Strategy               | Description                                                                 |
|------------------------|-----------------------------------------------------------------------------|
| **Prefer UTF-8**       | Replace `"UTF-8"` and platform-default encodings with `StandardCharsets.UTF_8` |
| **Keep Behavior**      | Only fix cases where behavior is guaranteed not to change (e.g. "UTF-8" literal) |
| **Aggregate UTF-8**    | Replace all `"UTF-8"` usage with a shared `static final Charset UTF_8` field |

---

#### Java Version Awareness

| Java Version | Supported Transformations                                                             |
|--------------|----------------------------------------------------------------------------------------|
| Java < 7     | Cleanup is **disabled** – `StandardCharsets` not available                            |
| Java 7–10    | Basic replacements using `StandardCharsets.UTF_8`, stream wrapping, and exception removal |
| Java 11+     | Adds support for `Files.newBufferedReader(path, charset)` and `Channels.newReader(...)` |
| Java 21+     | Enables usage of `Files.readString(...)` and `Files.writeString(...)` with charset     |

---

#### Supported Classes and APIs

The cleanup covers a wide range of encoding-sensitive classes:

| Class / API                          | Encoding Behavior                      | Cleanup Action                                          |
|-------------------------------------|----------------------------------------|---------------------------------------------------------|
| `OutputStreamWriter`                | Requires explicit encoding             | Replace `"UTF-8"` or add missing `StandardCharsets.UTF_8` |
| `InputStreamReader`                | Same                                   | Add `StandardCharsets.UTF_8` where missing              |
| `FileReader` / `FileWriter`        | Implicit platform encoding             | Replace with stream + `InputStreamReader` + charset     |
| `Scanner(InputStream)`             | Platform encoding                      | Add charset constructor if available (Java 10+)         |
| `PrintWriter(OutputStream)`        | Platform encoding                      | Use new constructor with charset if possible            |
| `Files.newBufferedReader(Path)`    | Platform encoding by default           | Use overload with charset                              |
| `Files.newBufferedWriter(Path)`    | Same                                   | Use overload with charset                              |
| `Files.readAllLines(Path)`         | Platform encoding                      | Use `readAllLines(path, charset)` if available          |
| `Files.readString(Path)`           | Available since Java 11 / 21+          | Use with charset overload                               |
| `Charset.forName("UTF-8")`         | Literal resolution                     | Replace with `StandardCharsets.UTF_8`                   |
| `Channels.newReader(...)`          | Charset overload available since Java 11 | Use it when applicable                                  |
| `InputSource.setEncoding(String)`  | Not a stream – SAX API                 | Replace string literal `"UTF-8"` with constant if possible |

---

#### Examples

##### Example: FileReader Replacement

**Before:**
```java
Reader r = new FileReader(file);
```

**After:**
```java
Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
```

---

##### Example: Channels.newReader (Java 10+)

**Before:**
```java
Reader r = Channels.newReader(channel, "UTF-8");
```

**After:**
```java
Reader r = Channels.newReader(channel, StandardCharsets.UTF_8);
```

---

##### Example: Files.readAllLines (Java 10+)

**Before:**
```java
List<String> lines = Files.readAllLines(path);
```

**After:**
```java
List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
```

---

##### Example: Scanner (Java 10+)

**Before:**
```java
Scanner scanner = new Scanner(inputStream);
```

**After:**
```java
Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8);
```

---

##### Example: SAX InputSource

**Before:**
```java
InputSource source = new InputSource();
source.setEncoding("UTF-8");
```

**After:**
```java
source.setEncoding(StandardCharsets.UTF_8.name());
```

---

#### Aggregation Mode Example

If `Aggregate UTF-8` mode is enabled:

```java
private static final Charset UTF_8 = StandardCharsets.UTF_8;

Reader r = new InputStreamReader(in, UTF_8);
```

All uses of `StandardCharsets.UTF_8` or `"UTF-8"` will be redirected to `UTF_8`.

---

#### Additional Fixes

- Adds required imports:
  - `import java.nio.charset.StandardCharsets;`
  - `import java.nio.charset.Charset;` (if aggregation is used)
- Removes:
  - `throws UnsupportedEncodingException` if replaced by standard charset
  - `"UTF-8"` string constants if inlined

---

#### Cleanup Mode × Java Version Matrix

| Java Version | Prefer UTF-8 | Keep Behavior | Aggregate UTF-8 | Files.readString / Channels |
|--------------|---------------|----------------|------------------|------------------------------|
| Java 7       | ✅             | ✅              | ✅                | ❌                           |
| Java 10      | ✅             | ✅              | ✅                | ❌                           |
| Java 11–20   | ✅             | ✅              | ✅                | ✅                           |
| Java 21+     | ✅             | ✅              | Optional         | ✅ (modern API encouraged)   |

---

#### Usage

- Via **Eclipse Clean Up...** under Encoding category
- Via **JDT Batch tooling**, with properties:
  - `encoding.strategy = PREFER_UTF8 | KEEP | AGGREGATE`
  - `aggregate.charset.name = UTF_8`
  - `min.java.version = 7 | 10 | 11 | 21`

##### Encoding Cleanup – Strategy Variants

The **Encoding Cleanup** supports multiple strategies depending on the selected configuration.
Each strategy affects which code constructs are transformed and how safely defaults are preserved.

---

##### Strategy: Prefer UTF-8

Replaces all literal `"UTF-8"` occurrences and platform-default encodings with `StandardCharsets.UTF_8`.

###### Example Transformations

**Before:**
```java
new InputStreamReader(in);
new FileReader(file);
Charset.forName("UTF-8");
```

**After:**
```java
new InputStreamReader(in, StandardCharsets.UTF_8);
new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
StandardCharsets.UTF_8;
```

---

##### Strategy: Keep Behavior

Only transforms code if `"UTF-8"` is explicitly used – avoids changing platform-default behaviors.

###### Example Transformations

**Before:**
```java
Charset charset = Charset.forName("UTF-8");
new InputStreamReader(in);
```

**After:**
```java
Charset charset = StandardCharsets.UTF_8;
new InputStreamReader(in); // left unchanged
```

---

##### Strategy: Aggregate UTF-8

Replaces all `"UTF-8"` usage and `StandardCharsets.UTF_8` with a class-level constant.

###### Example Transformations

**Before:**
```java
new InputStreamReader(in, StandardCharsets.UTF_8);
new FileReader(file);
```

**After:**
```java
private static final Charset UTF_8 = StandardCharsets.UTF_8;

new InputStreamReader(in, UTF_8);
new InputStreamReader(new FileInputStream(file), UTF_8);
```

Also supports dynamic replacement of:
- `Charset.forName("UTF-8")`
- `"UTF-8"` literals passed to methods like `setEncoding(...)`

---

##### Summary Table

| Strategy        | Platform Default Handling | Replaces `"UTF-8"` | Aggregates Constant |
|----------------|----------------------------|---------------------|----------------------|
| Prefer UTF-8   | Yes                        | Yes                 | No                   |
| Keep Behavior  | No                         | Yes (only explicit) | No                   |
| Aggregate UTF-8| Yes                        | Yes                 | Yes (`UTF_8`)        |

> These strategies are controlled via cleanup preferences:  
> `encoding.strategy = PREFER_UTF8 | KEEP | AGGREGATE`

##### Charset Literal Replacement Table

The cleanup recognizes common charset string literals and replaces them with the appropriate constants from `StandardCharsets`:

| String Literal     | Replacement Constant              |
|--------------------|------------------------------------|
| `"UTF-8"`          | `StandardCharsets.UTF_8`           |
| `"US-ASCII"`       | `StandardCharsets.US_ASCII`        |
| `"ISO-8859-1"`     | `StandardCharsets.ISO_8859_1`      |
| `"UTF-16"`         | `StandardCharsets.UTF_16`          |
| `"UTF-16BE"`       | `StandardCharsets.UTF_16BE`        |
| `"UTF-16LE"`       | `StandardCharsets.UTF_16LE`        |

---

#### Limitations

- Dynamic encodings (read from config or variables) are left untouched
- Aggregation introduces class-level fields (may require conflict checks)
- Cleanup logic avoids modifying non-I/O encoding usages

---

This documentation is based on test-driven implementations in the `sandbox_encoding_quickfix_test` module and reflects support for modern and legacy encoding cleanup across Java 7 to 22.

> **Reference**: [JEP 400: UTF-8 by Default](https://openjdk.java.net/jeps/400) – Partial implementation to highlight platform encoding usage via API changes.

### 3. `sandbox_extra_search`

Experimental search tool for identifying critical classes when upgrading Eclipse or Java versions.

### 4. `sandbox_usage_view`

Provides a table view of code objects, sorted by name, to detect inconsistent naming that could confuse developers.

### 5. `sandbox_platform_helper`

#### Platform Status Cleanup – Simplification of `new Status(...)` Calls

This cleanup modernizes the usage of `org.eclipse.core.runtime.Status` in Eclipse-based projects by replacing verbose constructor calls with cleaner alternatives.  
It supports two strategies, depending on the Java and Eclipse platform version:

- **Java 8 / Eclipse < 4.20**: Use a project-specific `StatusHelper` class.
- **Java 11+ / Eclipse ≥ 4.20**: Use the static factory methods `Status.error(...)`, `Status.warning(...)`, `Status.info(...)`, etc.

The cleanup logic is based on:

- [`Java8CleanUpTest.java`](../sandbox_platform_helper_test/src/org/sandbox/jdt/ui/tests/quickfix/Java8CleanUpTest.java)
- [`Java9CleanUpTest.java`](../sandbox_platform_helper_test/src/org/sandbox/jdt/ui/tests/quickfix/Java9CleanUpTest.java)

---

#### Motivation

Constructing `IStatus` instances via `new Status(...)` is verbose and error-prone. This cleanup provides more readable alternatives by:

- Reducing boilerplate code
- Unifying the way status objects are created
- Encouraging use of centralized helpers or platform-provided factories

---

#### Before/After Comparison

| Case Type               | Legacy Code                                             | Cleanup Result (Java 8)                      | Cleanup Result (Java 11 / Eclipse ≥ 4.20)   |
|-------------------------|---------------------------------------------------------|----------------------------------------------|---------------------------------------------|
| Basic warning           | `new Status(IStatus.WARNING, id, msg)`                 | *(unchanged – concise)*                      | *(unchanged – concise)*                     |
| With 4 arguments        | `new Status(IStatus.WARNING, id, msg, null)`           | `StatusHelper.warning(id, msg)`             | `Status.warning(msg)`                       |
| With exception          | `new Status(IStatus.ERROR, id, msg, e)`                | `StatusHelper.error(id, msg, e)`            | `Status.error(msg, e)`                      |
| INFO with 4 args        | `new Status(IStatus.INFO, id, code, msg, null)`        | `StatusHelper.info(id, msg)`                | `Status.info(msg)`                          |
| OK status               | `new Status(IStatus.OK, id, "done")`                   | *(unchanged – already minimal)*             | *(unchanged – already minimal)*             |

---

#### Examples

##### Java 8: With `StatusHelper`

**Before:**
```java
IStatus status = new Status(IStatus.WARNING, "plugin.id", "Something happened", null);
```

**After:**
```java
IStatus status = StatusHelper.warning("plugin.id", "Something happened");
```

---

##### Java 11+: With `Status.warning(...)`

**Before:**
```java
IStatus status = new Status(IStatus.WARNING, "plugin.id", IStatus.OK, "Something happened", null);
```

**After:**
```java
IStatus status = Status.warning("Something happened");
```

---

##### With Exception

**Before:**
```java
IStatus status = new Status(IStatus.ERROR, "plugin.id", "Something bad happened", exception);
```

**After (Java 8):**
```java
IStatus status = StatusHelper.error("plugin.id", "Something bad happened", exception);
```

**After (Java 11+):**
```java
IStatus status = Status.error("Something bad happened", exception);
```

---

#### Cleanup Strategy Selection

| Target Platform        | Strategy Used        |
|------------------------|----------------------|
| Eclipse < 4.20         | Insert `StatusHelper` method calls |
| Eclipse 4.20 or newer  | Replace with `Status.error(...)`, `Status.warning(...)`, etc. |

---

#### Requirements

- For **Status factory methods**: Eclipse Platform 4.20+ and Java 11+
- For **StatusHelper**: Either implement your own helper, or use a generated version
- Static import of `org.eclipse.core.runtime.Status` is recommended

---

#### Usage

This cleanup is available as part of the JDT Clean Up framework. It can be run via:

- **Eclipse UI** → Source → Clean Up
- **Automated build tools** using Eclipse JDT APIs or Maven plugins

---

#### Limitations

- Only applies to direct calls to the `Status` constructor
- Plugin ID handling is simplified – if it must be retained dynamically, manual changes may be needed
- Custom `IStatus` subclasses or complex logic are not handled

---

This documentation is based on the cleanup logic and test cases in `Java8CleanUpTest.java` and `Java9CleanUpTest.java`. Manual review is advised for edge cases or plugin-specific conventions.

> **Reference**: [Eclipse 4.20 Platform ISV – Simpler Status Creation](https://www.eclipse.org/eclipse/news/4.20/platform_isv.php#simpler-status-creation) – PoC for a QuickFix to migrate code based on new platform features.

### 6. `sandbox_tools`

**While-to-For** loop converter — already merged into Eclipse JDT.

### 7. `sandbox_jface_cleanup`

#### JFace Cleanup – SubProgressMonitor to SubMonitor Migration

The **JFace Cleanup** automates the migration from the deprecated `SubProgressMonitor` API to the modern `SubMonitor` API introduced in Eclipse 3.4.

---

#### Purpose

`SubProgressMonitor` has been deprecated in favor of `SubMonitor`, which provides:
- **Simpler API**: Fluent interface with method chaining
- **Better null safety**: Built-in null handling for progress monitors
- **Improved work allocation**: More intuitive split() semantics
- **Idempotent transformations**: The cleanup can be run multiple times safely
- **Forward compatibility**: SubMonitor is the recommended API since Eclipse 3.4+

This cleanup is designed to be **idempotent** – already migrated code will not be transformed again, ensuring safe repeated application.

---

#### Migration Pattern

The cleanup transforms the classic `beginTask()` + `SubProgressMonitor` pattern into the modern `SubMonitor.convert()` + `split()` pattern.

##### Basic Transformation

**Before:**
```java
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

public void doWork(IProgressMonitor monitor) {
    monitor.beginTask("Main Task", 100);
    IProgressMonitor sub1 = new SubProgressMonitor(monitor, 60);
    IProgressMonitor sub2 = new SubProgressMonitor(monitor, 40);
}
```

**After:**
```java
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;

public void doWork(IProgressMonitor monitor) {
    SubMonitor subMonitor = SubMonitor.convert(monitor, "Main Task", 100);
    IProgressMonitor sub1 = subMonitor.split(60);
    IProgressMonitor sub2 = subMonitor.split(40);
}
```

##### With Style Flags

The cleanup also handles `SubProgressMonitor` constructor calls with style flags:

**Before:**
```java
IProgressMonitor sub = new SubProgressMonitor(monitor, 50, SubProgressMonitor.PREPEND_MAIN_LABEL_TO_SUBTASK);
```

**After:**
```java
SubMonitor subMonitor = SubMonitor.convert(monitor, "Task", 100);
IProgressMonitor sub = subMonitor.split(50, SubMonitor.PREPEND_MAIN_LABEL_TO_SUBTASK);
```

---

#### Unique Variable Name Handling

If the scope already contains a variable named `subMonitor`, the cleanup generates a unique name:

**Before:**
```java
public void doWork(IProgressMonitor monitor) {
    String subMonitor = "test";  // Name collision
    monitor.beginTask("Task", 100);
    IProgressMonitor sub = new SubProgressMonitor(monitor, 50);
}
```

**After:**
```java
public void doWork(IProgressMonitor monitor) {
    String subMonitor = "test";
    SubMonitor subMonitor2 = SubMonitor.convert(monitor, "Task", 100);
    IProgressMonitor sub = subMonitor2.split(50);
}
```

---

#### Idempotence

The cleanup is **idempotent** – code that has already been migrated to `SubMonitor` will not be modified:

**Input (Already Migrated):**
```java
public void doWork(IProgressMonitor monitor) {
    SubMonitor subMonitor = SubMonitor.convert(monitor, "Task", 100);
    IProgressMonitor sub = subMonitor.split(50);
    IProgressMonitor sub2 = subMonitor.split(30);
}
```

**Output:**
```java
// No changes - code is already using SubMonitor pattern
public void doWork(IProgressMonitor monitor) {
    SubMonitor subMonitor = SubMonitor.convert(monitor, "Task", 100);
    IProgressMonitor sub = subMonitor.split(50);
    IProgressMonitor sub2 = subMonitor.split(30);
}
```

---

#### Official Eclipse Documentation

- **SubMonitor API**: [SubMonitor JavaDoc](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/api/org/eclipse/core/runtime/SubMonitor.html)
- **SubProgressMonitor (Deprecated)**: [SubProgressMonitor JavaDoc](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/api/org/eclipse/core/runtime/SubProgressMonitor.html)
- **Eclipse 3.4 Migration Guide**: SubMonitor was introduced in Eclipse 3.4 as the preferred way to handle progress monitors

---

#### Requirements

- **Eclipse Version**: 3.4+ (for `SubMonitor` API availability)
- **Java Version**: Compatible with Java 8+

---

#### Cleanup Name & Activation

| Eclipse Cleanup ID                   | Value                       |
|--------------------------------------|-----------------------------|
| `MYCleanUpConstants.JFACE_CLEANUP`   | `true` (enable this feature)|

**Usage:**
- Via **Eclipse Clean Up...** under the JFace category
- Via **Save Actions** in Eclipse preferences
- Via **JDT Batch tooling**

---

#### Limitations

- Only transforms code that follows the standard pattern of `monitor.beginTask()` followed by `new SubProgressMonitor()`
- Does not handle cases where monitors are passed through multiple layers without the beginTask call
- Name collision resolution uses simple numeric suffixes (subMonitor2, subMonitor3, etc.)

---

#### Test Coverage

The cleanup is thoroughly tested in:
- `sandbox_jface_cleanup_test/src/org/sandbox/jdt/ui/tests/quickfix/Java8CleanUpTest.java`

Test cases cover:
- Basic transformation patterns
- Multiple SubProgressMonitor instances per method
- Style flags (2-arg and 3-arg constructors)
- Variable name collisions
- Idempotence verification
- Mixed scenarios (some methods converted, others not)
- Nested classes and inner classes
- Import handling when SubProgressMonitor and SubMonitor imports coexist

---

### 8. `sandbox_functional_converter`

#### Functional Converter Cleanup – Transform Imperative Loops into Functional Java 8 Streams

This cleanup modernizes imperative Java loop constructs by transforming them into functional-style equivalents using Java 8 Streams, `map`, `filter`, `reduce`, and `forEach`.

> **📐 Architecture Documentation**: See [ARCHITECTURE.md](sandbox_functional_converter/ARCHITECTURE.md) for detailed implementation details, design patterns, and internal components.

---

#### Source and Test Basis

This cleanup is fully tested in:

- `sandbox_functional_converter_test/src/org/sandbox/jdt/ui/tests/quickfix/Java8CleanUpTest.java`

The test class defines:

- **25 enabled test cases** covering fully supported loop transformation patterns
- A list of `@Disabled` scenarios representing future features and unsupported patterns
- A set of `@ValueSource` cases where no transformation should be applied (edge cases)

---

#### Supported Transformations

The cleanup currently supports the following patterns:

| Pattern                                 | Transformed To                                      |
|----------------------------------------|-----------------------------------------------------|
| Simple enhanced for-loops              | `list.forEach(...)` or `list.stream().forEach(...)` |
| Mapping inside loops                   | `.stream().map(...)`                                |
| Filtering via `if` or `continue`       | `.stream().filter(...)`                             |
| Null safety checks                     | `.filter(l -> l != null).map(...)`                  |
| Reductions (sum/counter)               | `.stream().map(...).reduce(...)`                    |
| **MAX/MIN reductions**                 | `.reduce(init, Math::max)` or `.reduce(init, Math::min)` |
| `String` concatenation in loops        | `.reduce(..., String::concat)`                      |
| Conditional early `return true`        | `.anyMatch(...)`                                    |
| Conditional early `return false`       | `.noneMatch(...)`                                   |
| **Conditional check all valid**        | `.allMatch(...)`                                    |
| Method calls inside mapping/filtering  | `map(x -> method(x))`, `filter(...)`                |
| Combined `filter`, `map`, `forEach`    | Chained stream transformations                      |
| **Nested conditionals**                | Multiple `.filter(...)` operations                  |
| Increment/decrement reducers           | `.map(_item -> 1).reduce(0, Integer::sum)`          |
| Compound assignment reducers           | `.map(expr).reduce(init, operator)`                 |

**Enabled Test Cases** (25 total):
- `SIMPLECONVERT`, `CHAININGMAP`, `ChainingFilterMapForEachConvert`
- `SmoothLongerChaining`, `MergingOperations`, `BeautificationWorks`, `BeautificationWorks2`
- `NonFilteringIfChaining`, `ContinuingIfFilterSingleStatement`
- `SimpleReducer`, `ChainedReducer`, `IncrementReducer`, `AccumulatingMapReduce`
- `DOUBLEINCREMENTREDUCER`, `DecrementingReducer`, `ChainedReducerWithMerging`, `StringConcat`
- `ChainedAnyMatch`, `ChainedNoneMatch`
- `NoNeededVariablesMerging`, `SomeChainingWithNoNeededVar`
- **`MaxReducer`, `MinReducer`, `MaxWithExpression`, `MinWithExpression`**
- **`FilteredMaxReduction`, `ChainedMapWithMinReduction`, `ComplexFilterMapMaxReduction`**
- **`ContinueWithMapAndForEach`**
- **`SimpleAllMatch`, `AllMatchWithNullCheck`, `ChainedAllMatch`**
- **`NestedFilterCombination`**

---

#### Examples

##### Simple forEach Conversion
**Before:**
```java
for (Integer l : list) {
    System.out.println(l);
}
```

**After:**
```java
list.forEach(l -> System.out.println(l));
```

---

##### Filter + Map + forEach Chain
**Before:**
```java
for (Integer l : list) {
    if (l != null) {
        String s = l.toString();
        System.out.println(s);
    }
}
```

**After:**
```java
list.stream()
    .filter(l -> (l != null))
    .map(l -> l.toString())
    .forEachOrdered(s -> {
        System.out.println(s);
    });
```

---

##### Null Safety with Objects::nonNull
**Before:**
```java
for (Integer l : list) {
    if (l == null) {
        continue;
    }
    String s = l.toString();
    System.out.println(s);
}
```

**After:**
```java
list.stream()
    .filter(l -> !(l == null))
    .map(l -> l.toString())
    .forEachOrdered(s -> {
        System.out.println(s);
    });
```

---

##### AnyMatch Pattern (Early Return)
**Before:**
```java
for (Integer l : list) {
    String s = l.toString();
    Object o = foo(s);
    if (o == null)
        return true;
}
return false;
```

**After:**
```java
if (list.stream()
        .map(l -> l.toString())
        .map(s -> foo(s))
        .anyMatch(o -> (o == null))) {
    return true;
}
return false;
```

---

##### AllMatch Pattern (Check All Valid)
**Before:**
```java
for (String item : items) {
    if (!item.startsWith("valid")) {
        return false;
    }
}
return true;
```

**After:**
```java
if (!items.stream().allMatch(item -> item.startsWith("valid"))) {
    return false;
}
return true;
```

---

##### MAX/MIN Reduction
**Before:**
```java
int max = Integer.MIN_VALUE;
for (Integer num : numbers) {
    max = Math.max(max, num);
}
```

**After:**
```java
int max = Integer.MIN_VALUE;
max = numbers.stream().reduce(max, Math::max);
```

Similarly for `Math.min()` → `.reduce(min, Math::min)`

---

##### MAX/MIN with Expression Mapping
**Before:**
```java
int maxLen = 0;
for (String str : strings) {
    maxLen = Math.max(maxLen, str.length());
}
```

**After:**
```java
int maxLen = 0;
maxLen = strings.stream()
    .map(str -> str.length())
    .reduce(maxLen, Math::max);
```

---

##### Nested Conditional Filters
**Before:**
```java
for (String item : items) {
    if (item != null) {
        if (item.length() > 5) {
            System.out.println(item);
        }
    }
}
```

**After:**
```java
items.stream()
    .filter(item -> (item != null))
    .filter(item -> (item.length() > 5))
    .forEachOrdered(item -> {
        System.out.println(item);
    });
```

---

#### Reductions (Accumulators)

##### Increment Counter
**Before:**
```java
int count = 0;
for (String s : list) {
    count += 1;
}
```

**After:**
```java
int count = list.stream()
    .map(_item -> 1)
    .reduce(0, Integer::sum);
```

---

##### Mapped Reduction
**Before:**
```java
int sum = 0;
for (Integer l : list) {
    sum += foo(l);
}
```

**After:**
```java
int sum = list.stream()
    .map(l -> foo(l))
    .reduce(0, Integer::sum);
```

Also supported:

- **Decrementing**: `i -= 1` → `.reduce(i, (a, b) -> a - b)`
- **Type-aware literals**: `1` for int, `1L` for long, `1.0` for double, `1.0f` for float
- **String concatenation**: `.reduce("", String::concat)`

---

#### Not Yet Supported (Disabled Tests)

The following patterns are currently **not supported** and are marked `@Disabled` in the test suite:

| Pattern Description                                 | Reason / Required Feature                          |
|-----------------------------------------------------|-----------------------------------------------------|
| `Map.put(...)` inside loop                          | Needs `Collectors.toMap(...)` support               |
| Early `break` inside loop body                      | Requires stream short-circuit modeling (`findFirst()`) |
| Labeled `continue` or `break` (`label:`)            | Not expressible via Stream API                     |
| Complex `if-else-return` branches                   | Requires flow graph and branching preservation      |
| `throw` inside loop                                 | Non-convertible – not compatible with Stream flow  |
| Multiple accumulators in one loop                   | State mutation not easily transferable              |

These patterns are intentionally **excluded from transformation** to maintain semantic correctness and safety.

---

#### Ignored Cases – No Cleanup Triggered

The cleanup **does not modify** code in the following edge cases (validated by `@ValueSource` tests):

- Non-loop constructs
- Loops over arrays instead of `List` or `Iterable`
- Loops with early `return`, `throw`, or labeled `continue`
- Loops mixing multiple mutable accumulators
- Loops with side effects that cannot be safely preserved

---

#### Java Version Compatibility

| API Used                      | Requires Java |
|-------------------------------|---------------|
| `Stream`, `map`, `filter`     | Java 8+       |
| `forEach`, `forEachOrdered`   | Java 8+       |
| `anyMatch`, `noneMatch`       | Java 8+       |
| `reduce`                      | Java 8+       |
| `Collectors.toList()`         | Java 8+       |

This cleanup is designed for **Java 8+** projects and uses only APIs available since Java 8.

---

#### Cleanup Name & Activation

| Eclipse Cleanup ID                          | Value                       |
|---------------------------------------------|-----------------------------|
| `MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP` | `true` (enable this feature) |

**Usage:**
- Via **Eclipse Clean Up...** under the appropriate cleanup category
- Via **JDT Batch tooling** or **Save Actions**

---

#### Limitations

- Does not preserve external loop-scoped variables (e.g., index tracking, multiple accumulators)
- Cannot convert control structures with `return`, `break`, `continue label`, or `throw`
- Does not support loops producing `Map<K,V>` outputs or grouping patterns (future feature)
- Does not merge consecutive filters/maps (could be optimized in future versions)

---

#### Summary

The Functional Converter Cleanup:

- **Applies safe and proven transformations** across 21 tested patterns
- **Targets common loop structures** found in legacy codebases
- **Modernizes Java 5/6/7-style loops** to Java 8 stream-based idioms
- **Uses an extensive test suite** for coverage and correctness
- **Maintains semantic safety** by excluding complex patterns

---

**Further Reading:**
- **Implementation Details**: [ARCHITECTURE.md](sandbox_functional_converter/ARCHITECTURE.md) – In-depth architecture documentation
- **Test Coverage**: `Java8CleanUpTest.java` in the `sandbox_functional_converter_test` module
- **Wiki**: [Functional Converter](https://github.com/carstenartur/sandbox/wiki/Functional-Converter) – Converts `Iterator` loops to functional loops

### 9. `sandbox_junit`

#### JUnit Cleanup – Feature Overview

The **JUnit Cleanup** tool automates the migration of legacy tests from **JUnit 3** and **JUnit 4** to **JUnit 5**.  
It is based on verified transformations from the following test files:

- `JUnit3CleanupCases.java`
- `JUnitCleanupCases.java`

---

#### Migration Summary

The cleanup handles a comprehensive set of JUnit 3 and JUnit 4 constructs, including:

- **Class structure**: `extends TestCase`, test method naming conventions
- **Lifecycle methods**: Setup, teardown, class-level initialization
- **Annotations**: Test markers, lifecycle annotations, ignore/disable markers
- **Assertions**: All JUnit assertion methods with parameter reordering
- **Assumptions**: Precondition checking methods
- **Test suites**: Suite runners and configuration
- **Rules**: `@Rule`, `@ClassRule`, including `TemporaryFolder`, `TestName`, `ExternalResource`

---

#### JUnit 3 Classes and Methods

The cleanup tool handles all major JUnit 3 constructs used in legacy test codebases.

##### JUnit 3 Migration Summary Table

| JUnit 3 Construct                 | Description                           | JUnit 5 Equivalent                         |
|----------------------------------|---------------------------------------|--------------------------------------------|
| `junit.framework.TestCase`       | Base class for tests                  | (removed) – no base class needed           |
| `extends TestCase`               | Class inheritance                     | (removed) – use annotations instead        |
| `public void testXxx()`          | Test method naming convention         | `@Test void xxx()` – descriptive names     |
| `protected void setUp()`         | Setup before each test                | `@BeforeEach void setUp()`                 |
| `protected void tearDown()`      | Cleanup after each test               | `@AfterEach void tearDown()`               |
| `public static Test suite()`     | Test suite definition                 | `@TestMethodOrder` + `@Order` annotations  |
| `TestSuite.addTest(...)`         | Adding tests to suite                 | Individual `@Test` methods with ordering   |
| `junit.framework.Assert.*`       | Assertion methods                     | `org.junit.jupiter.api.Assertions.*`       |

---

##### Class Structure Transformations

The cleanup removes the `extends TestCase` inheritance and eliminates the need for JUnit 3's base class.

**Before:**
```java
import junit.framework.TestCase;

public class MyTest extends TestCase {
    public MyTest(String name) {
        super(name);
    }
}
```

**After:**
```java
import org.junit.jupiter.api.Test;

public class MyTest {
    // Constructor removed - no longer needed
}
```

**Changes Applied:**
- Remove `extends TestCase` from class declaration
- Remove constructor that calls `super(name)`
- Remove `import junit.framework.TestCase`
- Add appropriate JUnit 5 imports

---

##### Test Method Transformations

JUnit 3 uses naming conventions (`testXxx`) to identify test methods. JUnit 5 uses the `@Test` annotation.

**Before:**
```java
public void testBasicAssertions() {
    assertEquals("Values should match", 42, 42);
    assertTrue("Condition should be true", true);
}
```

**After:**
```java
@Test
@Order(1)
public void testBasicAssertions() {
    assertEquals(42, 42, "Values should match");
    assertTrue(true, "Condition should be true");
}
```

**Changes Applied:**
- Add `@Test` annotation
- Add `@Order` annotation if part of a suite (maintains test execution order)
- Reorder assertion parameters (message moves to last position)
- Optionally rename to more descriptive names (removing `test` prefix)

---

##### Setup and Teardown Methods

JUnit 3 uses method name conventions for setup and teardown. JUnit 5 uses annotations.

**Before:**
```java
@Override
protected void setUp() throws Exception {
    // Setup before each test
}

@Override
protected void tearDown() throws Exception {
    // Cleanup after each test
}
```

**After:**
```java
@BeforeEach
public void setUp() throws Exception {
    // Setup before each test
}

@AfterEach
public void tearDown() throws Exception {
    // Cleanup after each test
}
```

**Changes Applied:**
- Replace implicit naming convention with `@BeforeEach` annotation
- Replace implicit naming convention with `@AfterEach` annotation
- Remove `@Override` annotation (no longer overriding from base class)
- Methods can remain `protected` or become `public`

---

##### Test Suite Migration

JUnit 3 uses `suite()` methods and `TestSuite` class. JUnit 5 uses `@TestMethodOrder` with ordering annotations.

**Before:**
```java
public static Test suite() {
    TestSuite suite = new TestSuite();
    suite.addTest(new MyTest("testBasicAssertions"));
    suite.addTest(new MyTest("testArrayAssertions"));
    suite.addTest(new MyTest("testWithAssume"));
    return suite;
}
```

**After:**
```java
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MyTest {
    @Test
    @Order(1)
    public void testBasicAssertions() { }

    @Test
    @Order(2)
    public void testArrayAssertions() { }

    @Test
    @Order(3)
    public void testWithAssume() { }
}
```

**Changes Applied:**
- Remove `suite()` method completely
- Add `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` to class
- Add `@Order(n)` annotations to maintain execution order
- Add required imports:
  - `org.junit.jupiter.api.TestMethodOrder`
  - `org.junit.jupiter.api.MethodOrderer`
  - `org.junit.jupiter.api.Order`

---

#### JUnit 4 Annotations and Classes

The cleanup tool handles all major JUnit 4 annotations, lifecycle methods, and special constructs.

##### JUnit 4 Migration Summary Table

| JUnit 4 Construct                 | Description                           | JUnit 5 Equivalent                         |
|----------------------------------|---------------------------------------|--------------------------------------------|
| `@Test`                          | Test method marker                    | `@Test` (from `org.junit.jupiter.api`)     |
| `@Before`                        | Setup before each test                | `@BeforeEach`                              |
| `@After`                         | Cleanup after each test               | `@AfterEach`                               |
| `@BeforeClass`                   | Setup before all tests (static)       | `@BeforeAll`                               |
| `@AfterClass`                    | Cleanup after all tests (static)      | `@AfterAll`                                |
| `@Ignore`                        | Disable a test                        | `@Disabled`                                |
| `@Ignore("reason")`              | Disable with message                  | `@Disabled("reason")`                      |
| `@Test(expected = Ex.class)`     | Expected exception test               | `assertThrows(Ex.class, () -> {...})`      |
| `@Test(timeout = ms)`            | Timeout test                          | `assertTimeout(Duration.ofMillis(ms), ...)` |
| `@RunWith(Suite.class)`          | Suite runner                          | `@Suite`                                   |
| `@Suite.SuiteClasses({...})`     | Suite configuration                   | `@SelectClasses({...})`                    |
| `@Rule`                          | Test rule (instance-level)            | `@RegisterExtension`                       |
| `@ClassRule`                     | Test rule (class-level, static)       | `@RegisterExtension` (static)              |
| `@FixMethodOrder`                | Test method ordering                  | `@TestMethodOrder`                         |
| `org.junit.Assert.*`             | Assertion methods                     | `org.junit.jupiter.api.Assertions.*`       |
| `org.junit.Assume.*`             | Assumption methods                    | `org.junit.jupiter.api.Assumptions.*`      |

---

##### Lifecycle Annotations

JUnit 4 lifecycle annotations are replaced with JUnit 5 equivalents.

**Before:**
```java
import org.junit.Before;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.AfterClass;

@BeforeClass
public static void setUpBeforeClass() throws Exception {
    // Setup before all tests
}

@AfterClass
public static void tearDownAfterClass() throws Exception {
    // Cleanup after all tests
}

@Before
public void setUp() throws Exception {
    // Setup before each test
}

@After
public void tearDown() throws Exception {
    // Cleanup after each test
}
```

**After:**
```java
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

@BeforeAll
public static void setUpBeforeClass() throws Exception {
    // Setup before all tests
}

@AfterAll
public static void tearDownAfterClass() throws Exception {
    // Cleanup after all tests
}

@BeforeEach
public void setUp() throws Exception {
    // Setup before each test
}

@AfterEach
public void tearDown() throws Exception {
    // Cleanup after each test
}
```

**Changes Applied:**
- `@Before` → `@BeforeEach`
- `@After` → `@AfterEach`
- `@BeforeClass` → `@BeforeAll`
- `@AfterClass` → `@AfterAll`
- Update imports accordingly

---

##### Test Annotations

**@Test Annotation:**

Basic `@Test` annotation is migrated from JUnit 4 to JUnit 5:

**Before:**
```java
import org.junit.Test;

@Test
public void myTest() {
    // test code
}
```

**After:**
```java
import org.junit.jupiter.api.Test;

@Test
public void myTest() {
    // test code
}
```

**@Ignore / @Disabled:**

**Before:**
```java
import org.junit.Ignore;
import org.junit.Test;

@Ignore
@Test
public void ignoredTestWithoutMessage() {
    fail("This test is ignored");
}

@Ignore("Ignored with message")
@Test
public void ignoredTestWithMessage() {
    fail("This test is ignored with a message");
}
```

**After:**
```java
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled
@Test
public void ignoredTestWithoutMessage() {
    Assertions.fail("This test is ignored");
}

@Disabled("Ignored with message")
@Test
public void ignoredTestWithMessage() {
    Assertions.fail("This test is ignored with a message");
}
```

**Changes Applied:**
- `@Ignore` → `@Disabled`
- `@Ignore("reason")` → `@Disabled("reason")`
- Update imports

---

##### Test Suite Annotations

**Before:**
```java
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
    MyTest.class,
    OtherTest.class
})
public class MyTestSuite {
}
```

**After:**
```java
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SelectClasses;

@Suite
@SelectClasses({
    MyTest.class,
    OtherTest.class
})
public class MyTestSuite {
}
```

**Changes Applied:**
- `@RunWith(Suite.class)` → `@Suite`
- `@Suite.SuiteClasses({...})` → `@SelectClasses({...})`
- Update imports to JUnit Platform Suite API

---

##### Rule Annotations

JUnit 4 `@Rule` and `@ClassRule` are migrated to JUnit 5 `@RegisterExtension`.

**TemporaryFolder Rule:**

**Before:**
```java
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

@Rule
public TemporaryFolder tempFolder = new TemporaryFolder();

@Test
public void test() throws IOException {
    File newFile = tempFolder.newFile("myfile.txt");
}
```

**After:**
```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

@TempDir
Path tempFolder;

@Test
public void test() throws IOException {
    File newFile = tempFolder.resolve("myfile.txt").toFile();
}
```

**TestName Rule:**

**Before:**
```java
import org.junit.Rule;
import org.junit.rules.TestName;

@Rule
public TestName tn = new TestName();

@Test
public void test() {
    System.out.println("Test name: " + tn.getMethodName());
}
```

**After:**
```java
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.BeforeEach;

private String testName;

@BeforeEach
void init(TestInfo testInfo) {
    this.testName = testInfo.getDisplayName();
}

@Test
public void test() {
    System.out.println("Test name: " + testName);
}
```

**ExternalResource Rule:**

**Before:**
```java
import org.junit.Rule;
import org.junit.rules.ExternalResource;

@Rule
public ExternalResource er = new ExternalResource() {
    @Override
    protected void before() throws Throwable {
        // setup
    }

    @Override
    protected void after() {
        // cleanup
    }
};
```

**After:**
```java
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

@RegisterExtension
public Er_5b8b4 er = new Er_5b8b4();

class Er_5b8b4 implements BeforeEachCallback, AfterEachCallback {
    public void beforeEach(ExtensionContext context) {
        // setup
    }

    public void afterEach(ExtensionContext context) {
        // cleanup
    }
}
```

**Changes Applied:**
- `@Rule` → `@RegisterExtension`
- `@ClassRule` (static) → `@RegisterExtension` (static)
- `TemporaryFolder` → `@TempDir Path`
- `TestName` → `TestInfo` parameter in `@BeforeEach`
- `ExternalResource` → Custom extension implementing `BeforeEachCallback` / `AfterEachCallback`
- For class rules: `BeforeAllCallback` / `AfterAllCallback`

---

#### JUnit Assertion Migration – JUnit 3 and 4 to JUnit 5

The **JUnit Cleanup** tool automates the migration of assertions from **JUnit 3** and **JUnit 4** to **JUnit 5**.  
This includes:

- Updating imports to `org.junit.jupiter.api.Assertions`
- Reordering parameters: JUnit 5 places the message **last**
- Safely transforming legacy assertion method calls
- Handling special cases like `assertThat` with Hamcrest matchers

---

##### Supported Assertion Methods

The cleanup handles the following assertion methods from JUnit 3/4:

| Assertion Method       | Parameter Count | Description                           |
|------------------------|-----------------|---------------------------------------|
| `assertEquals`         | 2 or 3          | Assert two values are equal           |
| `assertNotEquals`      | 2 or 3          | Assert two values are not equal       |
| `assertArrayEquals`    | 2 or 3          | Assert two arrays are equal           |
| `assertSame`           | 2 or 3          | Assert two objects are the same       |
| `assertNotSame`        | 2 or 3          | Assert two objects are not the same   |
| `assertTrue`           | 1 or 2          | Assert condition is true              |
| `assertFalse`          | 1 or 2          | Assert condition is false             |
| `assertNull`           | 1 or 2          | Assert object is null                 |
| `assertNotNull`        | 1 or 2          | Assert object is not null             |
| `fail`                 | 0 or 1          | Explicitly fail the test              |
| `assertThat`           | 2 or 3          | Assert with Hamcrest matcher          |

**Note**: The parameter count includes the optional message parameter.

---

##### Parameter Order Differences

| Framework | Signature Format                            |
|-----------|---------------------------------------------|
| JUnit 3   | `assertEquals("message", expected, actual)` |
| JUnit 4   | `assertEquals("message", expected, actual)` |
| JUnit 5   | `assertEquals(expected, actual, "message")` |

> **Note**: In JUnit 5, the message is always the **last** argument.  
> The cleanup ensures correct reordering **only if it's safe** (i.e., the first argument is a String literal).

---

##### Assertion Mapping Table

| JUnit 3/4 Assertion                        | JUnit 5 Equivalent                         |
|-------------------------------------------|--------------------------------------------|
| `assertEquals(expected, actual)`          | `assertEquals(expected, actual)`           |
| `assertEquals("msg", expected, actual)`   | `assertEquals(expected, actual, "msg")`    |
| `assertSame(expected, actual)`            | `assertSame(expected, actual)`             |
| `assertSame("msg", expected, actual)`     | `assertSame(expected, actual, "msg")`      |
| `assertNotSame(expected, actual)`         | `assertNotSame(expected, actual)`          |
| `assertNotSame("msg", expected, actual)`  | `assertNotSame(expected, actual, "msg")`   |
| `assertTrue(condition)`                   | `assertTrue(condition)`                    |
| `assertTrue("msg", condition)`            | `assertTrue(condition, "msg")`             |
| `assertFalse(condition)`                  | `assertFalse(condition)`                   |
| `assertFalse("msg", condition)`           | `assertFalse(condition, "msg")`            |
| `assertNull(object)`                      | `assertNull(object)`                       |
| `assertNull("msg", object)`               | `assertNull(object, "msg")`                |
| `assertNotNull(object)`                   | `assertNotNull(object)`                    |
| `assertNotNull("msg", object)`            | `assertNotNull(object, "msg")`             |
| `fail()`                                  | `fail()`                                   |
| `fail("msg")`                              | `fail("msg")`                               |
| `assertArrayEquals("msg", expected, actual)` | `assertArrayEquals(expected, actual, "msg")` |
| `assertNotEquals("msg", expected, actual)` | `assertNotEquals(expected, actual, "msg")` |

**assertThat Special Handling:**

| JUnit 4 Assertion                          | JUnit 5 Equivalent                         |
|-------------------------------------------|--------------------------------------------|
| `Assert.assertThat(value, matcher)`       | `assertThat(value, matcher)` (Hamcrest)    |
| `Assert.assertThat("msg", value, matcher)`| `assertThat("msg", value, matcher)` (Hamcrest) |

> **Note**: `assertThat` is migrated to use Hamcrest's `MatcherAssert.assertThat` with static import, not JUnit 5's Assertions.

---

#### Example Transformations

##### Equality Check

**Before (JUnit 3/4):**
```java
assertEquals("Expected and actual differ", expected, actual);
```

**After (JUnit 5):**
```java
assertEquals(expected, actual, "Expected and actual differ");
```

---

##### Null Check

**Before:**
```java
assertNull("Object must be null", obj);
```

**After:**
```java
assertNull(obj, "Object must be null");
```

---

##### Boolean Assertions

**Before:**
```java
assertTrue("Must be true", condition);
assertFalse("Must be false", condition);
```

**After:**
```java
assertTrue(condition, "Must be true");
assertFalse(condition, "Must be false");
```

---

##### Identity Assertions

**Before:**
```java
assertSame("Should be the same", expected, actual);
assertNotSame("Should not be the same", expected, actual);
```

**After:**
```java
assertSame(expected, actual, "Should be the same");
assertNotSame(expected, actual, "Should not be the same");
```

---

##### NotNull Assertions

**Before:**
```java
assertNotNull("Should not be null", object);
```

**After:**
```java
assertNotNull(object, "Should not be null");
```

---

##### Fail Statements

**Before:**
```java
fail("Unexpected state reached");
```

**After:**
```java
fail("Unexpected state reached");
```

---

#### JUnit Assumption Migration

The cleanup also handles JUnit 4 assumption methods, which are used for conditional test execution.

##### Supported Assumption Methods

The cleanup handles the following assumption methods from JUnit 4:

| Assumption Method  | Parameter Count | Description                              |
|--------------------|-----------------|------------------------------------------|
| `assumeTrue`       | 1 or 2          | Assume condition is true                 |
| `assumeFalse`      | 1 or 2          | Assume condition is false                |
| `assumeNotNull`    | 1 or 2          | Assume object is not null                |
| `assumeThat`       | 2 or 3          | Assume with Hamcrest matcher             |

---

##### Assumption Mapping Table

| JUnit 4 Assumption                         | JUnit 5 Equivalent                         |
|-------------------------------------------|--------------------------------------------|
| `Assume.assumeTrue(condition)`            | `Assumptions.assumeTrue(condition)`        |
| `Assume.assumeTrue("msg", condition)`     | `Assumptions.assumeTrue(condition, "msg")` |
| `Assume.assumeFalse(condition)`           | `Assumptions.assumeFalse(condition)`       |
| `Assume.assumeFalse("msg", condition)`    | `Assumptions.assumeFalse(condition, "msg")`|
| `Assume.assumeNotNull(object)`            | `Assumptions.assumeNotNull(object)`        |
| `Assume.assumeNotNull("msg", object)`     | `Assumptions.assumeNotNull(object, "msg")` |
| `Assume.assumeThat(value, matcher)`       | `assumeThat(value, matcher)` (Hamcrest)    |
| `Assume.assumeThat("msg", value, matcher)`| `assumeThat("msg", value, matcher)` (Hamcrest) |

**Example:**

**Before:**
```java
import org.junit.Assume;

@Test
public void testWithAssume() {
    Assume.assumeTrue("Precondition failed", true);
    Assume.assumeFalse("Precondition not met", false);
    Assume.assumeNotNull("Value should not be null", new Object());
}
```

**After:**
```java
import org.junit.jupiter.api.Assumptions;

@Test
public void testWithAssume() {
    Assumptions.assumeTrue(true, "Precondition failed");
    Assumptions.assumeFalse(false, "Precondition not met");
    Assumptions.assumeNotNull(new Object(), "Value should not be null");
}
```

**Changes Applied:**
- `org.junit.Assume` → `org.junit.jupiter.api.Assumptions`
- Parameter order changed (message moved to last position)
- `assumeThat` uses Hamcrest's static import from `org.hamcrest.junit.MatcherAssume`

---

#### Notes

- The cleanup uses `org.junit.jupiter.api.Assertions` for all migrated assertions
- The cleanup uses `org.junit.jupiter.api.Assumptions` for all migrated assumptions
- Parameter reordering is applied conservatively, only if the first argument is a string literal
- `assertThat` is migrated to use Hamcrest's `MatcherAssert.assertThat` with static import
- `assumeThat` is migrated to use Hamcrest's `MatcherAssume.assumeThat` with static import
- Import statements are updated automatically:
  - `org.junit.*` → `org.junit.jupiter.api.*`
  - `org.junit.runners.*` → `org.junit.platform.suite.api.*`
  - Static imports are preserved with updated package names

---

#### Limitations

- **Custom Runners and Complex Rules**  
  Tests using `@RunWith(...)` with custom runners, or sophisticated `@Rule` implementations may need manual migration.
  
- **Test Suites (JUnit 3)**  
  Legacy `TestSuite` usage is automatically migrated using `@TestMethodOrder` and `@Order` annotations to preserve test execution order.

- **Parameterized Tests**  
  JUnit 4 parameterized tests (`@RunWith(Parameterized.class)`) are not automatically migrated and require manual conversion to JUnit 5's `@ParameterizedTest`.

- **Theories**  
  JUnit 4 theories (`@RunWith(Theories.class)`) are not automatically migrated.

- **Expected Exceptions and Timeouts**  
  The cleanup currently does not automatically migrate `@Test(expected=...)` and `@Test(timeout=...)` attributes. These require manual conversion to `assertThrows()` and `assertTimeout()`.

- **Custom Matchers**  
  Custom Hamcrest matchers should be reviewed after migration to ensure compatibility.

- **Static Imports**  
  Both wildcard (`import static org.junit.Assert.*`) and explicit static imports are handled, but code style may vary.

---

#### Usage

The JUnit Cleanup can be executed from within Eclipse using the Clean Up framework.

**Via Eclipse UI:**

1. Select Java files or packages in the Package Explorer
2. Right-click → **Source** → **Clean Up...**
3. Choose **Configure...** to customize cleanup settings
4. Enable **JUnit Cleanup** options in the configuration
5. Click **Finish** to apply the cleanup

**Via Save Actions:**

Configure automatic cleanup on save:

1. **Window** → **Preferences** → **Java** → **Editor** → **Save Actions**
2. Enable **Perform the selected actions on save**
3. Enable **Additional actions** → **Configure...**
4. Enable JUnit-related cleanup options
5. Apply changes

**Supported Cleanup Operations:**

The JUnit Cleanup includes multiple sub-operations that can be enabled independently:

- Migrate JUnit 3 test classes to JUnit 5
- Migrate JUnit 4 annotations to JUnit 5
- Update assertion method calls (parameter reordering)
- Update assumption method calls (parameter reordering)
- Migrate `@Rule` and `@ClassRule` to extensions
- Update test suite configurations
- Fix method ordering annotations

**Note**: The cleanup is safe and non-destructive. It only transforms code that matches known patterns from JUnit 3/4 to JUnit 5 equivalents.

---

This documentation is based on the test coverage provided in the JUnit 3 and 4 cleanup test cases. Manual adjustments may be necessary for advanced use cases or project-specific setups.

**Test Coverage:**
- `sandbox_junit_cleanup_test/src/org/eclipse/jdt/ui/tests/quickfix/Java8/JUnit3CleanupCases.java`
- `sandbox_junit_cleanup_test/src/org/eclipse/jdt/ui/tests/quickfix/Java8/JUnitCleanupCases.java`
- `sandbox_junit_cleanup_test/src/org/eclipse/jdt/ui/tests/quickfix/Java8/JUnitMigrationCleanUpTest.java`

<a href="/marketplace-client-intro?mpc_install=6454408" class="drag" title="Drag to your running Eclipse* workspace. *Requires Eclipse Marketplace Client">
<img style="width:80px;" typeof="foaf:Image" class="img-responsive" src="https://marketplace.eclipse.org/modules/custom/eclipsefdn/eclipsefdn_marketplace/images/btn-install.svg" alt="Drag to your running Eclipse* workspace. *Requires Eclipse Marketplace Client" />
</a>

---

## Installation

You can use the P2 update site:

```
https://github.com/carstenartur/sandbox/raw/main
```

> **Warning:**  
> Use only with a fresh Eclipse installation that can be discarded after testing.  
> It may break your setup. Don’t say you weren’t warned...