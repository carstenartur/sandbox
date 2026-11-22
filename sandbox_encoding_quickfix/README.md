# Encoding Quickfix Plugin

> **Navigation**: [Main README](../README.md) | [Architecture](ARCHITECTURE.md) | [TODO](TODO.md)

## Overview

The **Encoding Cleanup** plugin makes supported encoding choices explicit. Depending on the selected strategy, it preserves the runtime platform default or replaces that default with UTF-8. Recognized explicitly selected charsets retain their encoding.

It supports multiple strategies and is Java-version-aware, adapting transformations based on the target Java version (7, 10, 11, or 21+).

## Key Features

- 🔄 **Automatic Encoding Conversion** - Makes supported encoding uses explicit according to the selected policy
- 🎯 **Multiple Strategies** - Prefer UTF-8, Keep Behavior, or Aggregate UTF-8
- 📦 **Java Version Aware** - Adapts transformations based on Java 7, 10, 11, or 21+
- 🧪 **Comprehensive Test Coverage** - Tested against extensive test suites
- 🔌 **Eclipse Integration** - Works seamlessly with Eclipse Clean Up framework

## Typed constructors and checked exceptions

For Java 10 and later, supported `Scanner` and `Formatter` calls use actual
`Charset` overloads. A charset string is not replaced by a `.name()` expression.
The shared exception cleanup removes obsolete `UnsupportedEncodingException`
handling only when surviving expressions no longer require it.

Some file-based Charset constructors declare `IOException` rather than only
`FileNotFoundException`. The cleanup plans the required catch/throws changes,
source-owned interface contracts and callers together. A narrow handler is
widened only when its uses stay valid and it will not intercept unrelated I/O
failures. Otherwise the new exception is propagated through source contracts.
`Formatter` preserves the default **FORMAT** locale when adding its locale argument.

Select all required source files for **Source → Clean Up…**. The standard JDT
cleanup lifecycle combines their changes in one preview, apply and undo. An
incomplete selection or a fixed external contract is a reported conflict, not
a String-overload fallback. Integrations supporting Sandbox's existing scope
extension can discover the required same-project source closure. Save actions
must not silently expand their scope. Dynamic charset names remain unchanged.

`CharsetModernizationTest` verifies resolved constructor bindings, errors and new
warnings before/after, preview, idempotence and undo. It also compiles and executes
original and rewritten programs with `javac --release 10 -Xlint:all`.
`CharsetScopeTest` exercises the actual JDT multi-file cleanup lifecycle, source
interfaces, callers, scope conflicts, cancellation and stale-source rejection.

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
   - **Prefer UTF-8** - Use UTF-8 for supported implicit platform-default calls; preserve explicitly selected charsets
   - **Keep Behavior** - Make the runtime default explicit and preserve explicitly selected charsets
   - **Aggregate UTF-8** - Apply the UTF-8 default policy and reuse or create matching charset constants where safe

### Via JDT Batch Tooling

Use the shipped cleanup option IDs in a properties profile. Enable the master
option and select exactly one of the three strategy options, for example:

```properties
cleanup.explicit_encoding=true
cleanup.explicit_encoding_keep_behavior=true
cleanup.explicit_encoding_insert_utf8=false
cleanup.explicit_encoding_aggregate_to_utf8=false
```

For **Prefer UTF-8**, enable `cleanup.explicit_encoding_insert_utf8` instead of
`cleanup.explicit_encoding_keep_behavior`; for **Aggregate UTF-8**, enable
`cleanup.explicit_encoding_aggregate_to_utf8` instead. Keep the other strategy
options `false`. The Eclipse project's source level determines which API
overloads are available; there is no `min.java.version` cleanup option.

The shipped [conservative encoding profile](../.github/actions/cleanup-review/profiles/conservative-encoding.properties),
[explicit encoding review profile](../.github/cleanup-profiles/review-explicit-encoding.properties)
and [aggressive profile](../.github/cleanup-profiles/aggressive.properties) all
select **Keep Behavior** for encoding. A profile's name does not imply a UTF-8
behavior change.

## Cleanup Strategies

The cleanup supports three different strategies to handle encoding transformations:

| Strategy | Supported implicit platform-default calls | Recognized explicit charset names | Constant aggregation |
|----------|--------------------------------------------|-----------------------------------|----------------------|
| **Prefer UTF-8** | Use `StandardCharsets.UTF_8` | Use the matching standard charset | No |
| **Keep Behavior** | Use `Charset.defaultCharset()` | Use the matching standard charset | No |
| **Aggregate UTF-8** | Use a UTF-8 constant | Retain the matching charset | Reuse or create an owner-local field where safe; otherwise use the standard constant directly |

APIs with a specified UTF-8 default, such as the supported no-charset `Files`
reader/writer methods, keep that default in all three modes. They do not become
platform-default operations under **Keep Behavior**. These policies apply to
eligible API uses, not arbitrary string literals or dynamic charset names.

### Strategy: Prefer UTF-8

Uses `StandardCharsets.UTF_8` for supported implicit platform-default calls and
replaces recognized explicit names with their matching standard charset. An
explicit `"ISO-8859-1"`, for example, stays ISO-8859-1 rather than becoming UTF-8.

**Use Case**: Choose UTF-8 where supported calls currently rely on the platform default

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

Uses standard constants for supported explicit charset names and `Charset.defaultCharset()` for supported implicit-default calls. The runtime default is not frozen to the machine running the cleanup.

**Use Case**: Conservative approach, preserve platform defaults where they exist

**Example**:
```java
// Before
Charset charset = Charset.forName("UTF-8");
new InputStreamReader(in);  // No explicit UTF-8

// After
Charset charset = StandardCharsets.UTF_8;
new InputStreamReader(in, Charset.defaultCharset());
```

### Strategy: Aggregate UTF-8

Applies the same charset policy as **Prefer UTF-8**, then reuses or creates
owner-local constants for eligible rewrites. Recognized non-UTF-8 selections
keep their matching charset. Field names are derived from the charset with
collision handling; a direct standard constant is used when a field reference
or its initialization would be unsafe.

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

Recognized literals in supported `Charset.forName(...)` and
`InputSource.setEncoding(...)` calls are also eligible. Dynamic names and
unrelated methods with the same spelling are not covered by this policy.

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
- `FileReader` (typed Charset constructors on Java 11+ when the existing exception contract permits; compatible stream wrappers otherwise)
- `FileWriter` (typed Charset constructors on Java 11+; compatible stream wrappers on older source levels)
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

On Java 11+, a supported JDK `FileReader(File)` or `FileReader(String)` call
retains its concrete type and receives a typed `Charset` argument when an
existing catch, method throws clause or lambda contract covers `IOException`.
The Charset constructor declares `IOException`, which is broader than the
original constructor's `FileNotFoundException`; this rewrite does not widen the
surrounding exception contract.

For **Prefer UTF-8**, an existing broad contract permits this change:

```java
// Before
FileReader open(File file) throws IOException {
    return new FileReader(file);
}

// After (Java 11+)
FileReader open(File file) throws IOException {
    return new FileReader(file, StandardCharsets.UTF_8);
}
```

**Keep Behavior** uses `Charset.defaultCharset()` instead. If the typed
constructor is unavailable (including Java 7–10 source levels) or its exception
is not covered, the cleanup offers the stream wrapper only when the existing
target type accepts `InputStreamReader`. A concrete or inferred `FileReader`
target stays unchanged in those cases. Existing explicit-charset constructors
and anonymous subclasses are left unchanged.

For a compatible `Reader` target, the wrapper fallback is:

**Before:**
```java
Reader r = new FileReader(file);
```

**After:**
```java
Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
```

### Example: FileWriter Concrete Type and Append Mode

On Java 11+, supported JDK `FileWriter` constructors taking a `File` or `String`
path retain `FileWriter` and receive a typed `Charset` argument. The optional
`append` argument is preserved after the charset, so concrete declarations,
return types and inferred `var` types remain `FileWriter`.

For **Prefer UTF-8**, for example:

```java
// Before
FileWriter writer = new FileWriter(file, true);

// After (Java 11+)
FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8, true);
```

The chosen encoding strategy supplies the charset; **Keep Behavior** uses
`Charset.defaultCharset()`. At Java 7–10 source levels, the cleanup uses
`new OutputStreamWriter(new FileOutputStream(file, append), charset)` only when
the existing target type accepts `OutputStreamWriter`. A concrete `FileWriter`
target stays unchanged there. Both paths retain the original path and append
expressions without duplicating their evaluation. Existing explicit-charset
constructors, `FileDescriptor` constructors and anonymous subclasses are left
unchanged.

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

Eligible UTF-8 rewrites refer to a matching owner-local constant when that field
can be used safely. Its name may differ from `UTF_8` to avoid collisions;
otherwise the cleanup uses `StandardCharsets.UTF_8` directly.

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
