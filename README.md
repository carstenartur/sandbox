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

The **JUnit Cleanup** tool automatically migrates JUnit 4 tests to JUnit 5.  
The following transformations are supported based on the test cases found in  
`sandbox_junit_cleanup_test/src/org/eclipse/jdt/ui/tests/quickfix/Java8/JUnitCleanupCases.java`.

---

#### Supported Transformations

##### 1. Annotations

- `@Test`  
  → Migrated from `org.junit.Test` to `org.junit.jupiter.api.Test`.

- `@Before` / `@After`  
  → Replaced with `@BeforeEach` / `@AfterEach` from JUnit 5.

- `@BeforeClass` / `@AfterClass`  
  → Replaced with `@BeforeAll` / `@AfterAll`.

- `@Ignore`  
  → Replaced with `@Disabled`.

---

##### 2. Assertions

- Static imports from `org.junit.Assert`  
  → Replaced with `org.junit.jupiter.api.Assertions`.

- Methods like `assertEquals`, `assertTrue`, `assertFalse`, etc.  
  → Updated to their JUnit 5 equivalents.

---

##### 3. Expected Exceptions

JUnit 4:

```java
@Test(expected = IllegalArgumentException.class)
public void testSomething() {
    // code
}
```

JUnit 5:

```java
@Test
void testSomething() {
    assertThrows(IllegalArgumentException.class, () -> {
        // code
    });
}
```

---

##### 4. Timeout

JUnit 4:

```java
@Test(timeout = 1000)
public void testWithTimeout() {
    // code
}
```

JUnit 5:

```java
@Test
void testWithTimeout() {
    assertTimeout(Duration.ofMillis(1000), () -> {
        // code
    });
}
```

---

##### 5. Rules and ClassRules

- Fields annotated with `@Rule` or `@ClassRule`  
  → Removed or migrated to extensions (e.g., via `@RegisterExtension` or `@ExtendWith`) if applicable.

---

##### 6. Parameterized Tests

- `@RunWith(Parameterized.class)`  
  → Migrated to JUnit 5's `@ParameterizedTest` using appropriate sources like `@ValueSource`, `@CsvSource`, or `@MethodSource`.

---

#### Limitations

- **Custom Runners**  
  Test classes using custom `@RunWith` runners are **not** automatically migrated.

- **Complex Rules**  
  Rules with no direct JUnit 5 equivalent must be migrated manually.

---

#### How to Use

The cleanup can be triggered via the command line or directly in Eclipse.  
It scans your test classes and applies the above transformations automatically.

---

This documentation is based on the migration logic verified by the test cases in  
`JUnitCleanupCases.java`. For complex or project-specific test structures, manual adjustments may still be necessary.

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