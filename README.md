# Sandbox Project

This repository serves as a sandbox to experiment with various tools and build strategies.

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

Partial implementation to highlight platform encoding usage via API changes.  
Reference: https://openjdk.java.net/jeps/400

### 3. `sandbox_extra_search`

Experimental search tool for identifying critical classes when upgrading Eclipse or Java versions.

### 4. `sandbox_usage_view`

Provides a table view of code objects, sorted by name, to detect inconsistent naming that could confuse developers.

### 5. `sandbox_platform_helper`

PoC for a QuickFix to migrate code based on new platform features:  
https://www.eclipse.org/eclipse/news/4.20/platform_isv.php#simpler-status-creation

### 6. `sandbox_tools`

**While-to-For** loop converter — already merged into Eclipse JDT.

### 7. `sandbox_functional_converter`

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