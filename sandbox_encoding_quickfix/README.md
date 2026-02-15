# Encoding Quickfix Plugin

> **Navigation**: [Main README](../README.md) | [Architecture](ARCHITECTURE.md) | [TODO](TODO.md)

## Overview

The **Encoding Cleanup** plugin automatically replaces platform-dependent or implicit encoding usage with explicit, safe alternatives using `StandardCharsets.UTF_8` or equivalent constants. This cleanup improves code portability and prevents encoding-related bugs across different platforms and locales.

It supports multiple strategies and is Java-version-aware, adapting transformations based on the target Java version (7, 10, 11, or 21+).

## Key Features

- 🔄 **Automatic Encoding Conversion** - Replaces implicit platform encodings with explicit UTF-8
- 🎯 **Multiple Strategies** - Prefer UTF-8, Keep Behavior, or Aggregate UTF-8
- 📦 **Java Version Aware** - Adapts transformations based on Java 7, 10, 11, or 21+
- 🧪 **Comprehensive Test Coverage** - Tested against extensive test suites
- 🔌 **Eclipse Integration** - Works seamlessly with Eclipse Clean Up framework

## Test Coverage

The cleanup logic is tested and verified by the following test files:

- `Java22/ExplicitEncodingPatterns.java`
- `Java10/ExplicitEncodingPatternsPreferUTF8.java`
- `Java10/ExplicitEncodingPatternsKeepBehavior.java`
- `Java10/ExplicitEncodingPatternsAggregateUTF8.java`
- `Java10/ExplicitEncodingCleanUpTest.java`

## Quick Start

### Enable in Eclipse

1. Open **Source** → **Clean Up...**
2. Navigate to the **Encoding** category
3. Select one of the strategies:
   - **Prefer UTF-8** - Replace all encodings with UTF-8
   - **Keep Behavior** - Only replace explicit "UTF-8" strings
   - **Aggregate UTF-8** - Create a shared constant for UTF-8

### Via JDT Batch Tooling

Use these properties for command-line or automated cleanup:
- `encoding.strategy = PREFER_UTF8 | KEEP | AGGREGATE`
- `aggregate.charset.name = UTF_8`
- `min.java.version = 7 | 10 | 11 | 21`

## Cleanup Strategies

The cleanup supports three different strategies to handle encoding transformations:

| Strategy | Description | Platform Default Handling | Replaces `"UTF-8"` | Aggregates Constant |
|----------|-------------|---------------------------|--------------------|---------------------|
| **Prefer UTF-8** | Replace all implicit/platform encodings with UTF-8 | Yes | Yes | No |
| **Keep Behavior** | Only replace explicit "UTF-8" string literals | No | Yes (only explicit) | No |
| **Aggregate UTF-8** | Create class-level `UTF_8` constant, reference it everywhere | Yes | Yes | Yes (`UTF_8`) |

### Strategy: Prefer UTF-8

Replaces all literal `"UTF-8"` occurrences and platform-default encodings with `StandardCharsets.UTF_8`.

**Use Case**: Standardize on UTF-8 across entire codebase

**Example**:
```java
// Before
new InputStreamReader(in);
new FileReader(file);
Charset.forName("UTF-8");

// After
new InputStreamReader(in, StandardCharsets.UTF_8);
new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
StandardCharsets.UTF_8;
```

### Strategy: Keep Behavior

Only transforms code if `"UTF-8"` is explicitly used – avoids changing platform-default behaviors.

**Use Case**: Conservative approach, preserve platform defaults where they exist

**Example**:
```java
// Before
Charset charset = Charset.forName("UTF-8");
new InputStreamReader(in);  // No explicit UTF-8

// After
Charset charset = StandardCharsets.UTF_8;
new InputStreamReader(in);  // Left unchanged
```

### Strategy: Aggregate UTF-8

Replaces all `"UTF-8"` usage and `StandardCharsets.UTF_8` with a class-level constant.

**Use Case**: Reduce repetition, centralize encoding constant

**Example**:
```java
// Before
new InputStreamReader(in, StandardCharsets.UTF_8);
new FileReader(file);

// After
private static final Charset UTF_8 = StandardCharsets.UTF_8;

new InputStreamReader(in, UTF_8);
new InputStreamReader(new FileInputStream(file), UTF_8);
```

Also supports dynamic replacement of:
- `Charset.forName("UTF-8")`
- `"UTF-8"` literals passed to methods like `setEncoding(...)`

## Java Version Support

The cleanup is aware of Java version capabilities and adapts transformations accordingly:

| Java Version | Features Available |
|--------------|-------------------|
| **Java < 7** | Cleanup disabled - `StandardCharsets` not available |
| **Java 7-10** | Basic `StandardCharsets.UTF_8` replacements, stream wrapping, exception removal |
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

## Transformation Examples

### Example: FileReader Replacement

FileReader uses platform default encoding. The cleanup converts it to an explicit charset.

**Before:**
```java
Reader r = new FileReader(file);
```

**After:**
```java
Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
```

### Example: Channels.newReader (Java 10+)

**Before:**
```java
Reader r = Channels.newReader(channel, "UTF-8");
```

**After:**
```java
Reader r = Channels.newReader(channel, StandardCharsets.UTF_8);
```

### Example: Files.readAllLines (Java 10+)

**Before:**
```java
List<String> lines = Files.readAllLines(path);
```

**After:**
```java
List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
```

### Example: Scanner (Java 10+)

**Before:**
```java
Scanner scanner = new Scanner(inputStream);
```

**After:**
```java
Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8);
```

### Example: SAX InputSource

**Before:**
```java
InputSource source = new InputSource();
source.setEncoding("UTF-8");
```

**After:**
```java
source.setEncoding(StandardCharsets.UTF_8.name());
```

### Example: Aggregation Mode

If **Aggregate UTF-8** mode is enabled:

```java
private static final Charset UTF_8 = StandardCharsets.UTF_8;

Reader r = new InputStreamReader(in, UTF_8);
```

All uses of `StandardCharsets.UTF_8` or `"UTF-8"` will be redirected to `UTF_8`.

## Charset Literal Replacements

The cleanup recognizes common charset string literals and replaces them with the appropriate constants:

| String Literal | Replacement Constant |
|----------------|---------------------|
| `"UTF-8"` | `StandardCharsets.UTF_8` |
| `"US-ASCII"` | `StandardCharsets.US_ASCII` |
| `"ISO-8859-1"` | `StandardCharsets.ISO_8859_1` |
| `"UTF-16"` | `StandardCharsets.UTF_16` |
| `"UTF-16BE"` | `StandardCharsets.UTF_16BE` |
| `"UTF-16LE"` | `StandardCharsets.UTF_16LE` |

## Additional Fixes

The cleanup automatically handles:

- **Adds required imports**:
  - `import java.nio.charset.StandardCharsets;`
  - `import java.nio.charset.Charset;` (if aggregation is used)
- **Removes unnecessary code**:
  - `throws UnsupportedEncodingException` if replaced by standard charset
  - `"UTF-8"` string constants if inlined

## Limitations

- **Dynamic encodings**: Variables or configuration-based encodings are left untouched
- **Aggregation conflicts**: Aggregation introduces class-level fields (may require conflict checks if field name already exists)
- **Non-I/O encodings**: Cleanup logic avoids modifying non-I/O encoding usages

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

## Related Documentation

- **[Architecture](ARCHITECTURE.md)** - Implementation details, design patterns, and internal components
- **[TODO](TODO.md)** - Pending features and known issues
- **[Main README](../README.md#encoding-cleanup-sandbox_encoding_quickfix)** - Project overview

## References

> **JEP 400: UTF-8 by Default** - https://openjdk.java.net/jeps/400  
> This cleanup provides partial implementation to highlight platform encoding usage via API changes.

---

This documentation is based on test-driven implementations in the `sandbox_encoding_quickfix_test` module and reflects support for modern and legacy encoding cleanup across Java 7 to 22.

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
