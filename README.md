# Sandbox Project

[![Java CI with Maven](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml/badge.svg)](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml)
[![CodeQL](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml/badge.svg)](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml)

A collection of **Eclipse JDT cleanup plugins and tools** for modernizing Java code. This project provides automated refactoring capabilities including encoding fixes, JUnit migration, loop-to-stream conversions, and more.

## Features

- **Encoding Cleanup** – Replace platform-dependent encoding with explicit `StandardCharsets`
- **JUnit Migration** – Migrate JUnit 3/4 tests to JUnit 5
- **Functional Converter** – Transform imperative loops into Java 8 Streams
- **Platform Helper** – Simplify `Status` object creation in Eclipse plugins
- **While-to-For Converter** – Already merged into Eclipse JDT

## Installation

Install via P2 update site in Eclipse:

```
https://github.com/carstenartur/sandbox/raw/main
```

> **⚠️ Warning:** Use only with a fresh Eclipse installation that can be discarded after testing.

Alternatively, install from the [Eclipse Marketplace](https://marketplace.eclipse.org/content/sandbox).

## Building from Source

```bash
mvn -Dinclude=web -Pjacoco verify
```

- Product: `sandbox_product/target`
- WAR file: `sandbox_web/target`

## Java Version Requirements

| Branch       | Java Version |
|--------------|--------------|
| Since 2024-06 | Java 21     |
| 2022-12+     | Java 17      |
| Up to 2022-06| Java 11      |

## Projects

### Encoding Cleanup (`sandbox_encoding_quickfix`)

Replaces platform-dependent encoding usage with explicit `StandardCharsets.UTF_8` or equivalent constants. Supports multiple strategies:

- **Prefer UTF-8** – Replace all platform-default encodings with UTF-8
- **Keep Behavior** – Only fix explicit `"UTF-8"` literals
- **Aggregate UTF-8** – Use a shared class-level constant

**Example:**
```java
// Before
Reader r = new FileReader(file);

// After
Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
```

📖 [Learn more about JEP 400](https://openjdk.java.net/jeps/400)

### JUnit Cleanup (`sandbox_junit_cleanup`)

Automates migration from JUnit 3/4 to JUnit 5:

- Replaces `@Before`/`@After` with `@BeforeEach`/`@AfterEach`
- Converts `@Test(expected=...)` to `assertThrows(...)`
- Reorders assertion parameters (message last in JUnit 5)
- Removes `extends TestCase`

**Example:**
```java
// Before (JUnit 4)
@Test(expected = IOException.class)
public void testException() { ... }

// After (JUnit 5)
@Test
void testException() {
    assertThrows(IOException.class, () -> { ... });
}
```

### Functional Converter (`sandbox_functional_converter`)

Transforms imperative loops into functional-style Java 8 Streams:

**Example:**
```java
// Before
for (Integer l : list) {
    if (l != null) {
        System.out.println(l.toString());
    }
}

// After
list.stream()
    .filter(l -> l != null)
    .map(Object::toString)
    .forEach(System.out::println);
```

📖 [Wiki documentation](https://github.com/carstenartur/sandbox/wiki/Functional-Converter)

### Platform Helper (`sandbox_platform_helper`)

Simplifies `Status` object creation in Eclipse plugins:

**Example:**
```java
// Before
IStatus status = new Status(IStatus.ERROR, "plugin.id", "Error message", exception);

// After (Eclipse 4.20+)
IStatus status = Status.error("Error message", exception);
```

📖 [Eclipse Platform News](https://www.eclipse.org/eclipse/news/4.20/platform_isv.php#simpler-status-creation)

### Other Projects

| Project | Description | Status |
|---------|-------------|--------|
| `sandbox_cleanup_application` | CLI-based cleanup application | In progress |
| `sandbox_extra_search` | Search tool for Eclipse/Java upgrades | Experimental |
| `sandbox_usage_view` | Table view for detecting naming inconsistencies | In progress |
| `sandbox_tools` | While-to-For converter | ✅ Merged into Eclipse JDT |

## Contributing

All projects are work in progress. Contributions are welcome!

## License

See [LICENSE.txt](LICENSE.txt) for details.
