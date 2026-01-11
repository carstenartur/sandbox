# Encoding Quickfix Plugin

> **Navigation**: [Main README](../README.md) | [Architecture](ARCHITECTURE.md) | [TODO](TODO.md)

## Overview

The **Encoding Cleanup** plugin automatically replaces platform-dependent or implicit encoding usage with explicit, safe alternatives using `StandardCharsets.UTF_8` or equivalent constants. This cleanup improves code portability and prevents encoding-related bugs across different platforms and locales.

## Key Features

- 🔄 **Automatic Encoding Conversion** - Replaces implicit platform encodings with explicit UTF-8
- 🎯 **Multiple Strategies** - Prefer UTF-8, Keep Behavior, or Aggregate UTF-8
- 📦 **Java Version Aware** - Adapts transformations based on Java 7, 10, 11, or 21+
- 🧪 **Comprehensive Test Coverage** - Tested against extensive test suites
- 🔌 **Eclipse Integration** - Works seamlessly with Eclipse Clean Up framework

## Quick Start

### Enable in Eclipse

1. Open **Source** → **Clean Up...**
2. Navigate to the **Encoding** category
3. Select one of the strategies:
   - **Prefer UTF-8** - Replace all encodings with UTF-8
   - **Keep Behavior** - Only replace explicit "UTF-8" strings
   - **Aggregate UTF-8** - Create a shared constant for UTF-8

### Example Transformations

**FileReader Replacement:**
```java
// Before
Reader r = new FileReader(file);

// After
Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
```

**Files.readAllLines:**
```java
// Before
List<String> lines = Files.readAllLines(path);

// After
List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
```

## Cleanup Strategies

| Strategy | Description | Use Case |
|----------|-------------|----------|
| **Prefer UTF-8** | Replace all implicit/platform encodings with UTF-8 | Standardize on UTF-8 across codebase |
| **Keep Behavior** | Only replace explicit "UTF-8" string literals | Conservative approach, preserve platform defaults |
| **Aggregate UTF-8** | Create class-level `UTF_8` constant, reference it everywhere | Reduce repetition, centralize encoding constant |

## Java Version Support

| Java Version | Features Available |
|--------------|-------------------|
| **Java 7-10** | Basic `StandardCharsets.UTF_8` replacements |
| **Java 11+** | `Files.newBufferedReader()`, `Channels.newReader()` with charset |
| **Java 21+** | `Files.readString()`, `Files.writeString()` with charset |

> **Note**: Cleanup is disabled for Java < 7 (StandardCharsets not available)

## Supported APIs

The cleanup handles a wide range of encoding-sensitive classes:

### I/O Streams
- `InputStreamReader` / `OutputStreamWriter`
- `FileReader` / `FileWriter` (converted to stream + charset)
- `PrintWriter`
- `Scanner`

### NIO Files
- `Files.newBufferedReader()` / `Files.newBufferedWriter()`
- `Files.readAllLines()` / `Files.readString()`
- `Files.writeString()`

### Other APIs
- `Charset.forName("UTF-8")` → `StandardCharsets.UTF_8`
- `Channels.newReader()` / `Channels.newWriter()`
- `InputSource.setEncoding()` (SAX API)

## Documentation

- **[Architecture](ARCHITECTURE.md)** - Implementation details and design
- **[TODO](TODO.md)** - Pending features and known issues
- **[Main README](../README.md#2-sandbox_encoding_quickfix)** - Detailed examples and configuration

## Testing

Comprehensive test coverage in `sandbox_encoding_quickfix_test`:
- API transformation tests for all supported methods
- Java version compatibility tests
- Edge case handling
- Regression tests

Run tests:
```bash
xvfb-run --auto-servernum mvn test -pl sandbox_encoding_quickfix_test
```

## Contributing to Eclipse JDT

This plugin is designed for easy integration into Eclipse JDT:
1. Replace `org.sandbox` with `org.eclipse` in package names
2. Move classes to `org.eclipse.jdt.core.manipulation`
3. Update cleanup registration in plugin.xml

See [TODO.md](TODO.md) for Eclipse JDT contribution checklist.

## License

Eclipse Public License 2.0 (EPL-2.0)

---

> **Related Plugins**: [Platform Helper](../sandbox_platform_helper/), [JUnit Cleanup](../sandbox_junit_cleanup/)
