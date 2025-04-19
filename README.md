# Sandbox Project

This repository serves as a sandbox to experiment with various tools and build strategies.

## Table of Contents

- [Build Instructions](#build-instructions)
- [CI Status](#ci-status)
- [What's Included](#whats-included)
- [Projects](#projects)
  - [sandbox_cleanup_application](#1-sandbox_cleanup_application)
  - [sandbox_encoding_quickfix](#2-sandbox_encoding_quickfix)
  - [sandbox_extra_search](#3-sandbox_extra_search)
  - [sandbox_usage_view](#4-sandbox_usage_view)
  - [sandbox_platform_helper](#5-sandbox_platform_helper)
  - [sandbox_tools](#6-sandbox_tools)
  - [sandbox_functional_converter](#7-sandbox_functional_converter)
  - [sandbox_junit](#8-sandbox_junit)
- [Installation](#installation)

## Build Instructions

To build the project, including a WAR file that contains the update site, run:

```bash
mvn -Dinclude=web -Pjacoco verify
```

- The product will be located in `sandbox_product/target`
- The WAR file will be located in `sandbox_web/target`

---

## CI Status

### main (2025-03)

[![Java CI with Maven](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml/badge.svg)](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml)  
[![CodeQL](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml/badge.svg)](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml)  
[![Codacy Security Scan](https://github.com/carstenartur/sandbox/actions/workflows/codacy.yml/badge.svg)](https://github.com/carstenartur/sandbox/actions/workflows/codacy.yml)  
[![PMD](https://github.com/carstenartur/sandbox/actions/workflows/pmd.yml/badge.svg)](https://github.com/carstenartur/sandbox/actions/workflows/pmd.yml)

### 2022-12

[![Java CI with Maven](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml/badge.svg?branch=2022-12)](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml)  
[![CodeQL](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml/badge.svg?branch=2022-12)](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml)  
[![Codacy Security Scan](https://github.com/carstenartur/sandbox/actions/workflows/codacy.yml/badge.svg?branch=2022-12)](https://github.com/carstenartur/sandbox/actions/workflows/codacy.yml)  
[![PMD](https://github.com/carstenartur/sandbox/actions/workflows/pmd.yml/badge.svg?branch=2022-12)](https://github.com/carstenartur/sandbox/actions/workflows/pmd.yml)

### 2022-09

[![Java CI with Maven](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml/badge.svg?branch=2022-09)](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml)  
[![CodeQL](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml/badge.svg?branch=2022-09)](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml)  
[![Codacy Security Scan](https://github.com/carstenartur/sandbox/actions/workflows/codacy.yml/badge.svg?branch=2022-09)](https://github.com/carstenartur/sandbox/actions/workflows/codacy.yml)  
[![PMD](https://github.com/carstenartur/sandbox/actions/workflows/pmd.yml/badge.svg?branch=2022-09)](https://github.com/carstenartur/sandbox/actions/workflows/pmd.yml)

### 2022-06

[![Java CI with Maven](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml/badge.svg?branch=2022-06)](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml)  
[![CodeQL](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml/badge.svg?branch=2022-06)](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml)  
[![Codacy Security Scan](https://github.com/carstenartur/sandbox/actions/workflows/codacy.yml/badge.svg?branch=2022-06)](https://github.com/carstenartur/sandbox/actions/workflows/codacy.yml)  
[![PMD](https://github.com/carstenartur/sandbox/actions/workflows/pmd.yml/badge.svg?branch=2022-06)](https://github.com/carstenartur/sandbox/actions/workflows/pmd.yml)

---

## What's Included

Java version per branch:

- Since `2024-06`: Java 21  
- From `2022-12` onwards: Java 17  
- Up to `2022-06`: Java 11

Topics covered:

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

---

#### Limitations

- Dynamic encodings (read from config or variables) are left untouched
- Aggregation introduces class-level fields (may require conflict checks)
- Cleanup logic avoids modifying non-I/O encoding usages

---

This documentation is based on test-driven implementations in the `sandbox_encoding_quickfix_test` module and reflects support for modern and legacy encoding cleanup across Java 7 to 22.

Partial implementation to highlight platform encoding usage via API changes.  
Reference: https://openjdk.java.net/jeps/400

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

This documentation is based on the cleanup logic and test cases in  
`Java8CleanUpTest.java` and `Java9CleanUpTest.java`.  
Manual review is advised for edge cases or plugin-specific conventions.

PoC for a QuickFix to migrate code based on new platform features:  
https://www.eclipse.org/eclipse/news/4.20/platform_isv.php#simpler-status-creation

### 6. `sandbox_tools`

**While-to-For** loop converter — already merged into Eclipse JDT.

### 7. `sandbox_functional_converter`

#### Functional Converter Cleanup – Transform Imperative Loops into Functional Java 8 Streams

This cleanup modernizes imperative Java loop constructs by transforming them into functional-style equivalents using Java 8 Streams, `map`, `filter`, `reduce`, and `forEach`.

---

#### Source and Test Basis

This cleanup is fully tested in:

- `sandbox_functional_converter_test/src/org/sandbox/jdt/ui/tests/quickfix/Java8CleanUpTest.java`

The test class defines:

- A parameterized `EnumSource` with 30+ fully supported loop transformation cases
- A list of `@Disabled` scenarios (future features)
- A set of `@ValueSource` cases where no change should be applied

---

#### Supported Transformations

The cleanup currently supports:

| Pattern                                 | Transformed To                                      |
|----------------------------------------|-----------------------------------------------------|
| Simple enhanced for-loops              | `list.forEach(...)` or `list.stream().forEach(...)` |
| Mapping inside loops                   | `.stream().map(...)`                                |
| Filtering via `if` or `continue`       | `.stream().filter(...)`                             |
| Reductions (sum/counter)               | `.stream().map(...).reduce(...)`                    |
| `String` concatenation in loops        | `.reduce(..., String::concat)`                      |
| Null checks + mapping                  | `.filter(Objects::nonNull).map(...)`                |
| Conditional early `return true/false`  | `.anyMatch(...)` / `.noneMatch(...)`                |
| Method calls inside mapping/filtering  | `map(x -> method(x))`, `filter(...)`                |
| Combined `filter`, `map`, `forEach`    | Chained stream transformations                      |

---

#### Examples

**Imperative loop:**
```java
for (Integer l : list) {
    if (l != null) {
        String s = l.toString();
        System.out.println(s);
    }
}
```

**Functional:**
```java
list.stream()
    .filter(l -> l != null)
    .map(Object::toString)
    .forEach(System.out::println);
```

---

#### Reductions (Accumulators)

**Example:**
```java
int count = 0;
for (String s : list) {
    count += 1;
}
```

**Transformed:**
```java
int count = list.stream()
    .map(x -> 1)
    .reduce(0, Integer::sum);
```

Also supported:

- Decrementing: `i -= 1` → `.reduce(i, (a, b) -> a - b)`
- Custom mapped reductions: `.map(this::foo).reduce(...)`

---

#### Not Yet Supported (Disabled Tests)

The following patterns are present but currently **not supported** and are marked `@Disabled`:

| Pattern Description                                 | Reason / Required Feature                          |
|-----------------------------------------------------|-----------------------------------------------------|
| `Map.put(...)` inside loop                          | Needs `Collectors.toMap(...)` support               |
| Early `break`/`return` inside loop body             | Requires stream short-circuit modeling             |
| Labeled `continue` or `break` (`label:`)            | Not expressible via Stream API                     |
| Complex `if-else-return` branches                   | Requires flow graph and branching preservation      |
| `throw` inside loop                                 | Non-convertible – not compatible with Stream flow  |
| Multiple side effects in loop (e.g., `i++`, `j++`)  | State mutation not easily transferable              |

These are valid test cases but intentionally **excluded from transformation** for semantic safety.

---

#### Ignored Cases – No Cleanup Triggered

Tests in the `@ValueSource` section confirm the cleanup **does not act** in edge cases, e.g.:

- Non-loop constructs
- Loops over arrays instead of `List`
- Loops using early `return`, `throw`, or `continue label`
- Loops mixing multiple mutable accumulators

---

#### Java Version Compatibility

| API Used                      | Requires Java |
|-------------------------------|---------------|
| `Stream`, `map`, `filter`     | Java 8        |
| `Collectors.toList()`         | Java 8        |
| `Stream.anyMatch/noneMatch`   | Java 8        |
| `Stream.reduce(...)`          | Java 8        |

This cleanup is designed for Java 8+ projects and avoids APIs introduced in later versions.

---

#### Cleanup Name & Activation

| Eclipse Cleanup ID                          | Value                       |
|---------------------------------------------|-----------------------------|
| `MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP` | `true` (enable this feature) |

---

#### Limitations

- Does not preserve external loop-scoped variables (e.g., index tracking, multiple accumulators)
- Cannot convert control structures with `return`, `break`, `continue label`, or `throw`
- Currently does not support loops producing `Map<K,V>` outputs or grouping

---

#### Summary

The Functional Converter Cleanup:

- Applies safe and proven transformations
- Targets common loop structures
- Helps modernize Java 5/6/7-style loops to Java 8 stream-based idioms
- Uses an extensive test suite for coverage and correctness

---

For roadmap or contributions, see the test class:
`Java8CleanUpTest.java` in the `sandbox_functional_converter_test` module.

Converts `Iterator` loops to functional loops.  
See: https://github.com/carstenartur/sandbox/wiki/Functional-Converter

### 8. `sandbox_junit`

#### JUnit Cleanup – Feature Overview

The **JUnit Cleanup** tool automates the migration of legacy tests from **JUnit 3** and **JUnit 4** to **JUnit 5**.  
It is based on verified transformations from the following test files:

- `JUnit3CleanupCases.java`
- `JUnitCleanupCases.java`

---

#### Migration Summary

The table below summarizes how legacy JUnit constructs are translated to modern JUnit 5 equivalents:

| Legacy JUnit Construct                      | Origin     | JUnit 5 Equivalent                         |
|--------------------------------------------|------------|--------------------------------------------|
| `extends TestCase`                         | JUnit 3    | (removed) – no base class needed           |
| `setUp()`                                   | JUnit 3    | `@BeforeEach`                              |
| `tearDown()`                                | JUnit 3    | `@AfterEach`                               |
| `test*` method name prefix                  | JUnit 3    | `@Test` + rename to descriptive name       |
| `@Test`                                     | JUnit 4    | `@Test` (from `org.junit.jupiter.api`)     |
| `@Before` / `@After`                        | JUnit 4    | `@BeforeEach` / `@AfterEach`               |
| `@BeforeClass` / `@AfterClass`             | JUnit 4    | `@BeforeAll` / `@AfterAll`                 |
| `@Ignore`                                   | JUnit 4    | `@Disabled`                                |
| `@Test(expected = Exception.class)`         | JUnit 4    | `assertThrows(Exception.class, ...)`       |
| `@Test(timeout = ...)`                      | JUnit 4    | `assertTimeout(Duration.ofMillis(...), ...)` |
| `@Rule`, `@ClassRule`                       | JUnit 4    | `@RegisterExtension` / `@ExtendWith`       |
| `junit.framework.Assert` or `org.junit.Assert` methods | JUnit 3/4 | `org.junit.jupiter.api.Assertions` methods |

---

#### Migration Details

##### JUnit 3 Migration

###### Class Structure

- **Remove `extends TestCase`**
- **Remove `suite()` and `main()` methods**

###### Test Methods

Before:
```java
public void testExample() {
    // test logic
}
```

After:
```java
@Test
void example() {
    // test logic
}
```

###### Setup and Teardown

Before:
```java
protected void setUp() throws Exception {
    // setup
}

protected void tearDown() throws Exception {
    // teardown
}
```

After:
```java
@BeforeEach
void setUp() throws Exception {
    // setup
}

@AfterEach
void tearDown() throws Exception {
    // teardown
}
```

---

##### JUnit 4 Migration

###### Annotations

Before:
```java
@Before
public void init() {}

@After
public void cleanup() {}

@Test(expected = IOException.class)
public void testException() {}

@Test(timeout = 1000)
public void testTimeout() {}

@Ignore
public void ignoredTest() {}
```

After:
```java
@BeforeEach
void init() {}

@AfterEach
void cleanup() {}

@Test
void testException() {
    assertThrows(IOException.class, () -> {
        // code that throws
    });
}

@Test
void testTimeout() {
    assertTimeout(Duration.ofMillis(1000), () -> {
        // code
    });
}

@Disabled
void ignoredTest() {}
```

---

#### Limitations

- **Custom Runners and Complex Rules**  
  Tests using `@RunWith(...)`, custom runners, or sophisticated `@Rule` implementations may need to be migrated manually.

- **Test Suites**  
  Legacy `TestSuite` usage is not automatically migrated and should be replaced with JUnit 5 `@Nested` classes or display name tags.

---

#### Usage

The JUnit Cleanup can be executed from within Eclipse.  
It scans Java test files and applies the transformations described above automatically.

---

This documentation is based on the test coverage provided in the JUnit 3 and 4 cleanup test cases. Manual adjustments may be necessary for advanced use cases or project-specific setups.
Cleanup to migrate JUnit 4 tests to JUnit 5.

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